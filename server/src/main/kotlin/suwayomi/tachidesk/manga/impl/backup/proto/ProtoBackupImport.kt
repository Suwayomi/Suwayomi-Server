package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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
import mu.KotlinLogging
import okio.buffer
import okio.gzip
import okio.source
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.toStatus
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga.clearThumbnail
import suwayomi.tachidesk.manga.impl.backup.models.Chapter
import suwayomi.tachidesk.manga.impl.backup.models.Manga
import suwayomi.tachidesk.manga.impl.backup.models.Track
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator.ValidationResult
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator.validate
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupHistory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSerializer
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.io.InputStream
import java.lang.Integer.max
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

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

        data class RestoringCategories(val totalManga: Int) : BackupRestoreState()

        data class RestoringManga(val current: Int, val totalManga: Int, val title: String) : BackupRestoreState()
    }

    private val backupRestoreIdToState = mutableMapOf<String, BackupRestoreState>()

    val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    fun getRestoreState(id: String): BackupRestoreState? {
        return backupRestoreIdToState[id]
    }

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
    ): ValidationResult {
        return backupMutex.withLock {
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
    }

    private fun performRestore(
        id: String,
        sourceStream: InputStream,
    ): ValidationResult {
        val backupString = sourceStream.source().gzip().buffer().use { it.readByteArray() }
        val backup = parser.decodeFromByteArray(BackupSerializer, backupString)

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
                        CategoryTable.select { CategoryTable.name eq it.name }
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
        val history = backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()

        try {
            restoreMangaData(manga, chapters, categories, history, tracks, backupCategories, categoryMapping)
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
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        categoryMapping: Map<Int, Int>,
    ) {
        val dbManga =
            transaction {
                MangaTable.select { (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }
                    .firstOrNull()
            }

        if (dbManga == null) { // Manga not in database
            transaction {
                // insert manga to database
                val mangaId =
                    MangaTable.insertAndGetId {
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

                // delete thumbnail in case cached data still exists
                clearThumbnail(mangaId)

                // insert chapter data
                val chaptersLength = chapters.size
                ChapterTable.batchInsert(chapters) { chapter ->
                    this[ChapterTable.url] = chapter.url
                    this[ChapterTable.name] = chapter.name
                    if (chapter.date_upload == 0L) {
                        this[ChapterTable.date_upload] = chapter.date_fetch
                    } else {
                        this[ChapterTable.date_upload] = chapter.date_upload
                    }
                    this[ChapterTable.chapter_number] = chapter.chapter_number
                    this[ChapterTable.scanlator] = chapter.scanlator

                    this[ChapterTable.sourceOrder] = chaptersLength - chapter.source_order
                    this[ChapterTable.manga] = mangaId

                    this[ChapterTable.isRead] = chapter.read
                    this[ChapterTable.lastPageRead] = chapter.last_page_read
                    this[ChapterTable.isBookmarked] = chapter.bookmark

                    this[ChapterTable.fetchedAt] = TimeUnit.MILLISECONDS.toSeconds(chapter.date_fetch)
                }

                // insert categories
                categories.forEach { backupCategoryOrder ->
                    CategoryManga.addMangaToCategory(mangaId, categoryMapping[backupCategoryOrder]!!)
                }
            }
        } else { // Manga in database
            transaction {
                val mangaId = dbManga[MangaTable.id].value

                // Merge manga data
                MangaTable.update({ MangaTable.id eq mangaId }) {
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

                // merge chapter data
                val chaptersLength = chapters.size
                val dbChapters = ChapterTable.select { ChapterTable.manga eq mangaId }

                chapters.forEach { chapter ->
                    val dbChapter = dbChapters.find { it[ChapterTable.url] == chapter.url }

                    if (dbChapter == null) {
                        ChapterTable.insert {
                            it[url] = chapter.url
                            it[name] = chapter.name
                            if (chapter.date_upload == 0L) {
                                it[date_upload] = chapter.date_fetch
                            } else {
                                it[date_upload] = chapter.date_upload
                            }
                            it[chapter_number] = chapter.chapter_number
                            it[scanlator] = chapter.scanlator

                            it[sourceOrder] = chaptersLength - chapter.source_order
                            it[ChapterTable.manga] = mangaId

                            it[isRead] = chapter.read
                            it[lastPageRead] = chapter.last_page_read
                            it[isBookmarked] = chapter.bookmark
                        }
                    } else {
                        ChapterTable.update({ (ChapterTable.url eq dbChapter[ChapterTable.url]) and (ChapterTable.manga eq mangaId) }) {
                            it[isRead] = chapter.read || dbChapter[isRead]
                            it[lastPageRead] = max(chapter.last_page_read, dbChapter[lastPageRead])
                            it[isBookmarked] = chapter.bookmark || dbChapter[isBookmarked]
                        }
                    }
                }

                // merge categories
                categories.forEach { backupCategoryOrder ->
                    CategoryManga.addMangaToCategory(mangaId, categoryMapping[backupCategoryOrder]!!)
                }
            }
        }

        // TODO: insert/merge history

        // TODO: insert/merge tracking
    }
}
