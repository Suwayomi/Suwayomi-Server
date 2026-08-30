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
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
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
        val device_Name: String? = null,
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
    private val fullSyncInterval = 24.hours

    @Serializable
    private enum class SyncEventStatus {
        SYNC_STARTED,
        SYNC_SUCCESS,
        SYNC_FAILED,
        SYNC_ERROR,
        SYNC_CANCELLED,
    }

    suspend fun doSync(
        syncData: SyncData,
        full: Boolean,
        startDate: Instant,
        setSyncState: (SyncManager.SyncState) -> Unit,
    ): SyncResult {
        reportSyncEvent(SyncEventStatus.SYNC_STARTED)

        return try {
            if (supportsV2()) {
                doSyncV2(syncData, full, startDate, setSyncState)
            } else {
                doSyncV1(syncData, startDate, setSyncState)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                reportSyncEvent(SyncEventStatus.SYNC_CANCELLED, e.message)
                throw e
            }
            logger.error { "Error syncing: ${e.message}" }
            reportSyncEvent(SyncEventStatus.SYNC_ERROR, e.message)
            throw e
        }
    }

    private suspend fun doSyncV1(
        syncData: SyncData,
        startDate: Instant,
        setSyncState: (SyncManager.SyncState) -> Unit,
    ): SyncResult {
        setSyncState(SyncManager.SyncState.Downloading(startDate))
        val (remoteData, etag) = pullSyncData()

        val finalSyncData =
            if (remoteData != null) {
                require(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
                logger.debug { "Try update remote data with ETag($etag)" }
                setSyncState(SyncManager.SyncState.Merging(startDate))
                mergeSyncData(syncData, remoteData)
            } else {
                // init or overwrite remote data
                logger.debug { "Try overwrite remote data with ETag($etag)" }
                syncData
            }

        if (finalSyncData.backup != null) {
            setSyncState(SyncManager.SyncState.Uploading(startDate))
        }

        val success = pushSyncData(finalSyncData, etag)
        if (success) {
            reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
        } else {
            reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Failed to push sync data")
        }

        return SyncResult(finalSyncData.backup, changed = remoteData != null, protocolV2 = false)
    }

    // Protocol v2: the server merges; we upload what changed and restore what comes back.
    private suspend fun doSyncV2(
        syncData: SyncData,
        full: Boolean,
        startDate: Instant,
        setSyncState: (SyncManager.SyncState) -> Unit,
    ): SyncResult {
        val backup = syncData.backup ?: return SyncResult(null, changed = false, protocolV2 = true)
        val host = serverConfig.syncYomiHost.value
        val apiKey = serverConfig.syncYomiApiKey.value

        val pendingDeleted = pendingDeletedCategories()
        val headers =
            baseHeaders(apiKey)
                .add("X-Sync-Cursor", syncCursor().toString())
                .add("X-Sync-Full", full.toString())
        if (pendingDeleted.isNotEmpty()) {
            headers.add("X-Sync-Deleted-Categories", pendingDeleted.joinToString(","))
        }

        setSyncState(SyncManager.SyncState.Uploading(startDate))
        val body =
            ProtoBuf
                .encodeToByteArray(Backup.serializer(), backup)
                .toRequestBody("application/octet-stream".toMediaType())
        val response =
            syncClient()
                .newCall(POST(url = "$host/api/sync/v2/merge", headers = headers.build(), body = body))
                .await()

        if (!response.isSuccessful) {
            val responseBody = response.body.string()
            logger.error { "SyncError (${response.code}): $responseBody" }
            reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Server answered ${response.code}")
            throw SyncYomiException("Failed to sync: ${response.code} $responseBody")
        }

        setSyncState(SyncManager.SyncState.Downloading(startDate))
        val bytes = response.body.byteStream().use { it.readBytes() }
        val remote =
            try {
                ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
            } catch (e: SerializationException) {
                logger.error(e) { "Bad content responded from server" }
                reportSyncEvent(SyncEventStatus.SYNC_FAILED, "Bad content responded from server")
                throw SyncYomiException("Bad content responded from server: ${e.message}")
            }
        val cursor =
            response.headers["X-Sync-Cursor"]?.toLongOrNull()
                ?: throw SyncYomiException("Missing X-Sync-Cursor")
        val changed = response.headers["X-Sync-Changed"]?.toBoolean() ?: true
        val fullRequested = response.headers["X-Sync-Full-Requested"]?.toBoolean() ?: false

        syncPreferences
            .edit()
            .putLong(PREF_CURSOR, cursor)
            .putBoolean(PREF_FULL_REQUESTED, fullRequested)
            .putStringSet(PREF_PENDING_DELETED_CATEGORIES, pendingDeletedCategories() - pendingDeleted)
            .apply()
        if (full) {
            syncPreferences.edit().putLong(PREF_LAST_FULL_SYNC, startDate.toEpochMilliseconds()).apply()
        }
        logger.debug { "SyncYomi v2 merge done: cursor=$cursor changed=$changed fullRequested=$fullRequested" }

        reportSyncEvent(SyncEventStatus.SYNC_SUCCESS)
        return SyncResult(remote, changed = changed, protocolV2 = true)
    }

    // A full library is needed on the first sync, when the server asks for it, and once a day as a safety net.
    suspend fun needsFullSync(): Boolean {
        if (!supportsV2()) return true
        if (syncCursor() == 0L || syncPreferences.getBoolean(PREF_FULL_REQUESTED, false)) return true
        val lastFull = syncPreferences.getLong(PREF_LAST_FULL_SYNC, 0L)
        return lastFull == 0L || Clock.System.now() - Instant.fromEpochMilliseconds(lastFull) > fullSyncInterval
    }

    suspend fun supportsV2(): Boolean {
        val host = serverConfig.syncYomiHost.value
        if (syncPreferences.getString(PREF_V2_PROBED_HOST, null) == host) {
            return syncPreferences.getBoolean(PREF_V2_SUPPORTED, false)
        }

        val request = GET(url = "$host/api/sync/v2/capabilities", headers = baseHeaders(serverConfig.syncYomiApiKey.value).build())
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
            .putString(PREF_V2_PROBED_HOST, host)
            .putBoolean(PREF_V2_SUPPORTED, supported)
            .apply()
        logger.info { "SyncYomi server supports protocol v2: $supported" }
        return supported
    }

    fun syncCursor(): Long = syncPreferences.getLong(PREF_CURSOR, 0L)

    fun deviceId(): String {
        syncPreferences.getString(PREF_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        syncPreferences.edit().putString(PREF_DEVICE_ID, id).apply()
        return id
    }

    fun rememberDeletedCategory(uid: Long) {
        if (uid == 0L || !serverConfig.syncYomiEnabled.value) return
        syncPreferences
            .edit()
            .putStringSet(PREF_PENDING_DELETED_CATEGORIES, pendingDeletedCategories() + uid.toString())
            .apply()
    }

    private fun pendingDeletedCategories(): Set<String> =
        syncPreferences.getStringSet(PREF_PENDING_DELETED_CATEGORIES, emptySet()).orEmpty().toSet()

    private fun baseHeaders(apiKey: String): Headers.Builder =
        Headers
            .Builder()
            .add("X-API-Token", apiKey)
            .add("X-Device-ID", deviceId())
            .add("X-Device-Name", DEVICE_NAME)

    private fun syncClient() =
        network.client
            .newBuilder()
            .connectTimeout(30.seconds)
            .readTimeout(30.seconds)
            .writeTimeout(30.seconds)
            .build()

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        val host = serverConfig.syncYomiHost.value
        val apiKey = serverConfig.syncYomiApiKey.value
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey)
        val lastETag = syncPreferences.getString("last_sync_etag", "") ?: ""
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
            // maybe got deleted from remote
            return Pair(null, "")
        }

        if (response.isSuccessful) {
            val newETag =
                response.headers["ETag"]
                    ?.takeIf { it.isNotEmpty() } ?: throw SyncYomiException("Missing ETag")

            val byteArray =
                response.body.byteStream().use {
                    return@use it.readBytes()
                }

            return try {
                val backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                return Pair(SyncData(backup = backup), newETag)
            } catch (_: SerializationException) {
                logger.info { "Bad content responsed from server" }
                // the body is invalid
                // return default value so we can overwrite it
                Pair(null, "")
            }
        } else {
            val responseBody = response.body.string()
            logger.error { "SyncError: $responseBody" }
            throw SyncYomiException("Failed to download sync data: $responseBody")
        }
    }

    private suspend fun pushSyncData(
        syncData: SyncData,
        eTag: String,
    ): Boolean {
        val backup = syncData.backup ?: return true

        val host = serverConfig.syncYomiHost.value
        val apiKey = serverConfig.syncYomiApiKey.value
        val uploadUrl = "$host/api/sync/content"

        val headersBuilder = baseHeaders(apiKey)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        val client = syncClient()

        val byteArray = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException("Empty backup error")
        }
        val body = byteArray.toRequestBody("application/octet-stream".toMediaType())

        val uploadRequest =
            PUT(
                url = uploadUrl,
                headers = headers,
                body = body,
            )

        val response = client.newCall(uploadRequest).await()

        return if (response.isSuccessful) {
            val newETag =
                response.headers["ETag"]
                    ?.takeIf { it.isNotEmpty() } ?: throw SyncYomiException("Missing ETag")
            syncPreferences
                .edit()
                .putString("last_sync_etag", newETag)
                .apply()
            logger.debug { "SyncYomi sync completed" }
            true
        } else if (response.code == HttpStatus.PRECONDITION_FAILED.code) {
            // other clients updated remote data, will try next time
            logger.debug { "SyncYomi sync failed with 412" }
            false
        } else {
            val responseBody = response.body.string()
            logger.error { "SyncError: $responseBody" }
            false
        }
    }

    private suspend fun reportSyncEvent(
        event: SyncEventStatus,
        message: String? = null,
    ) {
        try {
            val host = serverConfig.syncYomiHost.value
            val apiKey = serverConfig.syncYomiApiKey.value
            val url = "$host/api/sync/event"

            val headers = baseHeaders(apiKey).build()

            val bodyObj =
                SyncEvent(
                    event = event,
                    device_id = deviceId(),
                    device_Name = DEVICE_NAME,
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
        localSyncData: SyncData,
        remoteSyncData: SyncData,
    ): SyncData {
        val mergedCategoriesList =
            mergeCategoriesLists(localSyncData.backup?.backupCategories, remoteSyncData.backup?.backupCategories)

        val mergedMangaList =
            mergeMangaLists(
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
            )

        // Create the merged SData object
        return SyncData(
            backup = mergedBackup,
        )
    }

    private fun mergeMangaLists(
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

        val lastSyncTime = syncPreferences.getLong("last_sync_timestamp", 0).milliseconds.inWholeSeconds

        val mergedList =
            (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
                val local = localMangaMap[compositeKey]
                val remote = remoteMangaMap[compositeKey]

                // New version comparison logic
                when {
                    local != null && remote == null -> {
                        if (lastSyncTime == 0L || local.lastModifiedAt > lastSyncTime) {
                            updateCategories(local, localCategoriesMapByOrder)
                        } else {
                            logger.debug { "Dropping local manga deleted on remote: ${local.title}." }
                            null
                        }
                    }

                    local == null && remote != null -> {
                        if (lastSyncTime == 0L || remote.lastModifiedAt > lastSyncTime) {
                            updateCategories(remote, remoteCategoriesMapByOrder)
                        } else {
                            logger.debug { "Dropping deleted remote manga: ${remote.title}." }
                            null
                        }
                    }

                    local != null && remote != null -> {
                        // Compare versions to decide which manga to keep
                        if (local.version >= remote.version) {
                            logger.debug { "Keeping local version of ${local.title} with merged chapters." }
                            updateCategories(
                                local.copy(
                                    chapters =
                                        mergeChapters(
                                            local.chapters,
                                            remote.chapters,
                                            lastSyncTime,
                                            serverConfig.syncDataChapters.value,
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
                                            lastSyncTime,
                                            serverConfig.syncDataChapters.value,
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
        lastSyncTime: Long,
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
                        if (lastSyncTime == 0L || localChapter.lastModifiedAt > lastSyncTime) {
                            logger.debug { "Keeping local chapter: ${localChapter.name}." }
                            localChapter
                        } else {
                            logger.debug { "Dropping local chapter deleted on remote: ${localChapter.name}." }
                            null
                        }
                    }

                    localChapter == null && remoteChapter != null -> {
                        if (lastSyncTime == 0L || remoteChapter.lastModifiedAt > lastSyncTime) {
                            logger.debug { "Taking remote chapter: ${remoteChapter.name}." }
                            remoteChapter
                        } else {
                            logger.debug { "Dropping deleted remote chapter: ${remoteChapter.name}." }
                            null
                        }
                    }

                    localChapter != null && remoteChapter != null -> {
                        // Use version number to decide which chapter to keep
                        val chosenChapter =
                            if (localChapter.version >= remoteChapter.version) {
                                // If there are more chapter on remote, local sourceOrder will need to be updated to maintain correct source order.
                                if (localChapters.size < remoteChapters.size) {
                                    localChapter.copy(sourceOrder = remoteChapter.sourceOrder)
                                } else {
                                    localChapter
                                }
                            } else {
                                remoteChapter
                            }
                        logger.debug {
                            "Merging chapter: ${chosenChapter.name}. Chosen version from: ${if (localChapter.version >= remoteChapter.version) "Local" else "Remote"}, Local version: ${localChapter.version}, Remote version: ${remoteChapter.version}."
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
        localCategoriesList: List<BackupCategory>?,
        remoteCategoriesList: List<BackupCategory>?,
    ): List<BackupCategory> {
        if (localCategoriesList == null) return remoteCategoriesList ?: emptyList()
        if (remoteCategoriesList == null) return localCategoriesList
        val result = mutableListOf<BackupCategory>()
        val processedLocals = mutableSetOf<BackupCategory>()

        val localMapByUid = localCategoriesList.filter { it.uid != 0L }.associateBy { it.uid }
        val localMapByName = localCategoriesList.associateBy { it.name }

        val lastSyncTime = syncPreferences.getLong("last_sync_timestamp", 0)

        remoteCategoriesList.forEach { remote ->
            var localMatch: BackupCategory? = null

            // 1. Try match by UID
            if (remote.uid != 0L) {
                localMatch = localMapByUid[remote.uid]
            }

            // 2. Try match by Name (fallback)
            if (localMatch == null) {
                localMatch = localMapByName[remote.name]
            }

            if (localMatch != null) {
                processedLocals.add(localMatch)
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
                val remoteModifiedTimeMillis = remote.lastModifiedAt.seconds.inWholeMilliseconds
                if (lastSyncTime == 0L || remoteModifiedTimeMillis > lastSyncTime) {
                    logger.debug { "Adding new remote category: ${remote.name} (UID: ${remote.uid})" }
                    result.add(remote)
                } else {
                    logger.debug { "Dropping deleted remote category: ${remote.name} (UID: ${remote.uid})" }
                }
            }
        }

        // Add remaining Local Categories
        localCategoriesList.forEach { local ->
            if (local !in processedLocals) {
                val localModifiedTimeMillis = local.lastModifiedAt.seconds.inWholeMilliseconds
                if (lastSyncTime == 0L || localModifiedTimeMillis > lastSyncTime) {
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
