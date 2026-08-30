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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.StartSyncResult
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
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.subscribeTo
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime

@Serializable
data class SyncData(
    val backup: Backup? = null,
)

/**
 * Manages per-user SyncYomi syncing.
 *
 * Each user has their own sync account (host/api key), data flags, interval, sync state, and scheduled task. The
 * per-user config is read from [userSettings] (falling back to the global [serverConfig] value when the user has no
 * override).
 */
object SyncManager {
    private val syncPreferences = Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val logger = KotlinLogging.logger {}

    // Per-user scheduled sync tasks: userId -> (taskId, interval)
    private val scheduledSyncTasks = ConcurrentHashMap<Int, Pair<String, Duration>>()

    // Per-user sync state
    private val syncStates = ConcurrentHashMap<Int, MutableStateFlow<SyncState?>>()

    // Per-user sync mutexes (so concurrent syncs for different users are not serialized against each other)
    private val syncMutexes = ConcurrentHashMap<Int, Mutex>()

    // Users whose sync config has been subscribed to (avoids duplicate subscriptions)
    private val subscribedUsers = ConcurrentHashMap.newKeySet<Int>()

    private val NEW_USER_CHECK_INTERVAL = 60.seconds

    /**
     * The per-user sync state. Returns a [StateFlow] that is `null` until the user has (started) syncing.
     */
    fun lastSyncState(userId: Int): StateFlow<SyncState?> =
        syncStates
            .getOrPut(userId) { MutableStateFlow(null) }
            .asStateFlow()

    private fun setLastSyncState(
        userId: Int,
        state: SyncState?,
    ) {
        syncStates.getOrPut(userId) { MutableStateFlow(null) }.value = state
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleSyncTask() {
        // Subscribe to the sync config of all existing users
        val userIds = transaction { UserAccountTable.selectAll().map { it[UserAccountTable.id].value } }
        userIds.forEach { userId -> subscribeToUserSyncConfig(userId) }

        // Periodically pick up newly created users (and clean up deleted ones)
        HAScheduler.schedule(
            { checkForNewUsers() },
            interval = NEW_USER_CHECK_INTERVAL.inWholeMilliseconds,
            delay = NEW_USER_CHECK_INTERVAL.inWholeMilliseconds,
            name = "sync-new-user-check",
        )
    }

    private fun checkForNewUsers() {
        val userIds = transaction { UserAccountTable.selectAll().map { it[UserAccountTable.id].value }.toSet() }

        userIds.forEach { userId -> subscribeToUserSyncConfig(userId) }

        // Clean up users that no longer exist
        subscribedUsers
            .toList()
            .filter { userId -> userId !in userIds }
            .forEach { userId ->
                scheduledSyncTasks.remove(userId)?.let { HAScheduler.deschedule(it.first) }
            }
    }

    /**
     * Idempotently subscribe to a user's (enabled, interval) config so their scheduled sync task is kept in sync.
     */
    private fun subscribeToUserSyncConfig(userId: Int) {
        if (!subscribedUsers.add(userId)) {
            return
        }

        val enabledFlow = userSettings.flow(userId, userConfig.syncYomiEnabled)
        val intervalFlow = userSettings.flow(userId, userConfig.syncInterval)

        subscribeTo(
            combine(enabledFlow, intervalFlow) { enabled, interval -> Pair(enabled, interval) },
            ignoreInitialValue = false,
        ) { (enabled, interval) ->
            rescheduleUserSync(userId, enabled, interval)
        }
    }

    private fun rescheduleUserSync(
        userId: Int,
        enabled: Boolean,
        interval: Duration,
    ) {
        val shouldSchedule = enabled && interval > 0.seconds
        val existing = scheduledSyncTasks[userId]

        if (shouldSchedule) {
            if (existing == null || existing.second != interval) {
                existing?.let { HAScheduler.deschedule(it.first) }

                val taskId =
                    HAScheduler.schedule(
                        { startSync(userId, periodic = true) },
                        interval = interval.inWholeMilliseconds,
                        delay = interval.inWholeMilliseconds,
                        name = "sync-user-$userId",
                    )
                scheduledSyncTasks[userId] = taskId to interval
            }
        } else {
            existing?.let {
                HAScheduler.deschedule(it.first)
                scheduledSyncTasks.remove(userId)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startSync(
        userId: Int,
        periodic: Boolean = false,
    ): StartSyncResult {
        if (!userSettings.value(userId, userConfig.syncYomiEnabled)) {
            return StartSyncResult.SYNC_DISABLED
        }

        val userMutex = syncMutexes.getOrPut(userId) { Mutex() }
        if (!userMutex.tryLock()) {
            return StartSyncResult.SYNC_IN_PROGRESS
        }

        GlobalScope.launch {
            try {
                syncData(userId, periodic)
            } finally {
                userMutex.unlock()
            }
        }

        return StartSyncResult.SUCCESS
    }

    suspend fun ensureSync(userId: Int) {
        if (!userSettings.value(userId, userConfig.syncYomiEnabled)) {
            return
        }

        val userMutex = syncMutexes.getOrPut(userId) { Mutex() }
        if (userMutex.tryLock()) {
            // there is no ongoing sync for this user, so start one
            try {
                syncData(userId)
            } finally {
                userMutex.unlock()
            }
        } else {
            // wait for the ongoing sync to finish
            userMutex.withLock {}
        }
    }

    private suspend fun syncData(
        userId: Int,
        periodic: Boolean = false,
    ) {
        val startInstant = Clock.System.now()
        setLastSyncState(userId, SyncState.Started(startInstant))

        try {
            logger.info {
                if (periodic) {
                    "Starting periodic sync for user $userId"
                } else {
                    "Starting manual sync for user $userId"
                }
            }

            transaction {
                MangaUserTable.update(
                    {
                        MangaUserTable.isSyncing eq true and (MangaUserTable.user eq userId)
                    },
                ) {
                    it[isSyncing] = false
                }
                ChapterUserTable.update(
                    {
                        ChapterUserTable.isSyncing eq true and (ChapterUserTable.user eq userId)
                    },
                ) {
                    it[isSyncing] = false
                }
                CategoryTable.update(
                    {
                        CategoryTable.isSyncing eq true and (CategoryTable.user eq userId)
                    },
                ) {
                    it[isSyncing] = false
                }
            }

            val backupFlags =
                BackupFlags(
                    includeManga = userSettings.value(userId, userConfig.syncDataManga),
                    includeCategories = userSettings.value(userId, userConfig.syncDataCategories),
                    includeChapters = userSettings.value(userId, userConfig.syncDataChapters),
                    includeTracking = userSettings.value(userId, userConfig.syncDataTracking),
                    includeHistory = userSettings.value(userId, userConfig.syncDataHistory),
                    includeClientData = false,
                    includeServerSettings = false,
                    includeUserSettings = false,
                )

            setLastSyncState(userId, SyncState.CreatingBackup(startInstant))
            val backupMangas = BackupMangaHandler.backup(userId, backupFlags)
            val backup =
                Backup(
                    backupMangas,
                    BackupCategoryHandler.backup(userId, backupFlags).filter { it.name != Category.DEFAULT_CATEGORY_NAME },
                    BackupSourceHandler.backup(userId, backupMangas, backupFlags),
                    emptyMap(),
                    null,
                    null,
                )

            val syncData =
                SyncData(
                    backup = backup,
                )

            val remoteBackup =
                SyncYomiSyncService.doSync(userId, syncData, startInstant) {
                    setLastSyncState(userId, it)
                }

            if (remoteBackup == null) {
                logger.debug { "Skip restore due to network issues" }
                finishWithError(userId, startInstant, "Network error", periodic)
                return
            }

            if (remoteBackup === syncData.backup) {
                // nothing changed
                logger.debug { "Skip restore due to remote was overwrite from local" }
                finishWithSuccess(userId, startInstant, periodic)
                return
            }

            // Stop the sync early if the remote backup is null or empty
            if (remoteBackup.backupManga.isEmpty() && remoteBackup.backupCategories.isEmpty() && remoteBackup.backupSources.isEmpty()) {
                logger.error { "No data found on remote server." }
                finishWithError(userId, startInstant, "No data found on remote server.", periodic)
                return
            }

            val isLibraryEmpty =
                transaction {
                    MangaUserTable
                        .selectAll()
                        .where { MangaUserTable.user eq userId and (MangaUserTable.inLibrary eq true) }
                        .empty()
                }

            // Check if it's first sync based on lastSyncTimestamp
            if (syncPreferences.getLong("last_sync_timestamp_$userId", 0) == 0L && !isLibraryEmpty) {
                // It's first sync no need to restore data. (just update remote data)
                finishWithSuccess(userId, startInstant, periodic)
                return
            }

            val (filteredFavorites, nonFavorites) = filterFavoritesAndNonFavorites(userId, remoteBackup)
            updateNonFavorites(userId, nonFavorites)

            val newSyncData =
                backup.copy(
                    backupManga = filteredFavorites,
                    backupCategories = remoteBackup.backupCategories,
                    backupSources = remoteBackup.backupSources,
                )

            val hasMangaChanges = filteredFavorites.isNotEmpty()
            val hasCategoryChanges = remoteBackup.backupCategories != backup.backupCategories
            val hasSourceChanges = remoteBackup.backupSources != backup.backupSources

            if (!hasMangaChanges && !hasCategoryChanges && !hasSourceChanges) {
                // update the sync timestamp
                finishWithSuccess(userId, startInstant, periodic)
                return
            }

            if (userSettings.value(userId, userConfig.syncDataCategories)) {
                val mergedUids = newSyncData.backupCategories.map { it.uid }.toSet()
                val mergedNames = newSyncData.backupCategories.map { it.name }.toSet()
                val localCategories = Category.getCategoryList(userId).filterNot { it.default } // Exclude system category
                val categoriesToDelete =
                    localCategories.filter {
                        it.uid !in mergedUids && it.name !in mergedNames
                    }
                if (categoriesToDelete.isNotEmpty()) {
                    transaction {
                        categoriesToDelete.forEach {
                            Category.removeCategory(userId, it.id)
                        }
                    }
                }
            }

            val backupStream = ProtoBuf.encodeToByteArray(Backup.serializer(), newSyncData).inputStream()
            val restoreId =
                ProtoBackupImport.restore(
                    userId = userId,
                    sourceStream = backupStream,
                    flags = backupFlags,
                    isSync = true,
                )
            setLastSyncState(userId, SyncState.Restoring(startInstant, restoreId))

            ProtoBackupImport.notifyFlow.first {
                val restoreState = ProtoBackupImport.getRestoreState(restoreId)

                restoreState == ProtoBackupImport.BackupRestoreState.Success ||
                    restoreState == ProtoBackupImport.BackupRestoreState.Failure
            }

            // update the sync timestamp
            finishWithSuccess(userId, startInstant, periodic)
        } catch (e: Throwable) {
            logger.error { "Error syncing: ${e.message}" }
            finishWithError(userId, startInstant, "${e::class.qualifiedName}: ${e.message}", periodic)
        }
    }

    private fun finishWithSuccess(
        userId: Int,
        startInstant: Instant,
        periodic: Boolean,
    ) {
        syncPreferences
            .edit()
            .putLong("last_sync_timestamp_$userId", Clock.System.now().toEpochMilliseconds())
            .apply()
        setLastSyncState(userId, SyncState.Success(startInstant))

        logger.info {
            if (periodic) {
                "Periodic sync for user $userId completed successfully"
            } else {
                "Manual sync for user $userId completed successfully"
            }
        }
    }

    private fun finishWithError(
        userId: Int,
        startInstant: Instant,
        message: String,
        periodic: Boolean,
    ) {
        setLastSyncState(userId, SyncState.Error(startInstant, message))

        logger.info {
            if (periodic) {
                "Periodic sync for user $userId failed: $message"
            } else {
                "Manual sync for user $userId failed: $message"
            }
        }
    }

    private fun isMangaDifferent(
        userId: Int,
        localManga: MangaDataClass,
        remoteManga: BackupManga,
    ): Boolean {
        val localVersion =
            transaction {
                MangaUserTable
                    .select(MangaUserTable.version)
                    .where { (MangaUserTable.user eq userId) and (MangaUserTable.manga eq localManga.id) }
                    .map { it[MangaUserTable.version] }
                    .firstOrNull() ?: 0
            }

        if (localVersion != remoteManga.version) {
            return true
        }

        val localChapterVersions =
            transaction {
                ChapterTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { ChapterTable.manga eq localManga.id }
                    .associate { it[ChapterTable.url] to (it.getOrNull(ChapterUserTable.version) ?: 0) }
            }

        if (areChaptersDifferent(localChapterVersions, remoteManga.chapters)) {
            return true
        }

        val localCategories =
            transaction {
                CategoryMangaTable
                    .innerJoin(
                        CategoryTable,
                        onColumn = { CategoryMangaTable.category },
                        otherColumn = { CategoryTable.id },
                        additionalConstraint = { CategoryTable.user eq userId },
                    ).selectAll()
                    .where { (CategoryMangaTable.user eq userId) and (CategoryMangaTable.manga eq localManga.id) }
                    .map { it[CategoryTable.order] }
            }

        return localCategories.toSet() != remoteManga.categories.toSet()
    }

    private fun areChaptersDifferent(
        localChapterVersions: Map<String, Long>,
        remoteChapters: List<BackupChapter>,
    ): Boolean {
        val remoteChapterMap = remoteChapters.associateBy { it.url }

        if (localChapterVersions.size != remoteChapterMap.size) {
            return true
        }

        for ((url, localVersion) in localChapterVersions) {
            val remoteChapter = remoteChapterMap[url]

            // If a matching remote chapter doesn't exist, or the version numbers are different, consider them different
            if (remoteChapter == null || localVersion != remoteChapter.version) {
                return true
            }
        }

        return false
    }

    private fun filterFavoritesAndNonFavorites(
        userId: Int,
        backup: Backup,
    ): Pair<List<BackupManga>, List<BackupManga>> {
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
                            if (localManga == null || isMangaDifferent(userId, localManga, remoteManga)) {
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

    private fun updateNonFavorites(
        userId: Int,
        nonFavorites: List<BackupManga>,
    ) {
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
                val inLibrary =
                    transaction {
                        MangaUserTable
                            .select(MangaUserTable.id)
                            .where {
                                (MangaUserTable.user eq userId) and
                                    (MangaUserTable.manga eq localManga.id) and
                                    (MangaUserTable.inLibrary eq true)
                            }.any()
                    }
                if (inLibrary != nonFavorite.favorite) {
                    transaction {
                        MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                            it[MangaUserTable.manga] = localManga.id
                            it[MangaUserTable.user] = userId
                            it[MangaUserTable.inLibrary] = nonFavorite.favorite
                            it[MangaUserTable.inLibraryAt] = nonFavorite.dateAdded
                        }
                    }.apply {
                        handleMangaThumbnail(localManga.id)
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
