package suwayomi.tachidesk.global.impl.sync

import android.app.Application
import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import kotlin.system.measureTimeMillis

@Serializable
data class SyncData(
    val backup: Backup? = null,
)

object SyncManager {
    private val syncPreferences = Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val logger = KotlinLogging.logger {}

    suspend fun syncData() {
        transaction {
            MangaTable.update({ MangaTable.isSyncing eq true }) {
                it[isSyncing] = false
            }
            ChapterTable.update({ ChapterTable.isSyncing eq true }) {
                it[isSyncing] = false
            }
        }

        val databaseManga = getAllMangaThatNeedsSync()

        val backupFlags = BackupFlags(
            includeManga = true,
            includeCategories = true,
            includeChapters = true,
            includeTracking = true,
            includeHistory = true,
            includeClientData = false,
            includeServerSettings = false,
        )

        val backupMangas = BackupMangaHandler.backup(backupFlags)

        val backup = Backup(
            BackupMangaHandler.backup(backupFlags),
            BackupCategoryHandler.backup(backupFlags),
            BackupSourceHandler.backup(backupMangas, backupFlags),
            emptyMap(),
            null,
        )

        val syncData = SyncData(
            backup = backup,
        )

        val remoteBackup = SyncYomiSyncService().doSync(syncData)

        if (remoteBackup == null) {
            logger.debug { "Skip restore due to network issues" }
            // should we call showSyncError?
            return
        }

        if (remoteBackup === syncData.backup) {
            // nothing changed
            logger.debug { "Skip restore due to remote was overwrite from local" }
            syncPreferences.edit()
                .putLong("last_sync_timestamp", Date().time)
                .apply()
            return
        }

        // Stop the sync early if the remote backup is null or empty
        if (remoteBackup.backupManga.isEmpty()) {
            return
        }

        // Check if it's first sync based on lastSyncTimestamp
        if (syncPreferences.getLong("last_sync_timestamp", 0) == 0L && databaseManga.isNotEmpty()) {
            // It's first sync no need to restore data. (just update remote data)
            syncPreferences.edit()
                .putLong("last_sync_timestamp", Date().time)
                .apply()
            return
        }

        val (filteredFavorites, nonFavorites) = filterFavoritesAndNonFavorites(remoteBackup)
        updateNonFavorites(nonFavorites)

        val newSyncData = backup.copy(
            backupManga = filteredFavorites,
            backupCategories = remoteBackup.backupCategories,
            backupSources = remoteBackup.backupSources,
        )

        // It's local sync no need to restore data. (just update remote data)
        if (filteredFavorites.isEmpty()) {
            // update the sync timestamp
            syncPreferences.edit()
                .putLong("last_sync_timestamp", Date().time)
                .apply()
            return
        }

        val backupStream = ProtoBuf.encodeToByteArray(Backup.serializer(), newSyncData).inputStream()
        ProtoBackupImport.restore(
            sourceStream = backupStream,
            flags = BackupFlags(
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
        syncPreferences.edit()
            .putLong("last_sync_timestamp", Date().time)
            .apply()
    }

    private fun getAllMangaFromDB(): List<MangaDataClass> {
        return transaction { MangaTable.selectAll().map { MangaTable.toDataClass(it) } }
    }

    private fun getAllMangaThatNeedsSync(): List<MangaDataClass> {
        return transaction {
            MangaTable.selectAll().where { MangaTable.inLibrary eq true }.map { MangaTable.toDataClass(it) }
        }
    }

    private fun isMangaDifferent(localManga: MangaDataClass, remoteManga: BackupManga): Boolean {
        val localChapters = transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq localManga.id }
                .map { ChapterTable.toDataClass(it) }
        }
        val localCategories = transaction {
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

        val elapsedTimeMillis = measureTimeMillis {
            val databaseManga = getAllMangaFromDB()
            val localMangaMap = databaseManga.associateBy {
                Triple(it.sourceId.toLong(), it.url, it.title)
            }

            logger.debug { "Starting to filter favorites and non-favorites from backup data." }

            backup.backupManga.forEach { remoteManga ->
                val compositeKey = Triple(remoteManga.source, remoteManga.url, remoteManga.title)
                val localManga = localMangaMap[compositeKey]
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

        val minutes = elapsedTimeMillis / 60000
        val seconds = (elapsedTimeMillis % 60000) / 1000
        logger.debug { "Filtering completed in ${minutes}m ${seconds}s. Favorites found: ${favorites.size}, Non-favorites found: ${nonFavorites.size}" }

        return Pair(favorites, nonFavorites)
    }

    private fun updateNonFavorites(nonFavorites: List<BackupManga>) {
        val localMangaList = getAllMangaFromDB()

        val localMangaMap = localMangaList.associateBy { Triple(it.sourceId.toLong(), it.url, it.title) }

        nonFavorites.forEach { nonFavorite ->
            val key = Triple(nonFavorite.source, nonFavorite.url, nonFavorite.title)
            localMangaMap[key]?.let { localManga ->
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

                it[MangaTable.sourceReference] = manga.sourceId.toLongOrNull() ?: 0L

                it[MangaTable.realUrl] = manga.realUrl
                it[MangaTable.lastFetchedAt] = manga.lastFetchedAt ?: 0L
                it[MangaTable.chaptersLastFetchedAt] = manga.chaptersLastFetchedAt ?: 0L

                it[MangaTable.updateStrategy] = manga.updateStrategy.name

                it[MangaTable.version] = manga.version
            }
        }
    }
}
