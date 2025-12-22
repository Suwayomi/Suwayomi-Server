package suwayomi.tachidesk.global.impl.sync

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.HttpStatus
import kotlinx.serialization.SerializationException
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
import kotlin.time.Duration.Companion.seconds

object SyncYomiSyncService {
    private val syncPreferences = Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)

    private val network: NetworkHelper by injectLazy()
    private val logger = KotlinLogging.logger {}

    private class SyncYomiException(
        message: String?,
    ) : Exception(message)

    suspend fun doSync(syncData: SyncData): Backup? {
        try {
            val (remoteData, etag) = pullSyncData()

            val finalSyncData =
                if (remoteData != null) {
                    require(etag.isNotEmpty()) { "ETag should never be empty if remote data is not null" }
                    logger.debug { "Try update remote data with ETag($etag)" }
                    mergeSyncData(syncData, remoteData)
                } else {
                    // init or overwrite remote data
                    logger.debug { "Try overwrite remote data with ETag($etag)" }
                    syncData
                }

            pushSyncData(finalSyncData, etag)
            return finalSyncData.backup
        } catch (e: Exception) {
            logger.error { "Error syncing: ${e.message}" }
            return null
        }
    }

    private suspend fun pullSyncData(): Pair<SyncData?, String> {
        val host = serverConfig.syncYomiHost.value
        val apiKey = serverConfig.syncYomiApiKey.value
        val downloadUrl = "$host/api/sync/content"

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
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
    ) {
        val backup = syncData.backup ?: return

        val host = serverConfig.syncYomiHost.value
        val apiKey = serverConfig.syncYomiApiKey.value
        val uploadUrl = "$host/api/sync/content"

        val headersBuilder = Headers.Builder().add("X-API-Token", apiKey)
        if (eTag.isNotEmpty()) {
            headersBuilder.add("If-Match", eTag)
        }
        val headers = headersBuilder.build()

        // Set timeout to 30 seconds
        val timeout = 30.seconds
        val client =
            network.client
                .newBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build()

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

        if (response.isSuccessful) {
            val newETag =
                response.headers["ETag"]
                    ?.takeIf { it.isNotEmpty() } ?: throw SyncYomiException("Missing ETag")
            syncPreferences
                .edit()
                .putString("last_sync_etag", newETag)
                .apply()
            logger.debug { "SyncYomi sync completed" }
        } else if (response.code == HttpStatus.PRECONDITION_FAILED.code) {
            // other clients updated remote data, will try next time
            logger.debug { "SyncYomi sync failed with 412" }
        } else {
            val responseBody = response.body.string()
            logger.error { "SyncError: $responseBody" }
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

        fun mangaCompositeKey(manga: BackupManga): String =
            "${manga.source}|${manga.url}|${manga.title.lowercase().trim()}|${manga.author?.lowercase()?.trim()}"

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

        logger.debug { "Starting merge. Local list size: ${localMangaListSafe.size}, Remote list size: ${remoteMangaListSafe.size}" }

        val mergedList =
            (localMangaMap.keys + remoteMangaMap.keys).distinct().mapNotNull { compositeKey ->
                val local = localMangaMap[compositeKey]
                val remote = remoteMangaMap[compositeKey]

                // New version comparison logic
                when {
                    local != null && remote == null -> {
                        updateCategories(local, localCategoriesMapByOrder)
                    }

                    local == null && remote != null -> {
                        updateCategories(remote, remoteCategoriesMapByOrder)
                    }

                    local != null && remote != null -> {
                        // Compare versions to decide which manga to keep
                        if (local.version >= remote.version) {
                            logger.debug { "Keeping local version of ${local.title} with merged chapters." }
                            updateCategories(
                                local.copy(chapters = mergeChapters(local.chapters, remote.chapters)),
                                localCategoriesMapByOrder,
                            )
                        } else {
                            logger.debug { "Keeping remote version of ${remote.title} with merged chapters." }
                            updateCategories(
                                remote.copy(chapters = mergeChapters(local.chapters, remote.chapters)),
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
    ): List<BackupChapter> {
        fun chapterCompositeKey(chapter: BackupChapter): String = "${chapter.url}|${chapter.name}|${chapter.chapterNumber}"

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
                        logger.debug { "Keeping local chapter: ${localChapter.name}." }
                        localChapter
                    }

                    localChapter == null && remoteChapter != null -> {
                        logger.debug { "Taking remote chapter: ${remoteChapter.name}." }
                        remoteChapter
                    }

                    localChapter != null && remoteChapter != null -> {
                        // Use version number to decide which chapter to keep
                        val chosenChapter =
                            if (localChapter.version >= remoteChapter.version) {
                                // If there mare more chapter on remote, local sourceOrder will need to be updated to maintain correct source order.
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
        val localCategoriesMap = localCategoriesList.associateBy { it.name }
        val remoteCategoriesMap = remoteCategoriesList.associateBy { it.name }

        val mergedCategoriesMap = mutableMapOf<String, BackupCategory>()

        localCategoriesMap.forEach { (name, localCategory) ->
            val remoteCategory = remoteCategoriesMap[name]
            if (remoteCategory != null) {
                // Compare and merge local and remote categories
                val mergedCategory =
                    if (localCategory.order > remoteCategory.order) {
                        localCategory
                    } else {
                        remoteCategory
                    }
                mergedCategoriesMap[name] = mergedCategory
            } else {
                // If the category is only in the local list, add it to the merged list
                mergedCategoriesMap[name] = localCategory
            }
        }

        // Add any categories from the remote list that are not in the local list
        remoteCategoriesMap.forEach { (name, remoteCategory) ->
            if (!mergedCategoriesMap.containsKey(name)) {
                mergedCategoriesMap[name] = remoteCategory
            }
        }

        return mergedCategoriesMap.values.toList()
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
                        logger.debug { "Remote and local is not empty: $sourceId. Skipping." }
                        null
                    }
                }
            }

        logger.debug { "Source merge completed. Total merged sources: ${mergedSources.size}" }

        return mergedSources
    }
}
