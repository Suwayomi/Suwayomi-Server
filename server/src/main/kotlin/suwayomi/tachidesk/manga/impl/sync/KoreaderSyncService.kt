package suwayomi.tachidesk.manga.impl.sync

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.lang.Hash
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.json.JsonMapper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncStrategy
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.util.UUID
import kotlin.math.abs

object KoreaderSyncService {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()
    private val jsonMapper: JsonMapper by injectLazy()

    @Serializable
    private data class KoreaderProgressPayload(
        val document: String,
        val progress: String,
        val percentage: Float,
        val device: String,
        val device_id: String,
    )

    @Serializable
    private data class KoreaderProgressResponse(
        val document: String? = null,
        val progress: String? = null,
        val percentage: Float? = null,
        val updated_at: Long? = null,
        val device: String? = null,
        val device_id: String? = null,
    )

    @Serializable
    data class SyncResult(
        val pageRead: Int,
        val timestamp: Long, // Unix timestamp in seconds
        val device: String,
        val shouldUpdate: Boolean = false,
        val isConflict: Boolean = false,
    )

    data class ConnectResult(
        val success: Boolean,
        val message: String? = null,
        val username: String? = null,
    )

    private data class AuthResult(
        val success: Boolean,
        val message: String? = null,
        val isUserNotFoundError: Boolean = false,
    )

    private fun buildRequest(
        url: String,
        block: Request.Builder.() -> Unit,
    ): Request =
        Request
            .Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.koreader.v1+json")
            .addHeader("Connection", "close")
            .apply(block)
            .build()

    private suspend fun getOrGenerateDeviceId(): String {
        var deviceId = serverConfig.koreaderSyncDeviceId.value
        if (deviceId.isBlank()) {
            deviceId =
                UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .uppercase()
            logger.info { "[KOSYNC] Generated new KOSync Device ID: $deviceId" }
            serverConfig.koreaderSyncDeviceId.value = deviceId
        }
        return deviceId
    }

    private fun getOrGenerateChapterHash(chapterId: Int): String? {
        return transaction {
            val chapterRow =
                ChapterTable
                    .select(ChapterTable.koreaderHash, ChapterTable.manga, ChapterTable.isDownloaded)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull() ?: return@transaction null

            val existingHash = chapterRow[ChapterTable.koreaderHash]
            if (!existingHash.isNullOrBlank()) {
                return@transaction existingHash
            }

            val mangaId = chapterRow[ChapterTable.manga].value
            val checksumMethod = serverConfig.koreaderSyncChecksumMethod.value

            val newHash =
                when (checksumMethod) {
                    KoreaderSyncChecksumMethod.BINARY -> {
                        logger.info { "[KOSYNC HASH] No hash for chapterId=$chapterId. Generating from downloaded content." }
                        try {
                            // This generates a deterministic CBZ stream from either a folder or an existing CBZ file.
                            // If it fails, it means the chapter is not available for hashing.
                            val (stream, _) = ChapterDownloadHelper.getAsArchiveStream(mangaId, chapterId)
                            stream.use {
                                Hash.md5(it.readBytes())
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "[KOSYNC HASH] Failed to generate archive stream for chapterId=$chapterId." }
                            null
                        }
                    }
                    KoreaderSyncChecksumMethod.FILENAME -> {
                        logger.info { "[KOSYNC HASH] No hash for chapterId=$chapterId. Generating from filename." }
                        (ChapterTable innerJoin MangaTable)
                            .select(ChapterTable.name, MangaTable.title)
                            .where { ChapterTable.id eq chapterId }
                            .firstOrNull()
                            ?.let {
                                val chapterName = it[ChapterTable.name]
                                val mangaTitle = it[MangaTable.title]
                                val baseFilename = "$mangaTitle - $chapterName".split('.').dropLast(1).joinToString(".")
                                Hash.md5(baseFilename)
                            }
                    }
                }

            if (newHash != null) {
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[koreaderHash] = newHash
                }
                logger.info { "[KOSYNC HASH] Generated and saved new hash for chapterId=$chapterId" }
            } else {
                logger.warn { "[KOSYNC HASH] Hashing failed for chapterId=$chapterId." }
            }
            newHash
        }
    }

    private suspend fun register(
        username: String,
        userkey: String,
    ): AuthResult {
        val payload =
            buildJsonObject {
                put("username", username)
                put("password", userkey)
            }
        val request =
            buildRequest("${serverConfig.koreaderSyncServerUrl.value.removeSuffix("/")}/users/create") {
                post(payload.toString().toRequestBody("application/json".toMediaType()))
            }

        return try {
            network.client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    AuthResult(true, "Registration successful.")
                } else {
                    val errorBody = response.body.string()
                    val errorMessage =
                        runCatching {
                            jsonMapper.fromJsonString<Map<String, String>>(
                                errorBody,
                                Map::class.java,
                            )["message"]
                        }.getOrNull()
                    val finalMessage = errorMessage ?: "Registration failed with code ${response.code}"
                    AuthResult(false, finalMessage)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[KOSYNC REGISTER] Exception" }
            AuthResult(false, e.message)
        }
    }

    private suspend fun authorize(
        username: String,
        userkey: String,
    ): AuthResult {
        val request =
            buildRequest("${serverConfig.koreaderSyncServerUrl.value.removeSuffix("/")}/users/auth") {
                get()
                addHeader("x-auth-user", username)
                addHeader("x-auth-key", userkey)
            }

        return try {
            network.client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    AuthResult(true)
                } else {
                    val isUserNotFound = response.code == 401 // Unauthorized often means user/pass combo is wrong
                    AuthResult(false, "Authorization failed with code ${response.code}", isUserNotFoundError = isUserNotFound)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[KOSYNC AUTHORIZE] Exception" }
            AuthResult(false, e.message)
        }
    }

    suspend fun connect(
        username: String,
        password: String,
    ): ConnectResult {
        val userkey = Hash.md5(password)
        val authResult = authorize(username, userkey)

        if (authResult.success) {
            serverConfig.koreaderSyncUsername.value = username
            serverConfig.koreaderSyncUserkey.value = userkey
            return ConnectResult(true, "Login successful.", username)
        }

        if (authResult.isUserNotFoundError) {
            logger.info { "[KOSYNC CONNECT] Authorization failed, attempting to register new user." }
            val registerResult = register(username, userkey)
            return if (registerResult.success) {
                serverConfig.koreaderSyncUsername.value = username
                serverConfig.koreaderSyncUserkey.value = userkey
                ConnectResult(true, "Registration successful.", username)
            } else {
                ConnectResult(false, registerResult.message ?: "Registration failed.", null)
            }
        }

        return ConnectResult(false, authResult.message ?: "Authentication failed.", null)
    }

    suspend fun logout() {
        serverConfig.koreaderSyncUsername.value = ""
        serverConfig.koreaderSyncUserkey.value = ""
    }

    suspend fun getStatus(): KoSyncStatusPayload {
        val username = serverConfig.koreaderSyncUsername.value
        val userkey = serverConfig.koreaderSyncUserkey.value
        if (username.isBlank() || userkey.isBlank()) {
            return KoSyncStatusPayload(isLoggedIn = false, username = null)
        }
        val authResult = authorize(username, userkey)
        return KoSyncStatusPayload(isLoggedIn = authResult.success, username = if (authResult.success) username else null)
    }

    suspend fun pushProgress(chapterId: Int) {
        val strategy = serverConfig.koreaderSyncStrategy.value
        if (strategy == KoreaderSyncStrategy.DISABLED || strategy == KoreaderSyncStrategy.RECEIVE) return

        val username = serverConfig.koreaderSyncUsername.value
        val userkey = serverConfig.koreaderSyncUserkey.value
        if (username.isBlank() || userkey.isBlank()) return

        logger.info { "[KOSYNC PUSH] Init." }

        val chapterHash = getOrGenerateChapterHash(chapterId)
        if (chapterHash.isNullOrBlank()) {
            logger.info { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: No hash." }
            return
        }

        val chapterInfo =
            transaction {
                ChapterTable
                    .select(ChapterTable.lastPageRead, ChapterTable.pageCount)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.let {
                        object {
                            val lastPageRead = it[ChapterTable.lastPageRead]
                            val pageCount = it[ChapterTable.pageCount]
                        }
                    }
            } ?: return

        if (chapterInfo.pageCount <= 0) {
            logger.warn { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: Invalid pageCount." }
            return
        }

        try {
            val deviceId = getOrGenerateDeviceId()
            val payload =
                KoreaderProgressPayload(
                    document = chapterHash,
                    progress = (chapterInfo.lastPageRead + 1).toString(),
                    percentage = (chapterInfo.lastPageRead + 1).toFloat() / chapterInfo.pageCount.toFloat(),
                    device = "Suwayomi-Server (${System.getProperty("os.name")})",
                    device_id = deviceId,
                )

            val requestBody = json.encodeToString(KoreaderProgressPayload.serializer(), payload)
            val request =
                buildRequest("${serverConfig.koreaderSyncServerUrl.value.removeSuffix("/")}/syncs/progress") {
                    put(requestBody.toRequestBody("application/json".toMediaType()))
                    addHeader("x-auth-user", username)
                    addHeader("x-auth-key", userkey)
                }

            logger.info { "[KOSYNC PUSH] PUT request to URL: ${request.url}" }
            logger.info { "[KOSYNC PUSH] Sending data: $requestBody" }

            network.client.newCall(request).await().use { response ->
                val responseBody = response.body.string()
                logger.info { "[KOSYNC PUSH] PUT response status: ${response.code}" }
                logger.info { "[KOSYNC PUSH] PUT response body: $responseBody" }
                if (!response.isSuccessful) {
                    logger.warn { "[KOSYNC PUSH] Failed for chapterId=$chapterId: ${response.code}" }
                } else {
                    logger.info { "[KOSYNC PUSH] Success for chapterId=$chapterId" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[KOSYNC PUSH] Exception for chapterId=$chapterId" }
        }
    }

    suspend fun checkAndPullProgress(chapterId: Int): SyncResult? {
        val strategy = serverConfig.koreaderSyncStrategy.value
        if (strategy == KoreaderSyncStrategy.DISABLED || strategy == KoreaderSyncStrategy.SEND) return null

        val username = serverConfig.koreaderSyncUsername.value
        val userkey = serverConfig.koreaderSyncUserkey.value
        if (username.isBlank() || userkey.isBlank()) return null

        val chapterHash = getOrGenerateChapterHash(chapterId)
        if (chapterHash.isNullOrBlank()) {
            logger.info { "[KOSYNC PULL] Aborted for chapterId=$chapterId: No hash." }
            return null
        }

        try {
            val request =
                buildRequest("${serverConfig.koreaderSyncServerUrl.value.removeSuffix("/")}/syncs/progress/$chapterHash") {
                    get()
                    addHeader("x-auth-user", username)
                    addHeader("x-auth-key", userkey)
                }
            logger.info { "[KOSYNC PULL] GET request to URL: ${request.url}" }

            network.client.newCall(request).await().use { response ->
                logger.info { "[KOSYNC PULL] GET response status: ${response.code}" }

                if (response.isSuccessful) {
                    val body = response.body.string()
                    logger.info { "[KOSYNC PULL] GET response body: $body" }
                    if (body.isBlank() || body == "{}") return null

                    val progressResponse = json.decodeFromString(KoreaderProgressResponse.serializer(), body)
                    val pageRead = progressResponse.progress?.toIntOrNull()?.minus(1)
                    val timestamp = progressResponse.updated_at
                    val device = progressResponse.device ?: "KOReader"

                    val localProgress =
                        transaction {
                            ChapterTable
                                .select(ChapterTable.lastReadAt, ChapterTable.lastPageRead, ChapterTable.pageCount)
                                .where { ChapterTable.id eq chapterId }
                                .firstOrNull()
                                ?.let {
                                    object {
                                        val lastReadAt = it[ChapterTable.lastReadAt]
                                        val lastPageRead = it[ChapterTable.lastPageRead]
                                        val pageCount = it[ChapterTable.pageCount]
                                    }
                                }
                        }

                    if (pageRead != null && timestamp != null) {
                        // Ignore XPath progress for now as we only support paginated files
                        if (progressResponse.progress?.startsWith("/") == true) {
                            return null
                        }

                        val localPercentage =
                            if (localProgress?.pageCount ?: 0 >
                                0
                            ) {
                                (localProgress!!.lastPageRead + 1).toFloat() / localProgress.pageCount
                            } else {
                                0f
                            }
                        val percentageDifference = abs(localPercentage - (progressResponse.percentage ?: 0f))

                        // Progress is within tolerance, no sync needed
                        if (percentageDifference < serverConfig.koreaderSyncPercentageTolerance.value) {
                            return null
                        }

                        when (strategy) {
                            KoreaderSyncStrategy.RECEIVE -> {
                                return SyncResult(pageRead, timestamp, device, shouldUpdate = true)
                            }
                            KoreaderSyncStrategy.SILENT -> {
                                if (timestamp > (localProgress?.lastReadAt ?: 0L)) {
                                    return SyncResult(pageRead, timestamp, device, shouldUpdate = true)
                                }
                            }
                            KoreaderSyncStrategy.PROMPT -> {
                                return SyncResult(pageRead, timestamp, device, isConflict = true)
                            }
                            else -> {} // SEND and DISABLED already handled at the start of the function
                        }
                    }
                } else {
                    logger.warn { "[KOSYNC PULL] Failed for chapterId=$chapterId: ${response.code}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[KOSYNC PULL] Exception for chapterId=$chapterId" }
        }
        return null
    }
}
