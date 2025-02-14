package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.buffer
import okio.gzip
import okio.source
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.toStatus
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.backup.models.Chapter
import suwayomi.tachidesk.manga.impl.backup.models.Manga
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator.ValidationResult
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator.validate
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupHistory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupTracking
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrack
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrackRecordDataClass
import suwayomi.tachidesk.manga.model.dataclass.TrackRecordDataClass
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.database.dbTransaction
import java.io.InputStream
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlin.math.max
import suwayomi.tachidesk.manga.impl.track.Track as Tracker

enum class RestoreMode {
    NEW,
    EXISTING,
}

object ProtoBackupImport : ProtoBackupBase() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val logger = KotlinLogging.logger {}

    private var restoreAmount = 0

    private val errors = mutableListOf<Pair<Date, String>>()

    private val backupMutex = Mutex()

    sealed class BackupRestoreState {
        data object Idle : BackupRestoreState()

        data object Success : BackupRestoreState()

        data object Failure : BackupRestoreState()

        data class RestoringCategories(
            val totalManga: Int,
        ) : BackupRestoreState()

        data class RestoringManga(
            val current: Int,
            val totalManga: Int,
            val title: String,
        ) : BackupRestoreState()
    }

    private val backupRestoreIdToState = mutableMapOf<String, BackupRestoreState>()

    val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    fun getRestoreState(id: String): BackupRestoreState? = backupRestoreIdToState[id]

    private fun updateRestoreState(
        id: String,
        state: BackupRestoreState,
    ) {
        backupRestoreIdToState[id] = state

        scope.launch {
            notifyFlow.emit(Unit)
        }
    }

    private fun cleanupRestoreState(id: String) {
        val timer = Timer()
        val delay = 1000L * 60 // 60 seconds

        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    logger.debug { "cleanupRestoreState: $id (${getRestoreState(id)?.toStatus()?.state})" }
                    backupRestoreIdToState.remove(id)
                }
            },
            delay,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun restore(sourceStream: InputStream): String {
        val restoreId = System.currentTimeMillis().toString()

        logger.info { "restore($restoreId): queued" }

        updateRestoreState(restoreId, BackupRestoreState.Idle)

        GlobalScope.launch {
            restoreLegacy(sourceStream, restoreId)
        }

        return restoreId
    }

    suspend fun restoreLegacy(
        sourceStream: InputStream,
        restoreId: String = "legacy",
    ): ValidationResult =
        backupMutex.withLock {
            try {
                logger.info { "restore($restoreId): restoring..." }
                performRestore(restoreId, sourceStream)
            } catch (e: Exception) {
                logger.error(e) { "restore($restoreId): failed due to" }

                updateRestoreState(restoreId, BackupRestoreState.Failure)
                ValidationResult(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            } finally {
                logger.info { "restore($restoreId): finished with state ${getRestoreState(restoreId)?.toStatus()?.state}" }
                cleanupRestoreState(restoreId)
            }
        }

    private fun performRestore(
        id: String,
        sourceStream: InputStream,
    ): ValidationResult {
        val backupString =
            sourceStream
                .source()
                .gzip()
                .buffer()
                .use { it.readByteArray() }
        val backup = parser.decodeFromByteArray(Backup.serializer(), backupString)

        val validationResult = validate(backup)

        restoreAmount = backup.backupManga.size + 1 // +1 for categories

        updateRestoreState(id, BackupRestoreState.RestoringCategories(backup.backupManga.size))
        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        val categoryMapping =
            transaction {
                backup.backupCategories.associate {
                    val dbCategory =
                        CategoryTable
                            .selectAll()
                            .where { CategoryTable.name eq it.name }
                            .firstOrNull()
                    val categoryId =
                        dbCategory?.let { categoryResultRow ->
                            categoryResultRow[CategoryTable.id].value
                        } ?: Category.DEFAULT_CATEGORY_ID
                    it.order to categoryId
                }
            }

        // Store source mapping for error messages
        sourceMapping = backup.getSourceMap()

        // Restore individual manga
        backup.backupManga.forEachIndexed { index, manga ->
            updateRestoreState(
                id,
                BackupRestoreState.RestoringManga(
                    current = index + 1,
                    totalManga = backup.backupManga.size,
                    title = manga.title,
                ),
            )

            restoreManga(
                backupManga = manga,
                backupCategories = backup.backupCategories,
                categoryMapping = categoryMapping,
            )
        }

        logger.info {
            """
            Restore Errors:
            ${errors.joinToString("\n") { "${it.first} - ${it.second}" }}
            Restore Summary:
            - Missing Sources:
                ${validationResult.missingSources.joinToString("\n                    ")}
            - Titles missing Sources:
                ${validationResult.mangasMissingSources.joinToString("\n                    ")}
            - Missing Trackers:
                ${validationResult.missingTrackers.joinToString("\n                    ")}
            """.trimIndent()
        }

        updateRestoreState(id, BackupRestoreState.Success)

        return validationResult
    }

    private fun restoreCategories(backupCategories: List<BackupCategory>) {
        val dbCategories = Category.getCategoryList()

        // Iterate over them and create missing categories
        backupCategories.forEach { category ->
            if (dbCategories.none { it.name == category.name }) {
                Category.createCategory(category.name)
            }
        }
    }

    private fun restoreManga(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
        categoryMapping: Map<Int, Int>,
    ) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories
        val history = backupManga.history

        try {
            restoreMangaData(manga, chapters, categories, history, backupManga.tracking, backupCategories, categoryMapping)
        } catch (e: Exception) {
            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER") // TODO: remove
    private fun restoreMangaData(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        backupCategories: List<BackupCategory>,
        categoryMapping: Map<Int, Int>,
    ) {
        val dbManga =
            transaction {
                MangaTable
                    .selectAll()
                    .where { (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }
                    .firstOrNull()
            }
        val restoreMode = if (dbManga != null) RestoreMode.EXISTING else RestoreMode.NEW

        val mangaId =
            transaction {
                val mangaId =
                    if (dbManga == null) {
                        // insert manga to database
                        MangaTable
                            .insertAndGetId {
                                it[url] = manga.url
                                it[title] = manga.title

                                it[artist] = manga.artist
                                it[author] = manga.author
                                it[description] = manga.description
                                it[genre] = manga.genre
                                it[status] = manga.status
                                it[thumbnail_url] = manga.thumbnail_url
                                it[updateStrategy] = manga.update_strategy.name

                                it[sourceReference] = manga.source

                                it[initialized] = manga.description != null

                                it[inLibrary] = manga.favorite

                                it[inLibraryAt] = TimeUnit.MILLISECONDS.toSeconds(manga.date_added)
                            }.value
                    } else {
                        val dbMangaId = dbManga[MangaTable.id].value

                        // Merge manga data
                        MangaTable.update({ MangaTable.id eq dbMangaId }) {
                            it[artist] = manga.artist ?: dbManga[artist]
                            it[author] = manga.author ?: dbManga[author]
                            it[description] = manga.description ?: dbManga[description]
                            it[genre] = manga.genre ?: dbManga[genre]
                            it[status] = manga.status
                            it[thumbnail_url] = manga.thumbnail_url ?: dbManga[thumbnail_url]
                            it[updateStrategy] = manga.update_strategy.name

                            it[initialized] = dbManga[initialized] || manga.description != null

                            it[inLibrary] = manga.favorite || dbManga[inLibrary]

                            it[inLibraryAt] = TimeUnit.MILLISECONDS.toSeconds(manga.date_added)
                        }

                        dbMangaId
                    }

                // merge chapter data
                restoreMangaChapterData(mangaId, restoreMode, chapters)

                // merge categories
                restoreMangaCategoryData(mangaId, categories, categoryMapping)

                mangaId
            }

        restoreMangaTrackerData(mangaId, tracks)

        // TODO: insert/merge history
    }

    private fun getMangaChapterToRestoreInfo(
        mangaId: Int,
        restoreMode: RestoreMode,
        chapters: List<Chapter>,
    ): Pair<List<Chapter>, List<Pair<Chapter, ResultRow>>> {
        val uniqueChapters = chapters.distinctBy { it.url }

        if (restoreMode == RestoreMode.NEW) {
            return Pair(uniqueChapters, emptyList())
        }

        val dbChaptersByUrl = ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.associateBy { it[ChapterTable.url] }

        val (chaptersToUpdate, chaptersToInsert) = uniqueChapters.partition { dbChaptersByUrl.contains(it.url) }
        val chaptersToUpdateToDbChapter = chaptersToUpdate.map { it to dbChaptersByUrl[it.url]!! }

        return chaptersToInsert to chaptersToUpdateToDbChapter
    }

    private fun restoreMangaChapterData(
        mangaId: Int,
        restoreMode: RestoreMode,
        chapters: List<Chapter>,
    ) = dbTransaction {
        val (chaptersToInsert, chaptersToUpdateToDbChapter) = getMangaChapterToRestoreInfo(mangaId, restoreMode, chapters)

        ChapterTable.batchInsert(chaptersToInsert) { chapter ->
            this[ChapterTable.url] = chapter.url
            this[ChapterTable.name] = chapter.name
            if (chapter.date_upload == 0L) {
                this[ChapterTable.date_upload] = chapter.date_fetch
            } else {
                this[ChapterTable.date_upload] = chapter.date_upload
            }
            this[ChapterTable.chapter_number] = chapter.chapter_number
            this[ChapterTable.scanlator] = chapter.scanlator

            this[ChapterTable.sourceOrder] = chaptersToInsert.size - chapter.source_order
            this[ChapterTable.manga] = mangaId

            this[ChapterTable.isRead] = chapter.read
            this[ChapterTable.lastPageRead] = chapter.last_page_read.coerceAtLeast(0)
            this[ChapterTable.isBookmarked] = chapter.bookmark

            this[ChapterTable.fetchedAt] = TimeUnit.MILLISECONDS.toSeconds(chapter.date_fetch)
        }

        if (chaptersToUpdateToDbChapter.isNotEmpty()) {
            BatchUpdateStatement(ChapterTable).apply {
                chaptersToUpdateToDbChapter.forEach { (backupChapter, dbChapter) ->
                    addBatch(EntityID(dbChapter[ChapterTable.id].value, ChapterTable))
                    this[ChapterTable.isRead] = backupChapter.read || dbChapter[ChapterTable.isRead]
                    this[ChapterTable.lastPageRead] =
                        max(backupChapter.last_page_read, dbChapter[ChapterTable.lastPageRead]).coerceAtLeast(0)
                    this[ChapterTable.isBookmarked] = backupChapter.bookmark || dbChapter[ChapterTable.isBookmarked]
                }
                execute(this@dbTransaction)
            }
        }
    }

    private fun restoreMangaCategoryData(
        mangaId: Int,
        categories: List<Int>,
        categoryMapping: Map<Int, Int>,
    ) {
        categories.forEach { backupCategoryOrder ->
            CategoryManga.addMangaToCategory(mangaId, categoryMapping[backupCategoryOrder]!!)
        }
    }

    private fun restoreMangaTrackerData(
        mangaId: Int,
        tracks: List<BackupTracking>,
    ) {
        val dbTrackRecordsByTrackerId =
            Tracker
                .getTrackRecordsByMangaId(mangaId)
                .mapNotNull { it.record?.toTrack() }
                .associateBy { it.sync_id }

        val (existingTracks, newTracks) =
            tracks
                .mapNotNull { backupTrack ->
                    val track = backupTrack.toTrack(mangaId)

                    val isUnsupportedTracker = TrackerManager.getTracker(track.sync_id) == null
                    if (isUnsupportedTracker) {
                        return@mapNotNull null
                    }

                    val dbTrack =
                        dbTrackRecordsByTrackerId[backupTrack.syncId]
                            ?: // new track
                            return@mapNotNull track

                    if (track.toTrackRecordDataClass().forComparison() == dbTrack.toTrackRecordDataClass().forComparison()) {
                        return@mapNotNull null
                    }

                    dbTrack.also {
                        it.media_id = track.media_id
                        it.library_id = track.library_id
                        it.last_chapter_read = max(dbTrack.last_chapter_read, track.last_chapter_read)
                    }
                }.partition { (it.id ?: -1) > 0 }

        existingTracks.forEach(Tracker::updateTrackRecord)
        newTracks.forEach(Tracker::insertTrackRecord)
    }

    private fun TrackRecordDataClass.forComparison() = this.copy(id = 0, mangaId = 0)
}
