package suwayomi.tachidesk.manga.impl.sync

import android.app.Application
import android.content.Context
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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.util.Platform
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.UUID
import kotlin.math.abs

object KoreaderSyncService {
    private val logger = KotlinLogging.logger {}

    // Per-user connection credentials are stored in SharedPreferences keyed by user id (not in the per-user settings
    // store, since they are account secrets rather than user preferences).
    private val preferences = Injekt.get<Application>().getSharedPreferences("koreader_sync", Context.MODE_PRIVATE)
    private const val SERVER_ADDRESS_KEY = "server_address"
    private const val USERNAME_KEY = "username"
    private const val USERKEY_KEY = "user_key"
    private const val DEVICE_ID_KEY = "client_id"

    private fun key(
        base: String,
        userId: Int,
    ) = "${base}_$userId"

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
        val message: String? = null,
        val status: KoSyncStatusPayload,
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

    private suspend fun getOrGenerateDeviceId(userId: Int): String {
        var deviceId = preferences.getString(key(DEVICE_ID_KEY, userId), "")!!

        if (deviceId.isBlank()) {
            deviceId =
                UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .uppercase()
            logger.info { "[KOSYNC] Generated new KOSync Device ID for user $userId: $deviceId" }
            preferences.edit().putString(key(DEVICE_ID_KEY, userId), deviceId).apply()
        }
        return deviceId
    }

    private suspend fun getOrGenerateChapterHash(
        userId: Int,
        chapterId: Int,
    ): String? {
        return suspendTransaction {
            val chapterRow =
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterUserTable.koreaderHash, ChapterTable.manga, ChapterUserTable.isDownloaded)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull() ?: return@suspendTransaction null

            val existingHash = chapterRow[ChapterUserTable.koreaderHash]
            if (!existingHash.isNullOrBlank()) {
                return@suspendTransaction existingHash
            }

            val mangaId = chapterRow[ChapterTable.manga].value
            val isDownloaded = chapterRow[ChapterUserTable.isDownloaded]
            val checksumMethod = userSettings.value(userId, userConfig.koreaderSyncChecksumMethod)

            val newHash =
                when (checksumMethod) {
                    KoreaderSyncChecksumMethod.BINARY -> {
                        // Only generate binary hash if the chapter is downloaded to avoid fetching missing files
                        if (isDownloaded) {
                            logger.debug { "[KOSYNC HASH] No hash for chapterId=$chapterId. Generating from downloaded content." }
                            try {
                                // Always create a CBZ in memory if it doesn't exist
                                val (stream, _) = ChapterDownloadHelper.getArchiveStreamWithSize(mangaId, chapterId)
                                // Write the stream to a temp file for partial hashing
                                val tempFile = File.createTempFile("kosync-hash-", ".cbz")
                                try {
                                    tempFile.outputStream().use { fos ->
                                        stream.use { it.copyTo(fos) }
                                    }
                                    // Use the same hashing method as for downloads
                                    KoreaderHelper.hashContents(tempFile)
                                } finally {
                                    // Always delete the temp file
                                    tempFile.delete()
                                }
                            } catch (e: Exception) {
                                logger.warn(e) { "[KOSYNC HASH] Failed to generate archive stream for chapterId=$chapterId." }
                                null
                            }
                        } else {
                            logger.debug { "[KOSYNC HASH] Skipping binary hash for chapterId=$chapterId because it is not downloaded." }
                            null
                        }
                    }

                    KoreaderSyncChecksumMethod.FILENAME -> {
                        logger.debug { "[KOSYNC HASH] No hash for chapterId=$chapterId. Generating from filename." }
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
                ChapterUserTable.upsert(ChapterUserTable.chapter, ChapterUserTable.user) {
                    it[user] = userId
                    it[chapter] = chapterId
                    it[koreaderHash] = newHash
                }
                logger.info { "[KOSYNC HASH] Generated and saved new hash for chapterId=$chapterId" }
            } else {
                logger.warn { "[KOSYNC HASH] Hashing failed or skipped for chapterId=$chapterId." }
            }
            newHash
        }
    }

    private suspend fun register(
        serverAddress: String,
        username: String,
        userkey: String,
    ): AuthResult {
        val payload =
            buildJsonObject {
                put("username", username)
                put("password", userkey)
            }
        val request =
            buildRequest("${serverAddress.removeSuffix("/")}/users/create") {
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
        serverAddress: String,
        username: String,
        userkey: String,
    ): AuthResult {
        val request =
            buildRequest("${serverAddress.removeSuffix("/")}/users/auth") {
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

    private fun getCredentials(userId: Int): Triple<String, String, String> {
        val serverAddress = preferences.getString(key(SERVER_ADDRESS_KEY, userId), "https://sync.koreader.rocks/")!!
        val username = preferences.getString(key(USERNAME_KEY, userId), "")!!
        val userkey = preferences.getString(key(USERKEY_KEY, userId), "")!!

        return Triple(serverAddress, username, userkey)
    }

    private fun setCredentials(
        userId: Int,
        serverAddress: String,
        username: String,
        userkey: String,
    ) {
        preferences
            .edit()
            .putString(key(SERVER_ADDRESS_KEY, userId), serverAddress)
            .putString(key(USERNAME_KEY, userId), username)
            .putString(key(USERKEY_KEY, userId), userkey)
            .apply()
    }

    private fun clearCredentials(userId: Int) {
        preferences
            .edit()
            .remove(key(SERVER_ADDRESS_KEY, userId))
            .remove(key(USERNAME_KEY, userId))
            .remove(key(USERKEY_KEY, userId))
            .remove(key(DEVICE_ID_KEY, userId))
            .apply()
    }

    suspend fun connect(
        userId: Int,
        serverAddress: String,
        username: String,
        password: String,
    ): ConnectResult {
        val userkey = Hash.md5(password)
        val authResult = authorize(serverAddress, username, userkey)

        if (authResult.success) {
            setCredentials(userId, serverAddress, username, userkey)
            return ConnectResult(
                "Login successful.",
                KoSyncStatusPayload(isLoggedIn = true, serverAddress = serverAddress, username = username),
            )
        }

        if (authResult.isUserNotFoundError) {
            logger.info { "[KOSYNC CONNECT] Authorization failed, attempting to register new user." }
            val registerResult = register(serverAddress, username, userkey)
            return if (registerResult.success) {
                setCredentials(userId, serverAddress, username, userkey)
                ConnectResult(
                    "Registration successful.",
                    KoSyncStatusPayload(isLoggedIn = true, serverAddress = serverAddress, username = username),
                )
            } else {
                ConnectResult(
                    registerResult.message ?: "Registration failed.",
                    KoSyncStatusPayload(isLoggedIn = false, serverAddress = null, username = null),
                )
            }
        }

        return ConnectResult(
            authResult.message ?: "Authentication failed.",
            KoSyncStatusPayload(isLoggedIn = false, serverAddress = null, username = null),
        )
    }

    fun logout(userId: Int) {
        clearCredentials(userId)
    }

    suspend fun getStatus(userId: Int): KoSyncStatusPayload {
        val (serverAddress, username, userkey) = getCredentials(userId)

        if (username.isBlank() || userkey.isBlank()) {
            return KoSyncStatusPayload(isLoggedIn = false, serverAddress = null, username = null)
        }

        val authResult = authorize(serverAddress, username, userkey)

        return if (authResult.success) {
            KoSyncStatusPayload(isLoggedIn = true, serverAddress = serverAddress, username = username)
        } else {
            KoSyncStatusPayload(isLoggedIn = false, serverAddress = null, username = null)
        }
    }

    suspend fun pushProgress(
        userId: Int,
        chapterId: Int,
    ) {
        val forwardStrategy = userSettings.value(userId, userConfig.koreaderSyncStrategyForward)
        val backwardStrategy = userSettings.value(userId, userConfig.koreaderSyncStrategyBackward)

        // if both directions keep remote, is in receive-only mode, so don't push.
        if (forwardStrategy == KoreaderSyncConflictStrategy.KEEP_REMOTE &&
            backwardStrategy == KoreaderSyncConflictStrategy.KEEP_REMOTE
        ) {
            return
        }

        val (serverAddress, username, userkey) = getCredentials(userId)
        if (serverAddress.isBlank() || username.isBlank() || userkey.isBlank()) return

        val chapterHash = getOrGenerateChapterHash(userId, chapterId)
        if (chapterHash.isNullOrBlank()) {
            logger.info { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: No hash." }
            return
        }

        val chapterInfo =
            transaction {
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterUserTable.lastPageRead, ChapterTable.pageCount)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.let {
                        object {
                            val lastPageRead = it[ChapterUserTable.lastPageRead]
                            val pageCount = it[ChapterTable.pageCount]
                        }
                    }
            } ?: return

        if (chapterInfo.pageCount <= 0) {
            logger.warn { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: Invalid pageCount." }
            return
        }

        try {
            val deviceId = getOrGenerateDeviceId(userId)
            val payload =
                KoreaderProgressPayload(
                    document = chapterHash,
                    progress = (chapterInfo.lastPageRead + 1).toString(),
                    percentage = (chapterInfo.lastPageRead + 1).toFloat() / chapterInfo.pageCount.toFloat(),
                    device = "Suwayomi-Server (${Platform.current.os.name})",
                    device_id = deviceId,
                )

            val requestBody = json.encodeToString(KoreaderProgressPayload.serializer(), payload)
            val request =
                buildRequest("${serverAddress.removeSuffix("/")}/syncs/progress") {
                    put(requestBody.toRequestBody("application/json".toMediaType()))
                    addHeader("x-auth-user", username)
                    addHeader("x-auth-key", userkey)
                }

            logger.info { "[KOSYNC PUSH] url= ${request.url} - Sending data: $requestBody" }

            network.client.newCall(request).await().use { response ->
                val responseBody = response.body.string()
                logger.debug { "[KOSYNC PUSH] PUT response status: ${response.code}; response body: $responseBody" }
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

    suspend fun checkAndPullProgress(
        userId: Int,
        chapterId: Int,
    ): SyncResult? {
        val forwardStrategy = userSettings.value(userId, userConfig.koreaderSyncStrategyForward)
        val backwardStrategy = userSettings.value(userId, userConfig.koreaderSyncStrategyBackward)

        // Skip remote fetch if both directions disabled OR both keep local (no remote data needed)
        if ((forwardStrategy == KoreaderSyncConflictStrategy.DISABLED && backwardStrategy == KoreaderSyncConflictStrategy.DISABLED) ||
            (forwardStrategy == KoreaderSyncConflictStrategy.KEEP_LOCAL && backwardStrategy == KoreaderSyncConflictStrategy.KEEP_LOCAL)
        ) {
            return null
        }

        val (serverAddress, username, userkey) = getCredentials(userId)
        if (serverAddress.isBlank() || username.isBlank() || userkey.isBlank()) return null

        val chapterHash = getOrGenerateChapterHash(userId, chapterId)
        if (chapterHash.isNullOrBlank()) {
            logger.debug { "[KOSYNC PULL] Aborted for chapterId=$chapterId: No hash." }
            return null
        }

        try {
            val request =
                buildRequest("${serverAddress.removeSuffix("/")}/syncs/progress/$chapterHash") {
                    get()
                    addHeader("x-auth-user", username)
                    addHeader("x-auth-key", userkey)
                }
            network.client.newCall(request).await().use { response ->
                logger.debug { "[KOSYNC PULL] GET response status: ${response.code}" }

                if (response.isSuccessful) {
                    val body = response.body.string()
                    logger.debug { "[KOSYNC PULL] GET response body: $body" }
                    if (body.isBlank() || body == "{}") return null

                    val progressResponse = json.decodeFromString(KoreaderProgressResponse.serializer(), body)
                    val pageRead = progressResponse.progress?.toIntOrNull()?.minus(1)
                    val timestamp = progressResponse.updated_at
                    val device = progressResponse.device ?: "KOReader"

                    val localProgress =
                        transaction {
                            ChapterTable
                                .getWithUserData(userId)
                                .select(ChapterUserTable.lastReadAt, ChapterUserTable.lastPageRead, ChapterTable.pageCount)
                                .where { ChapterTable.id eq chapterId }
                                .firstOrNull()
                                ?.let {
                                    object {
                                        val lastReadAt = it[ChapterUserTable.lastReadAt]
                                        val lastPageRead = it[ChapterUserTable.lastPageRead]
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
                            if ((localProgress?.pageCount ?: 0) > 0) {
                                (localProgress!!.lastPageRead + 1).toFloat() / localProgress.pageCount
                            } else {
                                0f
                            }
                        val percentageDifference = abs(localPercentage - (progressResponse.percentage ?: 0f))

                        // Progress is within tolerance, no sync needed
                        if (percentageDifference < userSettings.value(userId, userConfig.koreaderSyncPercentageTolerance)) {
                            return null
                        }

                        val localTimestamp = localProgress?.lastReadAt ?: 0L
                        val isRemoteNewer = timestamp > localTimestamp
                        val strategy = if (isRemoteNewer) forwardStrategy else backwardStrategy

                        return when (strategy) {
                            KoreaderSyncConflictStrategy.PROMPT -> SyncResult(pageRead, timestamp, device, isConflict = true)
                            KoreaderSyncConflictStrategy.KEEP_REMOTE -> SyncResult(pageRead, timestamp, device, shouldUpdate = true)
                            KoreaderSyncConflictStrategy.KEEP_LOCAL, KoreaderSyncConflictStrategy.DISABLED -> null
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
