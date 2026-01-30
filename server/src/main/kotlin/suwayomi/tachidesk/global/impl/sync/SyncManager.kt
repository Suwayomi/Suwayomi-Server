package suwayomi.tachidesk.global.impl.sync

import android.app.Application
import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
import suwayomi.tachidesk.graphql.types.SyncState
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.Library.handleMangaThumbnail
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
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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

    private val _lastSyncState: MutableStateFlow<SyncState?> = MutableStateFlow(null)
    val lastSyncState: StateFlow<SyncState?> = _lastSyncState.asStateFlow()

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleSyncTask() {
        serverConfig.subscribeTo(
            combine(
                serverConfig.syncYomiEnabled,
                serverConfig.syncInterval,
            ) { enabled, interval -> Pair(enabled, interval) },
            { (enabled, interval) ->
                currentTaskId?.let { HAScheduler.deschedule(it) }

                currentTaskId =
                    if (enabled && interval > 0.seconds) {
                        val lastSyncDate =
                            syncPreferences
                                .getLong("last_scheduled_sync", 0L)
                                .takeIf { it != 0L }
                                ?.let { Instant.fromEpochMilliseconds(it) }

                        if (lastSyncDate == null) {
                            syncPreferences
                                .edit()
                                .putLong("last_scheduled_sync", Clock.System.now().toEpochMilliseconds())
                                .apply()
                        }

                        val delay =
                            if (lastSyncDate != null) {
                                ((interval) - (Clock.System.now() - lastSyncDate)).coerceAtLeast(0.seconds)
                            } else {
                                interval
                            }

                        HAScheduler.schedule(
                            {
                                startSync(periodic = true)

                                syncPreferences
                                    .edit()
                                    .putLong("last_scheduled_sync", Clock.System.now().toEpochMilliseconds())
                                    .apply()
                            },
                            interval = interval.inWholeMilliseconds,
                            delay = delay.inWholeMilliseconds,
                            name = "sync",
                        )
                    } else {
                        syncPreferences
                            .edit()
                            .remove("last_scheduled_sync")
                            .apply()
                        null
                    }
            },
            ignoreInitialValue = false,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startSync(periodic: Boolean = false): StartSyncResult {
        if (!serverConfig.syncYomiEnabled.value) {
            return StartSyncResult.SYNC_DISABLED
        }

        if (!syncMutex.tryLock()) {
            return StartSyncResult.SYNC_IN_PROGRESS
        }

        GlobalScope.launch {
            try {
                syncData(periodic)
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

    private suspend fun syncData(periodic: Boolean = false) {
        val startInstant = Clock.System.now()
        _lastSyncState.value = SyncState.Started(startInstant)

        try {
            logger.info {
                if (periodic) {
                    "Starting periodic sync"
                } else {
                    "Starting manual sync"
                }
            }

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

            _lastSyncState.value = SyncState.CreatingBackup(startInstant)
            val backupMangas = BackupMangaHandler.backup(backupFlags)
            val backup =
                Backup(
                    backupMangas,
                    BackupCategoryHandler.backup(backupFlags).filter { it.name != Category.DEFAULT_CATEGORY_NAME },
                    BackupSourceHandler.backup(backupMangas, backupFlags),
                    emptyMap(),
                    null,
                )

            val syncData =
                SyncData(
                    backup = backup,
                )

            val remoteBackup =
                SyncYomiSyncService.doSync(syncData, startInstant) {
                    _lastSyncState.value = it
                }

            if (remoteBackup == null) {
                logger.debug { "Skip restore due to network issues" }
                finishWithError(startInstant, "Network error", periodic)
                return
            }

            if (remoteBackup === syncData.backup) {
                // nothing changed
                logger.debug { "Skip restore due to remote was overwrite from local" }
                finishWithSuccess(startInstant, periodic)
                return
            }

            // Stop the sync early if the remote backup is null or empty
            if (remoteBackup.backupManga.isEmpty()) {
                logger.error { "No data found on remote server." }
                finishWithError(startInstant, "No data found on remote server.", periodic)
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
                finishWithSuccess(startInstant, periodic)
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
                finishWithSuccess(startInstant, periodic)
                return
            }

            val backupStream = ProtoBuf.encodeToByteArray(Backup.serializer(), newSyncData).inputStream()
            val restoreId =
                ProtoBackupImport.restore(
                    sourceStream = backupStream,
                    flags = backupFlags,
                    isSync = true,
                )
            _lastSyncState.value = SyncState.Restoring(startInstant, restoreId)

            ProtoBackupImport.notifyFlow.first {
                val restoreState = ProtoBackupImport.getRestoreState(restoreId)

                restoreState == ProtoBackupImport.BackupRestoreState.Success ||
                    restoreState == ProtoBackupImport.BackupRestoreState.Failure
            }

            // update the sync timestamp
            finishWithSuccess(startInstant, periodic)
        } catch (e: Throwable) {
            logger.error { "Error syncing: ${e.message}" }
            finishWithError(startInstant, "${e::class.qualifiedName}: ${e.message}", periodic)
        }
    }

    private fun finishWithSuccess(
        startInstant: Instant,
        periodic: Boolean,
    ) {
        syncPreferences
            .edit()
            .putLong("last_sync_timestamp", Clock.System.now().toEpochMilliseconds())
            .apply()
        _lastSyncState.value = SyncState.Success(startInstant)

        logger.info {
            if (periodic) {
                "Periodic sync completed successfully"
            } else {
                "Manual sync completed successfully"
            }
        }
    }

    private fun finishWithError(
        startInstant: Instant,
        message: String,
        periodic: Boolean,
    ) {
        _lastSyncState.value = SyncState.Error(startInstant, message)

        logger.info {
            if (periodic) {
                "Periodic sync failed: $message"
            } else {
                "Manual sync failed: $message"
            }
        }
    }

    private fun isMangaDifferent(
        localManga: MangaDataClass,
        remoteManga: BackupManga,
    ): Boolean {
        if (localManga.version != remoteManga.version) {
            return true
        }

        val localChapters =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq localManga.id }
                    .map { ChapterTable.toDataClass(it) }
            }

        if (areChaptersDifferent(localChapters, remoteManga.chapters)) {
            return true
        }

        val localCategories =
            transaction {
                CategoryMangaTable
                    .innerJoin(CategoryTable)
                    .selectAll()
                    .where { CategoryMangaTable.manga eq localManga.id }
                    .map { it[CategoryTable.order] }
            }

        return localCategories.toSet() != remoteManga.categories.toSet()
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
                    transaction {
                        MangaTable.update({ MangaTable.id eq localManga.id }) {
                            it[inLibrary] = nonFavorite.favorite
                        }
                    }.apply {
                        handleMangaThumbnail(localManga.id, nonFavorite.favorite)
                    }
                }
            }
        }
    }

    sealed class SyncState(
        open val startDate: Instant,
    ) {
        data class Started(
            override val startDate: Instant,
        ) : SyncState(startDate)

        data class CreatingBackup(
            override val startDate: Instant,
        ) : SyncState(startDate)

        data class Downloading(
            override val startDate: Instant,
        ) : SyncState(startDate)

        data class Merging(
            override val startDate: Instant,
        ) : SyncState(startDate)

        data class Uploading(
            override val startDate: Instant,
        ) : SyncState(startDate)

        data class Restoring(
            override val startDate: Instant,
            val restoreId: String,
        ) : SyncState(startDate)

        data class Success(
            override val startDate: Instant,
            val endDate: Instant = Clock.System.now(),
        ) : SyncState(startDate)

        data class Error(
            override val startDate: Instant,
            val message: String,
            val endDate: Instant = Clock.System.now(),
        ) : SyncState(startDate)
    }
}
