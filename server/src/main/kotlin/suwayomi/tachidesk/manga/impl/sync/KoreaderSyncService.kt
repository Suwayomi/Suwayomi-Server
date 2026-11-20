package suwayomi.tachidesk.manga.impl.sync

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.lang.Hash
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import io.javalin.json.JsonMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.server.util.Platform
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingPushes = ConcurrentHashMap<Pair<Int, Int>, Job>()
    private val remoteProgressCache =
        Cache
            .Builder<Pair<Int, String>, KoreaderProgressResponse>()
            .expireAfterWrite(1.minutes)
            .build()

    // The strategies that make use of the remote progress
    private val remoteProgressStrategies = setOf(KoreaderSyncConflictStrategy.PROMPT, KoreaderSyncConflictStrategy.KEEP_REMOTE)

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
    data class SyncResult(
        val pageRead: Int,
        val timestamp: Long, // Unix timestamp in seconds
        val device: String,
        val shouldUpdate: Boolean = false,
        val isConflict: Boolean = false,
    )

    private class SyncSettings(
        val progressUrl: String,
        val username: String,
        val userkey: String,
        val deviceId: String,
        val checksumMethod: KoreaderSyncChecksumMethod,
        val forwardStrategy: KoreaderSyncConflictStrategy,
        val backwardStrategy: KoreaderSyncConflictStrategy,
        val percentageTolerance: Double,
    ) {
        val usesRemoteProgress = forwardStrategy in remoteProgressStrategies || backwardStrategy in remoteProgressStrategies
    }

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

    /**
     * Selects the chapters with what is needed to hash them and to compare
     * the progress of the user.
     */
    private fun selectChapters(
        userId: Int,
        condition: Op<Boolean>,
    ): List<ResultRow> =
        transaction {
            ChapterTable
                .getWithUserData(userId)
                .innerJoin(MangaTable, { ChapterTable.manga }, { MangaTable.id })
                .select(
                    ChapterTable.columns + MangaTable.title + ChapterUserTable.isRead + ChapterUserTable.lastPageRead +
                        ChapterUserTable.lastReadAt,
                ).where(condition)
                .toList()
        }

    /**
     * Returns the hash KOReader computes for the CBZ download of the chapter,
     * unknown for the binary checksum until the downloaded file is hashed.
     */
    private fun ResultRow.koreaderHash(checksumMethod: KoreaderSyncChecksumMethod): String? =
        when (checksumMethod) {
            // KOReader keeps this name when the catalog uses server file names
            KoreaderSyncChecksumMethod.FILENAME -> {
                Hash.md5(ChapterDownloadHelper.getCbzFileName(this[MangaTable.title], ChapterTable.toDataClass(this)))
            }

            KoreaderSyncChecksumMethod.BINARY -> {
                this[ChapterTable.koreaderHash]
            }
        }

    private suspend fun getOrGenerateChapterHash(
        settings: SyncSettings,
        chapter: ResultRow,
    ): String? {
        chapter.koreaderHash(settings.checksumMethod)?.let { return it }

        val chapterId = chapter[ChapterTable.id].value
        // Only downloaded chapters are hashed, to avoid fetching their pages
        if (!chapter[ChapterTable.isDownloaded]) {
            logger.debug { "[KOSYNC HASH] Skipping binary hash for chapterId=$chapterId because it is not downloaded." }
            return null
        }

        logger.debug { "[KOSYNC HASH] No hash for chapterId=$chapterId. Generating from downloaded content." }
        val newHash =
            try {
                ChapterDownloadHelper
                    .getArchiveStreamWithSize(chapter[ChapterTable.manga].value, chapterId)
                    .first
                    .let(KoreaderHelper::hashContents)
            } catch (e: Exception) {
                logger.warn(e) { "[KOSYNC HASH] Failed to generate archive stream for chapterId=$chapterId." }
                null
            } ?: return null

        transaction {
            ChapterTable.update({ ChapterTable.id eq chapterId }) { it[koreaderHash] = newHash }
        }
        logger.info { "[KOSYNC HASH] Generated and saved new hash for chapterId=$chapterId" }
        return newHash
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

    /**
     * Returns null when the user is not logged in.
     */
    private suspend fun getSyncSettings(userId: Int): SyncSettings? {
        val (serverAddress, username, userkey) = getCredentials(userId)
        if (serverAddress.isBlank() || username.isBlank() || userkey.isBlank()) return null

        return SyncSettings(
            "${serverAddress.removeSuffix("/")}/syncs/progress",
            username,
            userkey,
            getOrGenerateDeviceId(userId),
            userConfig.koreaderSyncChecksumMethod.value(userId),
            userConfig.koreaderSyncStrategyForward.value(userId),
            userConfig.koreaderSyncStrategyBackward.value(userId),
            userConfig.koreaderSyncPercentageTolerance.value(userId),
        )
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

    /**
     * Pushes the progress of a chapter once it stops changing for a few
     * seconds, as reading updates it on every page.
     */
    fun schedulePushProgress(
        userId: Int,
        chapterId: Int,
    ) {
        pendingPushes.compute(userId to chapterId) { key, pendingPush ->
            pendingPush?.cancel()
            scope.launch {
                delay(5.seconds)
                pendingPushes.remove(key, coroutineContext.job)
                pushProgress(userId, chapterId)
            }
        }
    }

    suspend fun pushProgress(
        userId: Int,
        chapterId: Int,
    ) {
        val settings = getSyncSettings(userId) ?: return

        // if both directions keep remote, is in receive-only mode, so don't push.
        if (settings.forwardStrategy == KoreaderSyncConflictStrategy.KEEP_REMOTE &&
            settings.backwardStrategy == KoreaderSyncConflictStrategy.KEEP_REMOTE
        ) {
            return
        }

        val chapter = selectChapters(userId, ChapterTable.id eq chapterId).firstOrNull() ?: return
        val pageCount = chapter[ChapterTable.pageCount]
        if (pageCount <= 0) {
            logger.warn { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: Invalid pageCount." }
            return
        }

        val chapterHash = getOrGenerateChapterHash(settings, chapter)
        if (chapterHash == null) {
            logger.info { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: No hash." }
            return
        }

        try {
            // A chapter marked as read is finished, whatever page was open last
            val pageRead =
                if (chapter.getOrNull(ChapterUserTable.isRead) == true) {
                    pageCount
                } else {
                    (chapter.getOrNull(ChapterUserTable.lastPageRead) ?: 0) + 1
                }
            val payload =
                KoreaderProgressPayload(
                    document = chapterHash,
                    progress = pageRead.toString(),
                    percentage = pageRead.toFloat() / pageCount,
                    device = "Suwayomi-Server (${Platform.current.os.name})",
                    device_id = settings.deviceId,
                )

            val requestBody = json.encodeToString(KoreaderProgressPayload.serializer(), payload)
            val request =
                buildRequest(settings.progressUrl) {
                    put(requestBody.toRequestBody("application/json".toMediaType()))
                    addHeader("x-auth-user", settings.username)
                    addHeader("x-auth-key", settings.userkey)
                }

            logger.debug { "[KOSYNC PUSH] url= ${request.url} - Sending data: $requestBody" }

            network.client.newCall(request).await().use { response ->
                val responseBody = response.body.string()
                logger.debug { "[KOSYNC PUSH] PUT response status: ${response.code}; response body: $responseBody" }
                if (!response.isSuccessful) {
                    logger.warn { "[KOSYNC PUSH] Failed for chapterId=$chapterId: ${response.code}" }
                } else {
                    logger.info { "[KOSYNC PUSH] Success for chapterId=$chapterId" }
                    remoteProgressCache.invalidate(userId to chapterHash)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[KOSYNC PUSH] Exception for chapterId=$chapterId" }
        }
    }

    /**
     * Returns an empty progress when the document has none or the request
     * fails.
     */
    private suspend fun fetchProgress(
        settings: SyncSettings,
        chapterHash: String,
    ): KoreaderProgressResponse {
        try {
            val request =
                buildRequest("${settings.progressUrl}/$chapterHash") {
                    get()
                    addHeader("x-auth-user", settings.username)
                    addHeader("x-auth-key", settings.userkey)
                }
            network.client.newCall(request).await().use { response ->
                logger.debug { "[KOSYNC PULL] GET response status: ${response.code}" }

                if (response.isSuccessful) {
                    val body = response.body.string()
                    logger.debug { "[KOSYNC PULL] GET response body: $body" }
                    return json.decodeFromString(KoreaderProgressResponse.serializer(), body)
                }
                logger.warn { "[KOSYNC PULL] Failed for document=$chapterHash: ${response.code}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "[KOSYNC PULL] Exception for document=$chapterHash" }
        }
        return KoreaderProgressResponse()
    }

    /**
     * Like KOReader, ignores the progress pushed by this server or matching
     * the local one, and applies the strategy of the direction it moves to.
     */
    private fun resolveSyncResult(
        settings: SyncSettings,
        remoteProgress: KoreaderProgressResponse,
        chapter: ResultRow,
    ): SyncResult? {
        // XPath progress of reflowable documents is not a page number
        val pageRead = remoteProgress.progress?.toIntOrNull()?.minus(1) ?: return null
        val timestamp = remoteProgress.timestamp ?: return null
        val lastPageRead = chapter.getOrNull(ChapterUserTable.lastPageRead) ?: 0
        if (remoteProgress.device_id == settings.deviceId || pageRead == lastPageRead) return null

        val pageCount = chapter[ChapterTable.pageCount]
        val localPercentage = if (pageCount > 0) (lastPageRead + 1).toFloat() / pageCount else 0f
        // Progress is within tolerance, no sync needed
        if (abs(localPercentage - (remoteProgress.percentage ?: 0f)) < settings.percentageTolerance) return null

        val strategy =
            if (timestamp > (chapter.getOrNull(ChapterUserTable.lastReadAt) ?: 0)) settings.forwardStrategy else settings.backwardStrategy
        if (strategy !in remoteProgressStrategies) return null

        return SyncResult(
            pageRead,
            timestamp,
            remoteProgress.device ?: "KOReader",
            shouldUpdate = strategy == KoreaderSyncConflictStrategy.KEEP_REMOTE,
            isConflict = strategy == KoreaderSyncConflictStrategy.PROMPT,
        )
    }

    suspend fun checkAndPullProgress(
        userId: Int,
        chapterId: Int,
    ): SyncResult? {
        val settings = getSyncSettings(userId)?.takeIf { it.usesRemoteProgress } ?: return null
        val chapter = selectChapters(userId, ChapterTable.id eq chapterId).firstOrNull() ?: return null

        val chapterHash = getOrGenerateChapterHash(settings, chapter)
        if (chapterHash == null) {
            logger.debug { "[KOSYNC PULL] Aborted for chapterId=$chapterId: No hash." }
            return null
        }

        return resolveSyncResult(settings, fetchProgress(settings, chapterHash), chapter)
    }

    /**
     * Pulls the remote progress of several chapters for the OPDS list feeds.
     *
     * Chapters without a known hash are skipped, as hashing a download builds
     * its archive. The sync server has no batch endpoint, so responses are
     * cached briefly and only those received in a short wait are used.
     */
    suspend fun checkAndPullProgress(
        userId: Int,
        chapterIds: List<Int>,
    ): Map<Int, SyncResult> {
        val settings = getSyncSettings(userId)?.takeIf { it.usesRemoteProgress } ?: return emptyMap()

        val syncResults = ConcurrentHashMap<Int, SyncResult>()
        // Launched in the service scope, so late responses still fill the cache
        val pulls =
            selectChapters(userId, ChapterTable.id inList chapterIds).mapNotNull { chapter ->
                val chapterHash = chapter.koreaderHash(settings.checksumMethod) ?: return@mapNotNull null
                scope.launch {
                    resolveSyncResult(
                        settings,
                        remoteProgressCache.get(userId to chapterHash) { fetchProgress(settings, chapterHash) },
                        chapter,
                    )?.let { syncResults[chapter[ChapterTable.id].value] = it }
                }
            }
        withTimeoutOrNull(5.seconds) { pulls.joinAll() }
        return syncResults.toMap()
    }
}
