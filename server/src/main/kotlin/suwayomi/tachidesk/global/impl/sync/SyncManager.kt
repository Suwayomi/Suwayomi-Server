package suwayomi.tachidesk.global.impl.sync

import android.app.Application
import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.StartSyncResult
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupCategoryHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupSourceHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

@Serializable
data class SyncData(
    val backup: Backup? = null,
)

object SyncManager {
    private val syncPreferences = Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val logger = KotlinLogging.logger {}

    private var currentTaskId: String? = null
    private val syncMutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleSyncTask() {
        serverConfig.subscribeTo(
            combine(
                serverConfig.syncYomiEnabled,
                serverConfig.syncInterval,
            ) { enabled, interval -> Pair(enabled, interval) },
            { (enabled, interval) ->
                currentTaskId =
                    if (enabled && interval > 0) {
                        val intervalMs = interval.minutes.inWholeMilliseconds

                        currentTaskId?.let { HAScheduler.deschedule(it) }

                        HAScheduler.schedule(
                            {
                                startSync()
                            },
                            interval = intervalMs,
                            delay = intervalMs,
                            name = "sync",
                        )
                    } else {
                        null
                    }
            },
            ignoreInitialValue = false,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startSync(): StartSyncResult {
        if (!serverConfig.syncYomiEnabled.value) {
            return StartSyncResult.SYNC_DISABLED
        }

        if (!syncMutex.tryLock()) {
            return StartSyncResult.SYNC_IN_PROGRESS
        }

        GlobalScope.launch {
            try {
                syncData()
            } finally {
                syncMutex.unlock()
            }
        }

        return StartSyncResult.SUCCESS
    }

    suspend fun ensureSync() {
        if (!serverConfig.syncYomiEnabled.value) {
            return
        }

        if (syncMutex.tryLock()) {
            // there is no ongoing sync, so start one
            try {
                syncData()
            } finally {
                syncMutex.unlock()
            }
        } else {
            // wait for the ongoing sync to finish
            syncMutex.withLock {}
        }
    }

    private suspend fun syncData() {
        transaction {
            MangaTable.update({ MangaTable.isSyncing eq true }) {
                it[isSyncing] = false
            }
            ChapterTable.update({ ChapterTable.isSyncing eq true }) {
                it[isSyncing] = false
            }
        }

        val backupFlags =
            BackupFlags(
                includeManga = serverConfig.syncDataManga.value,
                includeCategories = serverConfig.syncDataCategories.value,
                includeChapters = serverConfig.syncDataChapters.value,
                includeTracking = serverConfig.syncDataTracking.value,
                includeHistory = serverConfig.syncDataHistory.value,
                includeClientData = false,
                includeServerSettings = false,
            )

        val backupMangas = BackupMangaHandler.backup(backupFlags)

        val backup =
            Backup(
                BackupMangaHandler.backup(backupFlags),
                BackupCategoryHandler.backup(backupFlags).filter { it.name != Category.DEFAULT_CATEGORY_NAME },
                BackupSourceHandler.backup(backupMangas, backupFlags),
                emptyMap(),
                null,
            )

        val syncData =
            SyncData(
                backup = backup,
            )

        val remoteBackup = SyncYomiSyncService.doSync(syncData)

        if (remoteBackup == null) {
            logger.debug { "Skip restore due to network issues" }
            // should we call showSyncError?
            return
        }

        if (remoteBackup === syncData.backup) {
            // nothing changed
            logger.debug { "Skip restore due to remote was overwrite from local" }
            syncPreferences
                .edit()
                .putLong("last_sync_timestamp", Clock.System.now().toEpochMilliseconds())
                .apply()
            return
        }

        // Stop the sync early if the remote backup is null or empty
        if (remoteBackup.backupManga.isEmpty()) {
            return
        }

        val isLibraryEmpty =
            transaction {
                MangaTable
                    .selectAll()
                    .where { MangaTable.inLibrary eq true }
                    .empty()
            }

        // Check if it's first sync based on lastSyncTimestamp
        if (syncPreferences.getLong("last_sync_timestamp", 0) == 0L && !isLibraryEmpty) {
            // It's first sync no need to restore data. (just update remote data)
            syncPreferences
                .edit()
                .putLong("last_sync_timestamp", Clock.System.now().toEpochMilliseconds())
                .apply()
            return
        }

        val (filteredFavorites, nonFavorites) = filterFavoritesAndNonFavorites(remoteBackup)
        updateNonFavorites(nonFavorites)

        val newSyncData =
            backup.copy(
                backupManga = filteredFavorites,
                backupCategories = remoteBackup.backupCategories,
                backupSources = remoteBackup.backupSources,
            )

        // It's local sync no need to restore data. (just update remote data)
        if (filteredFavorites.isEmpty()) {
            // update the sync timestamp
            syncPreferences
                .edit()
                .putLong("last_sync_timestamp", Clock.System.now().toEpochMilliseconds())
                .apply()
            return
        }

        val backupStream = ProtoBuf.encodeToByteArray(Backup.serializer(), newSyncData).inputStream()
        ProtoBackupImport.restore(
            sourceStream = backupStream,
            flags =
                BackupFlags(
                    includeManga = true,
                    includeCategories = true,
                    includeChapters = true,
                    includeTracking = true,
                    includeHistory = true,
                    includeClientData = false,
                    includeServerSettings = false,
                ),
            isSync = true,
        )

        // update the sync timestamp
        syncPreferences
            .edit()
            .putLong("last_sync_timestamp", Clock.System.now().toEpochMilliseconds())
            .apply()
    }

    private fun isMangaDifferent(
        localManga: MangaDataClass,
        remoteManga: BackupManga,
    ): Boolean {
        val localChapters =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq localManga.id }
                    .map { ChapterTable.toDataClass(it) }
            }
        val localCategories =
            transaction {
                CategoryMangaTable
                    .innerJoin(CategoryTable)
                    .selectAll()
                    .where { CategoryMangaTable.manga eq localManga.id }
                    .map { it[CategoryTable.order] }
            }

        if (areChaptersDifferent(localChapters, remoteManga.chapters)) {
            return true
        }

        if (localManga.version != remoteManga.version) {
            return true
        }

        if (localCategories.toSet() != remoteManga.categories.toSet()) {
            return true
        }

        return false
    }

    private fun areChaptersDifferent(
        localChapters: List<ChapterDataClass>,
        remoteChapters: List<BackupChapter>,
    ): Boolean {
        val localChapterMap = localChapters.associateBy { it.url }
        val remoteChapterMap = remoteChapters.associateBy { it.url }

        if (localChapterMap.size != remoteChapterMap.size) {
            return true
        }

        for ((url, localChapter) in localChapterMap) {
            val remoteChapter = remoteChapterMap[url]

            // If a matching remote chapter doesn't exist, or the version numbers are different, consider them different
            if (remoteChapter == null || localChapter.version != remoteChapter.version) {
                return true
            }
        }

        return false
    }

    private fun filterFavoritesAndNonFavorites(backup: Backup): Pair<List<BackupManga>, List<BackupManga>> {
        val favorites = mutableListOf<BackupManga>()
        val nonFavorites = mutableListOf<BackupManga>()

        val elapsedTime =
            measureTime {
                logger.debug { "Starting to filter favorites and non-favorites from backup data." }

                backup.backupManga.forEach { remoteManga ->
                    val localManga =
                        transaction {
                            MangaTable
                                .selectAll()
                                .where {
                                    (MangaTable.sourceReference eq remoteManga.source) and
                                        (MangaTable.url eq remoteManga.url)
                                }.limit(1)
                                .map { MangaTable.toDataClass(it) }
                                .firstOrNull()
                        }

                    when {
                        // Checks if the manga is in favorites and needs updating or adding
                        remoteManga.favorite -> {
                            if (localManga == null || isMangaDifferent(localManga, remoteManga)) {
                                logger.debug { "Adding to favorites: ${remoteManga.title}" }
                                favorites.add(remoteManga)
                            } else {
                                logger.debug { "Already up-to-date favorite: ${remoteManga.title}" }
                            }
                        }

                        // Handle non-favorites
                        !remoteManga.favorite -> {
                            logger.debug { "Adding to non-favorites: ${remoteManga.title}" }
                            nonFavorites.add(remoteManga)
                        }
                    }
                }
            }

        logger.debug {
            "Filtering completed in $elapsedTime. Favorites found: ${favorites.size}, Non-favorites found: ${nonFavorites.size}"
        }

        return Pair(favorites, nonFavorites)
    }

    private fun updateNonFavorites(nonFavorites: List<BackupManga>) {
        nonFavorites.forEach { nonFavorite ->
            val localManga =
                transaction {
                    MangaTable
                        .selectAll()
                        .where {
                            (MangaTable.sourceReference eq nonFavorite.source) and
                                (MangaTable.url eq nonFavorite.url)
                        }.limit(1)
                        .map { MangaTable.toDataClass(it) }
                        .firstOrNull()
                }

            if (localManga != null) {
                if (localManga.inLibrary != nonFavorite.favorite) {
                    val updatedManga = localManga.copy(inLibrary = nonFavorite.favorite)
                    updateManga(updatedManga)
                }
            }
        }
    }

    private fun updateManga(manga: MangaDataClass) {
        transaction {
            MangaTable.update({ MangaTable.id eq manga.id }) {
                it[MangaTable.url] = manga.url
                it[MangaTable.title] = manga.title
                it[MangaTable.initialized] = manga.initialized

                it[MangaTable.artist] = manga.artist
                it[MangaTable.author] = manga.author
                it[MangaTable.description] = manga.description
                it[MangaTable.genre] = manga.genre.joinToString(separator = ", ")

                it[MangaTable.status] = MangaStatus.valueOf(manga.status).value
                it[MangaTable.thumbnail_url] = manga.thumbnailUrl
                it[MangaTable.thumbnailUrlLastFetched] = manga.thumbnailUrlLastFetched

                it[MangaTable.inLibrary] = manga.inLibrary
                it[MangaTable.inLibraryAt] = manga.inLibraryAt

                it[MangaTable.sourceReference] = manga.sourceId.toLong()

                it[MangaTable.realUrl] = manga.realUrl
                it[MangaTable.lastFetchedAt] = manga.lastFetchedAt ?: 0L
                it[MangaTable.chaptersLastFetchedAt] = manga.chaptersLastFetchedAt ?: 0L

                it[MangaTable.updateStrategy] = manga.updateStrategy.name

                it[MangaTable.version] = manga.version
            }
        }
    }
}
