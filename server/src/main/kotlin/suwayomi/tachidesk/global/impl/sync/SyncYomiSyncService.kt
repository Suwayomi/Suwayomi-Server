package suwayomi.tachidesk.global.impl.sync

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.HttpStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSource
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

object SyncYomiSyncService {
    private val syncPreferences = Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)

    private val network: NetworkHelper by injectLazy()
    private val logger = KotlinLogging.logger {}

    private class SyncYomiException(
        message: String?,
    ) : Exception(message)

    @Serializable
    private data class SyncEvent(
        val event: SyncEventStatus,
        val device_id: String? = null,
        val device_name: String? = null,
        val message: String? = null,
    )

    data class SyncResult(
        val backup: Backup?,
        val changed: Boolean,
        val protocolV2: Boolean,
    )

    private const val DEVICE_NAME = "Suwayomi Server"
    private const val PREF_DEVICE_ID = "device_id"
    private const val PREF_CURSOR = "sync_cursor"
    private const val PREF_FULL_REQUESTED = "full_sync_requested"
    private const val PREF_LAST_FULL_SYNC = "last_full_sync"
    private const val PREF_PENDING_DELETED_CATEGORIES = "pending_deleted_category_uids"
    private const val PREF_V2_PROBED_HOST = "v2_probed_host"
    private const val PREF_V2_SUPPORTED = "server_supports_v2"
    private const val LAST_SYNC_TIMESTAMP_PREF = "last_sync_timestamp"

    // any sane epoch-milliseconds timestamp is past this point (~Sep 2001); smaller
    // values in last_sync_timestamp can only be legacy epoch-seconds watermarks
    private const val MILLIS_TIMESTAMP_FLOOR = 1_000_000_000_000L
    private val fullSyncInterval = 24.hours
    private val syncClientTimeout = 30.seconds

    @Serializable
    private enum class SyncEventStatus {
        SYNC_STARTED,
        SYNC_SUCCESS,
        SYNC_FAILED,
        SYNC_ERROR,
        SYNC_CANCELLED,
    }

    suspend fun doSync(
        userId: Int,
        syncData: SyncData,
        full: Boolean,
        startDate: Instant,
        setSyncState: (SyncManager.SyncState) -> Unit,
    ): SyncResult {
        reportSyncEvent(userId, SyncEventStatus.SYNC_STARTED)

        return try {
            if (supportsV2(userId)) {
                doSyncV2(userId, syncData, full, startDate, setSyncState)
            } else {
                doSyncV1(userId, syncData, startDate, setSyncState)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                reportSyncEvent(userId, SyncEventStatus.SYNC_CANCELLED, e.message)
                throw e
            }
            logger.error { "Error syncing: ${e.message}" }
            reportSyncEvent(userId, SyncEventStatus.SYNC_ERROR, e.message)
            throw e
        } catch (e: OutOfMemoryError) {
            logger.error { "Out of memory while syncing" }
            reportSyncEvent(userId, SyncEventStatus.SYNC_ERROR, "OutOfMemoryError")
            throw e
        }
    }

    private suspend fun doSyncV1(
        userId: Int,
        syncData: SyncData,
        startDate: Instant,
        setSyncState: (SyncManager.SyncState) -> Unit,
    ): SyncResult {
        setSyncState(SyncManager.SyncState.Downloading(startDate))
        val (remoteData, etag) = pullSyncData(userId)

        val finalSyncData =
            if (remoteData != null) {
                require(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
                logger.debug { "Try update remote data with ETag($etag)" }
                setSyncState(SyncManager.SyncState.Merging(startDate))
                mergeSyncData(userId, syncData, remoteData)
            } else {
                // init or overwrite remote data
                logger.debug { "Try overwrite remote data with ETag($etag)" }
                syncData
            }

        if (finalSyncData.backup != null) {
            setSyncState(SyncManager.SyncState.Uploading(startDate))
        }

        // a failed push (412 precondition or any other non-2xx) is a sync failure, not a
        // "try again next time" success — throwing keeps the watermarks untouched and
        // reports the error instead of letting the caller restore/finish as if it worked
        pushSyncData(userId, finalSyncData, etag)
        reportSyncEvent(userId, SyncEventStatus.SYNC_SUCCESS)

        return SyncResult(finalSyncData.backup, changed = remoteData != null, protocolV2 = false)
    }

    // Protocol v2: the server merges; we upload what changed and restore what comes back.
    private suspend fun doSyncV2(
        userId: Int,
        syncData: SyncData,
        full: Boolean,
        startDate: Instant,
        setSyncState: (SyncManager.SyncState) -> Unit,
    ): SyncResult {
        val backup = syncData.backup ?: return SyncResult(null, changed = false, protocolV2 = true)
        val host = syncHost(userId)
        val apiKey = userConfig.syncYomiApiKey.value(userId)

        val pendingDeleted = pendingDeletedCategories(userId)
        val headers =
            baseHeaders(apiKey, userId)
                .add("X-Sync-Cursor", syncCursor(userId).toString())
                .add("X-Sync-Full", full.toString())
                .add("Content-Encoding", "gzip")
        if (pendingDeleted.isNotEmpty()) {
            headers.add("X-Sync-Deleted-Categories", pendingDeleted.joinToString(","))
        }

        setSyncState(SyncManager.SyncState.Uploading(startDate))
        val body = BackupRequestBody(backup, ProtoBuf, gzip = true)
        val response =
            syncClient()
                .newCall(POST(url = "$host/api/sync/v2/merge", headers = headers.build(), body = body))
                .await()

        if (!response.isSuccessful) {
            val responseBody = response.body.string()
            logger.error { "SyncError (${response.code}): $responseBody" }
            if (response.code == HttpStatus.NOT_FOUND.code) {
                // a 404 from the v2 endpoint means the cached probe is stale (the server
                // was downgraded); drop it so the next sync re-probes and falls back to v1
                invalidateV2ProbeCache(userId)
            }
            reportSyncEvent(userId, SyncEventStatus.SYNC_FAILED, "Server answered ${response.code}")
            throw SyncYomiException("Failed to sync: ${response.code} $responseBody")
        }

        setSyncState(SyncManager.SyncState.Downloading(startDate))
        val bytes = response.body.bytes()
        val remote =
            try {
                ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
            } catch (e: SerializationException) {
                logger.error(e) { "Bad content responded from server" }
                reportSyncEvent(userId, SyncEventStatus.SYNC_FAILED, "Bad content responded from server")
                throw SyncYomiException("Bad content responded from server: ${e.message}")
            }
        // v2 response contract:
        //  - X-Sync-Cursor (required): opaque server cursor echoed on the next request;
        //    a client cursor of 0 asks for — and the response then contains — a full library
        //  - X-Sync-Changed (optional): whether the merged remote actually differs from the
        //    upload; absent means "changed" (conservative default, the restore is cheap)
        //  - X-Sync-Full-Requested (optional): the server demands a full library next time
        val cursor =
            response.headers["X-Sync-Cursor"]?.toLongOrNull()
                ?: throw SyncYomiException("Missing X-Sync-Cursor")
        val changed = response.headers["X-Sync-Changed"]?.toBoolean() ?: true
        val fullRequested = response.headers["X-Sync-Full-Requested"]?.toBoolean() ?: false

        syncPreferences
            .edit()
            .putLong("${PREF_CURSOR}_$userId", cursor)
            .putBoolean("${PREF_FULL_REQUESTED}_$userId", fullRequested)
            .putStringSet("${PREF_PENDING_DELETED_CATEGORIES}_$userId", pendingDeletedCategories(userId) - pendingDeleted)
            .apply {
                if (full) putLong("${PREF_LAST_FULL_SYNC}_$userId", startDate.toEpochMilliseconds())
            }.apply()
        logger.debug { "SyncYomi v2 merge done: cursor=$cursor changed=$changed fullRequested=$fullRequested" }

        reportSyncEvent(userId, SyncEventStatus.SYNC_SUCCESS)
        return SyncResult(remote, changed = changed, protocolV2 = true)
    }

    // A full library is needed on the first sync, when the server asks for it, and once a day as a safety net.
    suspend fun needsFullSync(userId: Int): Boolean {
        if (!supportsV2(userId)) return true
        if (syncCursor(userId) == 0L || syncPreferences.getBoolean("${PREF_FULL_REQUESTED}_$userId", false)) return true
        val lastFull = syncPreferences.getLong("${PREF_LAST_FULL_SYNC}_$userId", 0L)
        return lastFull == 0L || Clock.System.now() - Instant.fromEpochMilliseconds(lastFull) > fullSyncInterval
    }

    suspend fun supportsV2(userId: Int): Boolean {
        val host = syncHost(userId)
        if (syncPreferences.getString("${PREF_V2_PROBED_HOST}_$userId", null) == host) {
            return syncPreferences.getBoolean("${PREF_V2_SUPPORTED}_$userId", false)
        }

        val apiKey = userConfig.syncYomiApiKey.value(userId)
        val request = GET(url = "$host/api/sync/v2/capabilities", headers = baseHeaders(apiKey, userId).build())
        val response = network.client.newCall(request).await()
        response.close()
        val supported =
            when (response.code) {
                HttpStatus.OK.code -> true
                HttpStatus.NOT_FOUND.code -> false
                else -> throw SyncYomiException("Failed to probe server capabilities: ${response.code}")
            }
        syncPreferences
            .edit()
            .putString("${PREF_V2_PROBED_HOST}_$userId", host)
            .putBoolean("${PREF_V2_SUPPORTED}_$userId", supported)
            .apply()
        logger.info { "SyncYomi server supports protocol v2: $supported" }
        return supported
    }

    private fun invalidateV2ProbeCache(userId: Int) {
        syncPreferences
            .edit()
            .remove("${PREF_V2_PROBED_HOST}_$userId")
            .remove("${PREF_V2_SUPPORTED}_$userId")
            .apply()
    }

    fun syncCursor(userId: Int): Long = syncPreferences.getLong("${PREF_CURSOR}_$userId", 0L)

    fun deviceId(userId: Int): String {
        syncPreferences.getString("${PREF_DEVICE_ID}_$userId", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        syncPreferences.edit().putString("${PREF_DEVICE_ID}_$userId", id).apply()
        return id
    }

    fun rememberDeletedCategory(
        userId: Int,
        uid: Long,
    ) {
        if (uid == 0L || !userConfig.syncYomiEnabled.value(userId)) return
        syncPreferences
            .edit()
            .putStringSet("${PREF_PENDING_DELETED_CATEGORIES}_$userId", pendingDeletedCategories(userId) + uid.toString())
            .apply()
    }

    /**
     * Drop [uids] from the pending-deletion tombstones. [rememberDeletedCategory] records
     * user-initiated deletions for the next v2 upload, but a deletion driven by the sync
     * merge itself must not be re-propagated as if the user deleted it on this device.
     */
    fun forgetDeletedCategories(
        userId: Int,
        uids: Collection<Long>,
    ) {
        if (uids.isEmpty()) return
        syncPreferences
            .edit()
            .putStringSet(
                "${PREF_PENDING_DELETED_CATEGORIES}_$userId",
                pendingDeletedCategories(userId) - uids.mapTo(mutableSetOf()) { it.toString() },
            ).apply()
    }

    private fun pendingDeletedCategories(userId: Int): Set<String> =
        syncPreferences.getStringSet("${PREF_PENDING_DELETED_CATEGORIES}_$userId", emptySet()).orEmpty().toSet()

    /**
     * The configured SyncYomi host, normalized for URL concatenation. A cleartext http
     * host sends the X-API-Token unencrypted — legal for LAN setups but worth a warning.
     */
    private fun syncHost(userId: Int): String {
        val host = userConfig.syncYomiHost.value(userId).removeSuffix("/")
        if (host.startsWith("http://")) {
            logger.warn { "SyncYomi host for user $userId uses cleartext http; the API token is sent unencrypted" }
        }
        return host
    }

    /**
     * The last successful sync's watermark in epoch milliseconds. SyncManager's
     * finishWithSuccess writes milliseconds today, but early installs stored seconds;
     * a value too small to be a sane millisecond timestamp (< ~Sep 2001 in ms) is
     * converted on read.
     */
    internal fun lastSyncTimestampMs(userId: Int): Long {
        val raw = syncPreferences.getLong("${LAST_SYNC_TIMESTAMP_PREF}_$userId", 0L)
        return if (raw in 1 until MILLIS_TIMESTAMP_FLOOR) raw * 1000 else raw
    }

    /**
     * Backup `lastModifiedAt` values are only second-granular, so a modification inside
     * the same second as the last sync cannot be proven to predate it. Keeping such an
     * entry (and maybe re-uploading it once) beats silently inferring it as deleted —
     * the deletion inference propagates to the server and every other device.
     */
    internal fun modifiedSinceLastSync(
        lastModifiedAtSeconds: Long,
        lastSyncTimeMs: Long,
    ): Boolean = lastSyncTimeMs == 0L || (lastModifiedAtSeconds + 1).seconds.inWholeMilliseconds > lastSyncTimeMs

    private fun baseHeaders(
        apiKey: String,
        userId: Int,
    ): Headers.Builder =
        Headers
            .Builder()
            .add("X-API-Token", apiKey)
            .add("X-Device-ID", deviceId(userId))
            .add("X-Device-Name", DEVICE_NAME)

    private fun syncClient() =
        network.client
            .newBuilder()
            .connectTimeout(syncClientTimeout)
            .readTimeout(syncClientTimeout)
            .writeTimeout(syncClientTimeout)
            .build()

    private suspend fun pullSyncData(userId: Int): Pair<SyncData?, String> {
        val host = syncHost(userId)
        val apiKey = userConfig.syncYomiApiKey.value(userId)
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey, userId)
        val lastETag = syncPreferences.getString("last_sync_etag_$userId", "") ?: ""
        if (lastETag != "") {
            headersBuilder.add("If-None-Match", lastETag)
        }
        val headers = headersBuilder.build()

        val downloadRequest =
            GET(
                url = downloadUrl,
                headers = headers,
            )

        val response = network.client.newCall(downloadRequest).await()

        if (response.code == HttpStatus.NOT_MODIFIED.code) {
            // not modified
            require(lastETag.isNotEmpty())
            logger.info { "Remote server not modified" }
            return Pair(null, lastETag)
        } else if (response.code == HttpStatus.NOT_FOUND.code) {
            // the remote backup is gone; drop the stale etag so no If-None-Match is sent
            // for a resource that no longer exists
            syncPreferences.edit().remove("last_sync_etag_$userId").apply()
            return Pair(null, "")
        }

        if (response.isSuccessful) {
            val newETag =
                response.headers["ETag"]
                    ?.takeIf { it.isNotEmpty() } ?: throw SyncYomiException("Missing ETag")

            val byteArray = response.body.bytes()

            try {
                val backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                return Pair(SyncData(backup = backup), newETag)
            } catch (e: SerializationException) {
                // undecodable remote data is an error, not "remote is empty": returning
                // null here would drop the etag and let the push below overwrite the
                // whole remote library with local data
                logger.error(e) { "Bad content responded from server" }
                throw SyncYomiException("Failed to decode remote backup: ${e.message}")
            }
        } else {
            val responseBody = response.body.string()
            logger.error { "SyncError: $responseBody" }
            throw SyncYomiException("Failed to download sync data: $responseBody")
        }
    }

    private suspend fun pushSyncData(
        userId: Int,
        syncData: SyncData,
        eTag: String,
    ) {
        val backup = syncData.backup ?: return

        val host = syncHost(userId)
        val apiKey = userConfig.syncYomiApiKey.value(userId)
        val uploadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey, userId).add("Content-Encoding", "gzip")
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        val client = syncClient()

        val body = BackupRequestBody(backup, ProtoBuf, gzip = true)
        if (body.metaBytes.isEmpty() && backup.backupManga.isEmpty()) {
            throw IllegalStateException("Empty backup error")
        }

        val uploadRequest =
            PUT(
                url = uploadUrl,
                headers = headers,
                body = body,
            )

        val response = client.newCall(uploadRequest).await()

        if (response.isSuccessful) {
            val newETag =
                response.headers["ETag"]
                    ?.takeIf { it.isNotEmpty() } ?: throw SyncYomiException("Missing ETag")
            syncPreferences
                .edit()
                .putString("last_sync_etag_$userId", newETag)
                .apply()
            logger.debug { "SyncYomi sync completed for user $userId" }
            return
        }

        if (response.code == HttpStatus.PRECONDITION_FAILED.code) {
            // another client updated the remote backup since our pull — the push fails
            // and the whole sync is retried on the next run instead of being marked ok
            throw SyncYomiException("Failed to push sync data: remote backup was changed by another client (412)")
        }

        val responseBody = response.body.string()
        logger.error { "SyncError: $responseBody" }
        throw SyncYomiException("Failed to push sync data: ${response.code} $responseBody")
    }

    private suspend fun reportSyncEvent(
        userId: Int,
        event: SyncEventStatus,
        message: String? = null,
    ) {
        try {
            val host = syncHost(userId)
            val apiKey = userConfig.syncYomiApiKey.value(userId)
            val url = "$host/api/sync/event"

            val headers = baseHeaders(apiKey, userId).build()

            val bodyObj =
                SyncEvent(
                    event = event,
                    device_id = deviceId(userId),
                    device_name = DEVICE_NAME,
                    message = message,
                )

            val jsonBody = Json.encodeToString(SyncEvent.serializer(), bodyObj)
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request =
                POST(
                    url = url,
                    headers = headers,
                    body = requestBody,
                )

            network.client
                .newCall(request)
                .await()
                .close()
        } catch (e: Exception) {
            logger.error { "Failed to report sync event: ${e.message}" }
        }
    }

    fun mergeSyncData(
        userId: Int,
        localSyncData: SyncData,
        remoteSyncData: SyncData,
    ): SyncData {
        val mergedCategoriesList =
            mergeCategoriesLists(userId, localSyncData.backup?.backupCategories, remoteSyncData.backup?.backupCategories)

        val mergedMangaList =
            mergeMangaLists(
                userId,
                localSyncData.backup?.backupManga,
                remoteSyncData.backup?.backupManga,
                localSyncData.backup?.backupCategories ?: emptyList(),
                remoteSyncData.backup?.backupCategories ?: emptyList(),
                mergedCategoriesList,
            )

        val mergedSourcesList =
            mergeSourcesLists(localSyncData.backup?.backupSources, remoteSyncData.backup?.backupSources)

        // Create the merged Backup object
        val mergedBackup =
            Backup(
                backupManga = mergedMangaList,
                backupCategories = mergedCategoriesList,
                backupSources = mergedSourcesList,
                meta = emptyMap(),
                serverSettings = null,
                userSettings = null,
            )

        // Create the merged SData object
        return SyncData(
            backup = mergedBackup,
        )
    }

    private fun mergeMangaLists(
        userId: Int,
        localMangaList: List<BackupManga>?,
        remoteMangaList: List<BackupManga>?,
        localCategories: List<BackupCategory>,
        remoteCategories: List<BackupCategory>,
        mergedCategories: List<BackupCategory>,
    ): List<BackupManga> {
        val localMangaListSafe = localMangaList.orEmpty()
        val remoteMangaListSafe = remoteMangaList.orEmpty()

        logger.debug { "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}" }

        fun mangaCompositeKey(manga: BackupManga): String = "${manga.source}|${manga.url}"

        // Create maps using composite keys
        val localMangaMap = localMangaListSafe.associateBy { mangaCompositeKey(it) }
        val remoteMangaMap = remoteMangaListSafe.associateBy { mangaCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(
            theManga: BackupManga,
            theMap: Map<Int, BackupCategory>,
        ): BackupManga =
            theManga.copy(
                categories =
                    theManga.categories.mapNotNull {
                        theMap[it]?.let { category ->
                            mergedCategoriesMapByName[category.name]?.order
                        }
                    },
            )

        val lastSyncTimeMs = lastSyncTimestampMs(userId)
        val syncingChapters = userConfig.syncDataChapters.value(userId)

        val mergedList =
            (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
                val local = localMangaMap[compositeKey]
                val remote = remoteMangaMap[compositeKey]

                // New version comparison logic
                when {
                    local != null && remote == null -> {
                        if (modifiedSinceLastSync(local.lastModifiedAt, lastSyncTimeMs)) {
                            updateCategories(local, localCategoriesMapByOrder)
                        } else {
                            logger.debug { "Dropping local manga deleted on remote: ${local.title}." }
                            null
                        }
                    }

                    local == null && remote != null -> {
                        if (modifiedSinceLastSync(remote.lastModifiedAt, lastSyncTimeMs)) {
                            updateCategories(remote, remoteCategoriesMapByOrder)
                        } else {
                            logger.debug { "Dropping deleted remote manga: ${remote.title}." }
                            null
                        }
                    }

                    local != null && remote != null -> {
                        // Compare versions to decide which manga to keep; ties resolve by
                        // lastModifiedAt, and a full tie takes remote so every device
                        // converges on the server copy instead of flip-flopping
                        if (local.version > remote.version ||
                            (local.version == remote.version && local.lastModifiedAt > remote.lastModifiedAt)
                        ) {
                            logger.debug { "Keeping local version of ${local.title} with merged chapters." }
                            updateCategories(
                                local.copy(
                                    chapters =
                                        mergeChapters(
                                            local.chapters,
                                            remote.chapters,
                                            lastSyncTimeMs,
                                            syncingChapters,
                                        ),
                                ),
                                localCategoriesMapByOrder,
                            )
                        } else {
                            logger.debug { "Keeping remote version of ${remote.title} with merged chapters." }
                            updateCategories(
                                remote.copy(
                                    chapters =
                                        mergeChapters(
                                            local.chapters,
                                            remote.chapters,
                                            lastSyncTimeMs,
                                            syncingChapters,
                                        ),
                                ),
                                remoteCategoriesMapByOrder,
                            )
                        }
                    }

                    else -> {
                        null
                    } // No manga found for key
                }
            }

        // Counting favorites and non-favorites
        val (favorites, nonFavorites) = mergedList.partition { it.favorite }

        logger.debug {
            "Merge completed. Total merged manga: ${mergedList.size}, Favorites: ${favorites.size}, Non-Favorites: ${nonFavorites.size}"
        }

        return mergedList
    }

    private fun mergeChapters(
        localChapters: List<BackupChapter>,
        remoteChapters: List<BackupChapter>,
        lastSyncTimeMs: Long,
        syncingChapters: Boolean,
    ): List<BackupChapter> {
        if (!syncingChapters) {
            return remoteChapters // If not syncing chapters, keep remote untouched
        }

        fun chapterCompositeKey(chapter: BackupChapter): String = chapter.url

        val localChapterMap = localChapters.associateBy { chapterCompositeKey(it) }
        val remoteChapterMap = remoteChapters.associateBy { chapterCompositeKey(it) }

        logger.debug { "Starting chapter merge. Local chapters: ${localChapters.size}, Remote chapters: ${remoteChapters.size}" }

        // Merge both chapter maps based on version numbers
        val mergedChapters =
            (localChapterMap.keys + remoteChapterMap.keys).distinct().mapNotNull { compositeKey ->
                val localChapter = localChapterMap[compositeKey]
                val remoteChapter = remoteChapterMap[compositeKey]

                logger.debug {
                    "Processing chapter key: $compositeKey. Local chapter: ${localChapter != null}, Remote chapter: ${remoteChapter != null}"
                }

                when {
                    localChapter != null && remoteChapter == null -> {
                        if (modifiedSinceLastSync(localChapter.lastModifiedAt, lastSyncTimeMs)) {
                            logger.debug { "Keeping local chapter: ${localChapter.name}." }
                            localChapter
                        } else {
                            logger.debug { "Dropping local chapter deleted on remote: ${localChapter.name}." }
                            null
                        }
                    }

                    localChapter == null && remoteChapter != null -> {
                        if (modifiedSinceLastSync(remoteChapter.lastModifiedAt, lastSyncTimeMs)) {
                            logger.debug { "Taking remote chapter: ${remoteChapter.name}." }
                            remoteChapter
                        } else {
                            logger.debug { "Dropping deleted remote chapter: ${remoteChapter.name}." }
                            null
                        }
                    }

                    localChapter != null && remoteChapter != null -> {
                        // Same tie-break as manga: a full tie takes remote so all devices
                        // converge on the server copy
                        val localWins =
                            localChapter.version > remoteChapter.version ||
                                (
                                    localChapter.version == remoteChapter.version &&
                                        localChapter.lastModifiedAt > remoteChapter.lastModifiedAt
                                )
                        val chosenChapter =
                            if (localWins) {
                                // The remote list is the ordering reference for shared
                                // urls (e.g. it may carry renumbered positions), so the
                                // chosen local content keeps the remote sourceOrder
                                localChapter.copy(sourceOrder = remoteChapter.sourceOrder)
                            } else {
                                remoteChapter
                            }
                        logger.debug {
                            "Merging chapter: ${chosenChapter.name}. Chosen version from: ${if (localWins) "Local" else "Remote"}, Local version: ${localChapter.version}, Remote version: ${remoteChapter.version}."
                        }
                        chosenChapter
                    }

                    else -> {
                        logger.debug { "No chapter found for composite key: $compositeKey. Skipping." }
                        null
                    }
                }
            }

        logger.debug { "Chapter merge completed. Total merged chapters: ${mergedChapters.size}" }

        return mergedChapters
    }

    private fun mergeCategoriesLists(
        userId: Int,
        localCategoriesList: List<BackupCategory>?,
        remoteCategoriesList: List<BackupCategory>?,
    ): List<BackupCategory> {
        if (localCategoriesList == null) return remoteCategoriesList ?: emptyList()
        if (remoteCategoriesList == null) return localCategoriesList
        val result = mutableListOf<BackupCategory>()

        // Match and mark by list index: distinct categories may share a name, and two
        // structurally equal entries are still different rows — value lookups and a
        // data-class-equality processed-set would collapse both cases into one
        val processedLocalIndices = mutableSetOf<Int>()
        val localIndexByUid =
            localCategoriesList
                .mapIndexedNotNull { index, category ->
                    if (category.uid != 0L) category.uid to index else null
                }.toMap()
        val localIndicesByName =
            localCategoriesList
                .mapIndexed { index, category -> category.name to index }
                .groupBy({ it.first }, { it.second })

        val lastSyncTimeMs = lastSyncTimestampMs(userId)

        remoteCategoriesList.forEach { remote ->
            var localIndex: Int? = null

            // 1. Try match by UID
            if (remote.uid != 0L) {
                localIndex = localIndexByUid[remote.uid]
            }

            // 2. Try match by Name (fallback), but only when the uid cannot contradict
            //    it — a same-named category with a different uid is a different category
            if (localIndex == null) {
                localIndex =
                    localIndicesByName[remote.name]?.firstOrNull { index ->
                        index !in processedLocalIndices &&
                            (remote.uid == 0L || localCategoriesList[index].uid == 0L || localCategoriesList[index].uid == remote.uid)
                    }
            }

            if (localIndex != null) {
                val localMatch = localCategoriesList[localIndex]
                processedLocalIndices.add(localIndex)
                // Conflict resolution
                if (localMatch.version >= remote.version) {
                    logger.debug { "Keeping local category: ${localMatch.name} (UID: ${localMatch.uid})" }
                    result.add(localMatch)
                } else {
                    logger.debug { "Keeping remote category: ${remote.name} (UID: ${remote.uid})" }
                    // Preserve Local UID if Remote was 0
                    if (remote.uid == 0L) {
                        remote.uid = localMatch.uid
                    }
                    result.add(remote)
                }
            } else {
                if (modifiedSinceLastSync(remote.lastModifiedAt, lastSyncTimeMs)) {
                    logger.debug { "Adding new remote category: ${remote.name} (UID: ${remote.uid})" }
                    result.add(remote)
                } else {
                    logger.debug { "Dropping deleted remote category: ${remote.name} (UID: ${remote.uid})" }
                }
            }
        }

        // Add remaining Local Categories
        localCategoriesList.forEachIndexed { index, local ->
            if (index !in processedLocalIndices) {
                if (modifiedSinceLastSync(local.lastModifiedAt, lastSyncTimeMs)) {
                    logger.debug { "Keeping local only category: ${local.name} (UID: ${local.uid})" }
                    result.add(local)
                } else {
                    logger.debug { "Dropping local category deleted on remote: ${local.name} (UID: ${local.uid})" }
                }
            }
        }

        return result.sortedBy { it.order }
    }

    private fun mergeSourcesLists(
        localSources: List<BackupSource>?,
        remoteSources: List<BackupSource>?,
    ): List<BackupSource> {
        // Create maps using sourceId as key
        val localSourceMap = localSources?.associateBy { it.sourceId } ?: emptyMap()
        val remoteSourceMap = remoteSources?.associateBy { it.sourceId } ?: emptyMap()

        logger.debug { "Starting source merge. Local sources: ${localSources?.size}, Remote sources: ${remoteSources?.size}" }

        // Merge both source maps
        val mergedSources =
            (localSourceMap.keys + remoteSourceMap.keys).distinct().mapNotNull { sourceId ->
                val localSource = localSourceMap[sourceId]
                val remoteSource = remoteSourceMap[sourceId]

                logger.debug {
                    "Processing source ID: $sourceId. Local source: ${localSource != null}, Remote source: ${remoteSource != null}"
                }

                when {
                    localSource != null && remoteSource == null -> {
                        logger.debug { "Using local source: ${localSource.name}." }
                        localSource
                    }

                    remoteSource != null && localSource == null -> {
                        logger.debug { "Using remote source: ${remoteSource.name}." }
                        remoteSource
                    }

                    else -> {
                        logger.debug { "Remote and local have the same source ID: $sourceId. Keeping local." }
                        localSource
                    }
                }
            }

        logger.debug { "Source merge completed. Total merged sources: ${mergedSources.size}" }

        return mergedSources
    }
}
