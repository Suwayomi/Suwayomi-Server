package suwayomi.tachidesk.manga.impl.sync

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.lang.Hash
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import io.javalin.json.JsonMapper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.graphql.types.KoSyncStatusPayload
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.server.util.Platform
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.UUID
import kotlin.math.abs

object KoreaderSyncService {
    private val logger = KotlinLogging.logger {}

    // Per-user connection credentials are stored in SharedPreferences keyed by user id (not in the per-user settings
    // store, since they are account secrets rather than user preferences).
    // NOTE: the user key is stored as the plain md5 of the password — that is the credential KOReader's
    // kosync protocol itself uses for x-auth-key, so it is secret material. Clearing a user account does
    // not cascade here; use [logout] or wipe the "koreader_sync" preferences.
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

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class KoreaderProgressResponse(
        val document: String? = null,
        val progress: String? = null,
        val percentage: Float? = null,
        // Official kosync servers return `timestamp` (kosync `get_progress`); `updated_at` is
        // tolerated as an alias for custom server implementations.
        @JsonNames("updated_at")
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

    private val deviceIdLock = Any()

    private fun getOrGenerateDeviceId(userId: Int): String {
        preferences
            .getString(key(DEVICE_ID_KEY, userId), "")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // Check+generate+store must be atomic so two concurrent pushes for the same
        // user don't end up with different device ids.
        synchronized(deviceIdLock) {
            val existing = preferences.getString(key(DEVICE_ID_KEY, userId), "")!!
            if (existing.isNotBlank()) return existing

            val deviceId =
                UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .uppercase()
            logger.info { "[KOSYNC] Generated new KOSync Device ID for user $userId: $deviceId" }
            preferences.edit().putString(key(DEVICE_ID_KEY, userId), deviceId).apply()
            return deviceId
        }
    }

    private class ChapterHashState(
        val existingHash: String?,
        val mangaId: Int,
        val isDownloaded: Boolean,
    )

    private suspend fun getOrGenerateChapterHash(
        userId: Int,
        chapterId: Int,
    ): String? {
        // The user-data columns come from a LEFT JOIN, so a chapter the user never
        // interacted with produces NULLs; read them defensively. [ChapterTable.isDownloaded]
        // is used (not [ChapterUserTable.isDownloaded]) because the file on disk is global —
        // a user who did not request the download can still sync it.
        val chapterState =
            suspendTransaction {
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterUserTable.koreaderHash, ChapterTable.manga, ChapterTable.isDownloaded)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.let { row ->
                        ChapterHashState(
                            existingHash = row.getOrNull(ChapterUserTable.koreaderHash),
                            mangaId = row[ChapterTable.manga].value,
                            isDownloaded = row[ChapterTable.isDownloaded],
                        )
                    }
            } ?: return null

        val checksumMethod = userConfig.koreaderSyncChecksumMethod.value(userId)

        // The stored hash is only trusted for BINARY: content hashing is expensive,
        // and the providers already invalidate the hash when the downloaded file is
        // replaced. A FILENAME digest, by contrast, is a cheap md5 of the served
        // file name — which embeds manga title, chapter name and scanlator — so it
        // silently goes stale on every rename (renames don't touch the file and
        // thus never trigger the providers' invalidation). Recompute it on every
        // sync and let the write below refresh the stored value when it changed.
        val existingHash = chapterState.existingHash
        if (!existingHash.isNullOrBlank() && checksumMethod != KoreaderSyncChecksumMethod.FILENAME) {
            return existingHash
        }

        // Hash computation (archive streaming / filename resolution) is done outside of
        // the DB transaction: the archive stream can be heavyweight I/O.
        val newHash =
            when (checksumMethod) {
                KoreaderSyncChecksumMethod.BINARY -> {
                    // Only generate binary hash if the chapter is downloaded to avoid fetching missing files
                    if (chapterState.isDownloaded) {
                        logger.debug { "[KOSYNC HASH] No hash for chapterId=$chapterId. Generating from downloaded content." }
                        try {
                            // Always create a CBZ in memory if it doesn't exist
                            val (stream, size) =
                                ChapterDownloadHelper.getArchiveStreamWithSize(chapterState.mangaId, chapterId)
                            stream.use { KoreaderHelper.hashStream(it, size) }
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
                    // KOReader's getFileNameDigest is `md5(file_name)` of the file as the
                    // client saved it — extension included — so reuse the exact file name
                    // the chapter download endpoint serves.
                    runCatching { ChapterDownloadHelper.getChapterArchiveFileName(chapterId) }
                        .onFailure {
                            logger.warn(it) { "[KOSYNC HASH] Failed to resolve archive filename for chapterId=$chapterId." }
                        }.getOrNull()
                        ?.let { Hash.md5(it) }
                }
            }

        if (newHash != null && newHash != chapterState.existingHash) {
            suspendTransaction {
                ChapterUserTable.upsert(ChapterUserTable.chapter, ChapterUserTable.user) {
                    it[user] = userId
                    it[chapter] = chapterId
                    it[koreaderHash] = newHash
                }
            }
            logger.info { "[KOSYNC HASH] Generated and saved new hash for chapterId=$chapterId" }
        } else if (newHash == null) {
            logger.warn { "[KOSYNC HASH] Hashing failed or skipped for chapterId=$chapterId." }
        }
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

        return try {
            val request =
                buildRequest("${serverAddress.removeSuffix("/")}/users/create") {
                    post(payload.toString().toRequestBody("application/json".toMediaType()))
                }
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
                    val finalMessage =
                        when {
                            // kosync answers 402 when the username is already taken — which,
                            // after a 401 on auth, means the account exists but the password
                            // was wrong, not that registration is needed.
                            response.code == 402 -> {
                                errorMessage
                                    ?: "Username is already registered; the credentials are probably wrong."
                            }

                            else -> {
                                errorMessage ?: "Registration failed with code ${response.code}"
                            }
                        }
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
    ): AuthResult =
        try {
            val request =
                buildRequest("${serverAddress.removeSuffix("/")}/users/auth") {
                    get()
                    addHeader("x-auth-user", username)
                    addHeader("x-auth-key", userkey)
                }
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
        if (!serverAddress.startsWith("https://")) {
            logger.warn {
                "[KOSYNC] serverAddress '$serverAddress' for user $userId is not HTTPS; " +
                    "the kosync user key and reading progress are sent unencrypted."
            }
        }
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
        if (!serverAddress.startsWith("https://")) {
            logger.warn {
                "[KOSYNC CONNECT] serverAddress '$serverAddress' is not HTTPS; " +
                    "credentials and reading progress are sent unencrypted."
            }
        }
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

    // Progress pushes are serialized per (user, chapter) through a fixed-size striped
    // lock table — bounded by construction, so no per-key map can grow without bound;
    // a hash collision merely shares a lock between two keys. Each queued push re-reads
    // the latest local progress inside the lock, and a push carrying a value that was
    // already sent is skipped — so a burst of page-turn triggers coalesces into ordered
    // pushes whose final write carries the newest state (remote applies last-write-wins).
    private const val PUSH_LOCK_STRIPES = 256 // must stay a power of two
    private val pushMutexStripes = Array(PUSH_LOCK_STRIPES) { Mutex() }

    private fun pushMutex(
        userId: Int,
        chapterId: Int,
    ): Mutex = pushMutexStripes[(31 * userId + chapterId) and (PUSH_LOCK_STRIPES - 1)]

    // "Already pushed" coalescing record per (user, chapter), bounded so a long-running
    // server doesn't keep an entry per ever-synced chapter forever; an eviction only
    // costs one redundant (idempotent) re-push.
    private const val PUSHED_PROGRESS_CACHE_SIZE = 4096L
    private val lastPushedProgress =
        Cache
            .Builder<Pair<Int, Int>, String>()
            .maximumCacheSize(PUSHED_PROGRESS_CACHE_SIZE)
            .build()

    /**
     * Pushes the user's local reading progress of [chapterId] to the configured KOReader
     * sync server.
     *
     * @return `true` when the push succeeded or was legitimately skipped (sync disabled,
     * no credentials, no hash, unchanged progress); `false` when a push was attempted or
     * expected but failed.
     */
    suspend fun pushProgress(
        userId: Int,
        chapterId: Int,
    ): Boolean =
        pushMutex(userId, chapterId).withLock {
            pushProgressLocked(userId, chapterId)
        }

    private suspend fun pushProgressLocked(
        userId: Int,
        chapterId: Int,
    ): Boolean {
        val backwardStrategy = userConfig.koreaderSyncStrategyBackward.value(userId)

        // Pushing uploads the local progress — i.e. it performs the "local is newer"
        // (backward) action, so it is skipped whenever that action is suppressed:
        //   backward == DISABLED    -> local-newer action disabled (also covers both DISABLED)
        //   backward == KEEP_REMOTE -> remote wins even when local is newer (covers legacy RECEIVE)
        // Otherwise (KEEP_LOCAL or PROMPT) local progress may be pushed.
        if (backwardStrategy == KoreaderSyncConflictStrategy.DISABLED ||
            backwardStrategy == KoreaderSyncConflictStrategy.KEEP_REMOTE
        ) {
            return true
        }

        val (serverAddress, username, userkey) = getCredentials(userId)
        if (serverAddress.isBlank() || username.isBlank() || userkey.isBlank()) return true

        val chapterHash = getOrGenerateChapterHash(userId, chapterId)
        if (chapterHash.isNullOrBlank()) {
            logger.info { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: No hash." }
            return true
        }

        val chapterInfo =
            suspendTransaction {
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterUserTable.lastPageRead, ChapterUserTable.isRead, ChapterTable.pageCount)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.let {
                        object {
                            val lastPageRead = it.getOrNull(ChapterUserTable.lastPageRead) ?: 0
                            val isRead = it.getOrNull(ChapterUserTable.isRead) ?: false
                            val pageCount = it[ChapterTable.pageCount]
                        }
                    }
            } ?: return false

        if (chapterInfo.pageCount <= 0) {
            logger.warn { "[KOSYNC PUSH] Aborted for chapterId=$chapterId: Invalid pageCount." }
            return true
        }

        // A chapter marked read without a recorded page would otherwise be pushed as
        // page 1; report it as fully read instead. Always clamp into the local bounds.
        val lastPageRead =
            (
                if (chapterInfo.isRead && chapterInfo.lastPageRead <= 0) {
                    chapterInfo.pageCount - 1
                } else {
                    chapterInfo.lastPageRead
                }
            ).coerceIn(0, chapterInfo.pageCount - 1)
        val progress = (lastPageRead + 1).toString()

        // Coalescing: an identical progress value was already pushed for this chapter.
        // The document hash is part of the record so a rename (which changes the
        // FILENAME digest, i.e. the remote document id) is never deduped away.
        val key = userId to chapterId
        val pushState = "$chapterHash:$progress"
        if (lastPushedProgress.get(key) == pushState) return true

        return try {
            val deviceId = getOrGenerateDeviceId(userId)
            val payload =
                KoreaderProgressPayload(
                    document = chapterHash,
                    progress = progress,
                    percentage = (lastPageRead + 1).toFloat() / chapterInfo.pageCount.toFloat(),
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
                    false
                } else {
                    logger.info { "[KOSYNC PUSH] Success for chapterId=$chapterId" }
                    lastPushedProgress.put(key, pushState)
                    true
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[KOSYNC PUSH] Exception for chapterId=$chapterId" }
            false
        }
    }

    suspend fun checkAndPullProgress(
        userId: Int,
        chapterId: Int,
    ): SyncResult? {
        val forwardStrategy = userConfig.koreaderSyncStrategyForward.value(userId)
        val backwardStrategy = userConfig.koreaderSyncStrategyBackward.value(userId)

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

        val body =
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
                        response.body.string()
                    } else {
                        logger.warn { "[KOSYNC PULL] Failed for chapterId=$chapterId: ${response.code}" }
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "[KOSYNC PULL] Exception for chapterId=$chapterId" }
                return null
            }

        if (body.isNullOrBlank() || body == "{}") return null

        val progressResponse =
            try {
                json.decodeFromString(KoreaderProgressResponse.serializer(), body)
            } catch (e: Exception) {
                logger.warn(e) { "[KOSYNC PULL] Failed to decode progress response for chapterId=$chapterId" }
                return null
            }

        // Ignore XPath progress for now as we only support paginated files
        if (progressResponse.progress?.startsWith("/") == true) {
            return null
        }

        val rawPageRead = progressResponse.progress?.toIntOrNull()?.minus(1)
        val timestamp = progressResponse.timestamp
        val device = progressResponse.device ?: "KOReader"

        val localProgress =
            suspendTransaction {
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterUserTable.lastReadAt, ChapterUserTable.lastPageRead, ChapterTable.pageCount)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull()
                    ?.let {
                        object {
                            val lastReadAt = it.getOrNull(ChapterUserTable.lastReadAt) ?: 0L
                            val lastPageRead = it.getOrNull(ChapterUserTable.lastPageRead) ?: 0
                            val pageCount = it[ChapterTable.pageCount]
                        }
                    }
            }

        if (rawPageRead != null && timestamp != null) {
            // Clamp the remote value into the local bounds; without a known page count
            // only the lower bound can be enforced.
            val pageRead =
                rawPageRead.coerceIn(
                    0,
                    localProgress
                        ?.pageCount
                        ?.takeIf { it > 0 }
                        ?.minus(1) ?: Int.MAX_VALUE,
                )

            val localPercentage =
                if ((localProgress?.pageCount ?: 0) > 0) {
                    (localProgress!!.lastPageRead + 1).toFloat() / localProgress.pageCount
                } else {
                    0f
                }
            val percentageDifference = abs(localPercentage - (progressResponse.percentage ?: 0f))

            // Progress is within tolerance, no sync needed
            if (percentageDifference < userConfig.koreaderSyncPercentageTolerance.value(userId)) {
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
        return null
    }
}
