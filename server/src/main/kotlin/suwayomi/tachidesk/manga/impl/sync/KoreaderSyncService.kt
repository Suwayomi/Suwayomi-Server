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
import suwayomi.tachidesk.graphql.types.KoSyncConnectPayload
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.graphql.types.KoreaderSyncStrategy
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.UUID

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
        val timestamp: Long? = null,
        val device: String? = null,
        val device_id: String? = null,
    )

    @Serializable
    data class RemoteProgress(
        val pageRead: Int,
        val timestamp: Long, // Unix timestamp in seconds
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
            val existingHash =
                ChapterTable
                    .select(ChapterTable.koreaderHash)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.get(ChapterTable.koreaderHash)

            if (!existingHash.isNullOrBlank()) {
                return@transaction existingHash
            }

            logger.info { "[KOSYNC HASH] No hash for chapterId=$chapterId. Generating from CBZ." }

            val mangaId =
                ChapterTable
                    .select(ChapterTable.manga)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.get(ChapterTable.manga)
                    ?.value ?: return@transaction null

            val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
            if (!cbzFile.exists()) {
                logger.info { "[KOSYNC HASH] Could not generate hash for chapterId=$chapterId. CBZ not found." }
                return@transaction null
            }

            val newHash = KoreaderHelper.hashContents(cbzFile)
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
    ): KoSyncConnectPayload {
        val userkey = Hash.md5(password)
        val authResult = authorize(username, userkey)

        if (authResult.success) {
            serverConfig.koreaderSyncUsername.value = username
            serverConfig.koreaderSyncUserkey.value = userkey
            return KoSyncConnectPayload(true, "Login successful.", username)
        }

        if (authResult.isUserNotFoundError) {
            logger.info { "[KOSYNC CONNECT] Authorization failed, attempting to register new user." }
            val registerResult = register(username, userkey)
            return if (registerResult.success) {
                serverConfig.koreaderSyncUsername.value = username
                serverConfig.koreaderSyncUserkey.value = userkey
                KoSyncConnectPayload(true, "Registration successful.", username)
            } else {
                KoSyncConnectPayload(false, registerResult.message ?: "Registration failed.", null)
            }
        }

        return KoSyncConnectPayload(false, authResult.message ?: "Authentication failed.", null)
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
        if (!serverConfig.koreaderSyncEnabled.value) return
        val userkey = serverConfig.koreaderSyncUserkey.value
        if (userkey.isBlank()) return
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
                    addHeader("x-auth-user", serverConfig.koreaderSyncUsername.value)
                    addHeader("x-auth-key", userkey)
                }

            network.client.newCall(request).await().use { response ->
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

    suspend fun pullProgress(chapterId: Int): RemoteProgress? {
        val strategy = serverConfig.koreaderSyncStrategy.value
        if (!serverConfig.koreaderSyncEnabled.value || strategy == KoreaderSyncStrategy.SUWAYOMI) return null

        val userkey = serverConfig.koreaderSyncUserkey.value
        if (userkey.isBlank()) return null

        val chapterHash = getOrGenerateChapterHash(chapterId)
        if (chapterHash.isNullOrBlank()) {
            logger.info { "[KOSYNC PULL] Aborted for chapterId=$chapterId: No hash." }
            return null
        }

        try {
            val request =
                buildRequest("${serverConfig.koreaderSyncServerUrl.value.removeSuffix("/")}/syncs/progress/$chapterHash") {
                    get()
                    addHeader("x-auth-user", serverConfig.koreaderSyncUsername.value)
                    addHeader("x-auth-key", userkey)
                }

            network.client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    if (body.isBlank() || body == "{}") return null

                    val progressResponse = json.decodeFromString(KoreaderProgressResponse.serializer(), body)
                    val pageRead = progressResponse.progress?.toIntOrNull()?.minus(1)
                    val timestamp = progressResponse.timestamp

                    if (pageRead != null && timestamp != null) {
                        when (strategy) {
                            KoreaderSyncStrategy.KOSYNC -> return RemoteProgress(pageRead, timestamp)
                            KoreaderSyncStrategy.LATEST -> {
                                val localTimestamp =
                                    transaction {
                                        ChapterTable
                                            .select(ChapterTable.lastReadAt)
                                            .where { ChapterTable.id eq chapterId }
                                            .firstOrNull()
                                            ?.get(ChapterTable.lastReadAt) ?: 0L
                                    }
                                if (timestamp > localTimestamp) {
                                    return RemoteProgress(pageRead, timestamp)
                                }
                            }
                            KoreaderSyncStrategy.SUWAYOMI -> {} // Already handled at the start of the function
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
