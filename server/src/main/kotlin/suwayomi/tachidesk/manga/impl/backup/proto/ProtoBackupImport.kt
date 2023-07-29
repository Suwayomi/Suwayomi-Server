package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import okio.buffer
import okio.gzip
import okio.source
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
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
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import java.io.InputStream
import java.lang.Integer.max
import java.util.Date
import java.util.concurrent.TimeUnit

object ProtoBackupImport : ProtoBackupBase() {
    private val logger = KotlinLogging.logger {}

    private var restoreAmount = 0

    private val errors = mutableListOf<Pair<Date, String>>()

    private val backupMutex = Mutex()
    sealed class BackupRestoreState {
        object Idle : BackupRestoreState()
        data class RestoringCategories(val totalManga: Int) : BackupRestoreState()
        data class RestoringManga(val current: Int, val totalManga: Int, val title: String) : BackupRestoreState()
    }

    val backupRestoreState = MutableStateFlow<BackupRestoreState>(BackupRestoreState.Idle)

    suspend fun performRestore(userId: Int, sourceStream: InputStream): ValidationResult {
        return backupMutex.withLock {
            val backupString = sourceStream.source().gzip().buffer().use { it.readByteArray() }
            val backup = parser.decodeFromByteArray(BackupSerializer, backupString)

            val validationResult = validate(backup)

            restoreAmount = backup.backupManga.size + 1 // +1 for categories

            backupRestoreState.value = BackupRestoreState.RestoringCategories(backup.backupManga.size)
            // Restore categories
            if (backup.backupCategories.isNotEmpty()) {
                restoreCategories(userId, backup.backupCategories)
            }

            val categoryMapping = transaction {
                backup.backupCategories.associate {
                    it.order to CategoryTable.select { CategoryTable.user eq userId and (CategoryTable.name eq it.name) }
                        .first()[CategoryTable.id].value
                }
            }

            // Store source mapping for error messages
            sourceMapping = backup.getSourceMap()

            // Restore individual manga
            backup.backupManga.forEachIndexed { index, manga ->
                backupRestoreState.value = BackupRestoreState.RestoringManga(
                    current = index + 1,
                    totalManga = backup.backupManga.size,
                    title = manga.title
                )
                restoreManga(
                    userId = userId,
                    backupManga = manga,
                    backupCategories = backup.backupCategories,
                    categoryMapping = categoryMapping
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
            backupRestoreState.value = BackupRestoreState.Idle

            validationResult
        }
    }

    private fun restoreCategories(userId: Int, backupCategories: List<BackupCategory>) {
        val dbCategories = Category.getCategoryList(userId)

        // Iterate over them and create missing categories
        backupCategories.forEach { category ->
            if (dbCategories.none { it.name == category.name }) {
                Category.createCategory(userId, category.name)
            }
        }
    }

    private fun restoreManga(
        userId: Int,
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
        categoryMapping: Map<Int, Int>
    ) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories
        val history = backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()

        try {
            restoreMangaData(userId, manga, chapters, categories, history, tracks, backupCategories, categoryMapping)
        } catch (e: Exception) {
            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }
    }

    @Suppress("UNUSED_PARAMETER") // TODO: remove
    private fun restoreMangaData(
        userId: Int,
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        categoryMapping: Map<Int, Int>
    ) {
        val dbManga = transaction {
            MangaTable.select { (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }
                .firstOrNull()
        }

        if (dbManga == null) { // Manga not in database
            transaction {
                // insert manga to database
                val mangaId = MangaTable.insertAndGetId {
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
                }.value

                MangaUserTable.insert {
                    it[MangaUserTable.manga] = mangaId
                    it[MangaUserTable.user] = userId
                    it[MangaUserTable.inLibrary] = manga.favorite
                    it[MangaUserTable.inLibraryAt] = TimeUnit.MILLISECONDS.toSeconds(manga.date_added)
                }

                // insert chapter data
                val chaptersLength = chapters.size
                chapters.forEach { chapter ->
                    val chapterId = ChapterTable.insertAndGetId {
                        it[url] = chapter.url
                        it[name] = chapter.name
                        it[date_upload] = chapter.date_upload
                        it[chapter_number] = chapter.chapter_number
                        it[scanlator] = chapter.scanlator

                        it[sourceOrder] = chaptersLength - chapter.source_order
                        it[ChapterTable.manga] = mangaId

                        it[fetchedAt] = TimeUnit.MILLISECONDS.toSeconds(chapter.date_fetch)
                    }.value

                    ChapterUserTable.insert {
                        it[ChapterUserTable.chapter] = chapterId
                        it[ChapterUserTable.user] = userId
                        it[ChapterUserTable.isRead] = chapter.read
                        it[ChapterUserTable.lastPageRead] = chapter.last_page_read
                        it[ChapterUserTable.isBookmarked] = chapter.bookmark
                    }
                }

                // insert categories
                categories.forEach { backupCategoryOrder ->
                    CategoryManga.addMangaToCategory(userId, mangaId, categoryMapping[backupCategoryOrder]!!)
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
                }

                val mangaUserData = MangaUserTable.select {
                    MangaUserTable.user eq userId and (MangaUserTable.manga eq mangaId)
                }.firstOrNull()
                if (mangaUserData != null) {
                    MangaUserTable.update({ MangaUserTable.id eq mangaUserData[ChapterUserTable.id] }) {
                        it[MangaUserTable.inLibrary] = manga.favorite || mangaUserData[MangaUserTable.inLibrary]
                        it[MangaUserTable.inLibraryAt] = TimeUnit.MILLISECONDS.toSeconds(manga.date_added)
                    }
                } else {
                    MangaUserTable.insert {
                        it[MangaUserTable.manga] = mangaId
                        it[MangaUserTable.user] = userId
                        it[MangaUserTable.inLibrary] = manga.favorite
                        it[MangaUserTable.inLibraryAt] = TimeUnit.MILLISECONDS.toSeconds(manga.date_added)
                    }
                }

                // merge chapter data
                val chaptersLength = chapters.size
                val dbChapters = ChapterTable.select { ChapterTable.manga eq mangaId }

                chapters.forEach { chapter ->
                    val dbChapter = dbChapters.find { it[ChapterTable.url] == chapter.url }

                    if (dbChapter == null) {
                        val chapterId = ChapterTable.insertAndGetId {
                            it[url] = chapter.url
                            it[name] = chapter.name
                            it[date_upload] = chapter.date_upload
                            it[chapter_number] = chapter.chapter_number
                            it[scanlator] = chapter.scanlator

                            it[sourceOrder] = chaptersLength - chapter.source_order
                            it[ChapterTable.manga] = mangaId
                        }.value

                        ChapterUserTable.insert {
                            it[ChapterUserTable.chapter] = mangaId
                            it[ChapterUserTable.user] = userId
                            it[ChapterUserTable.isRead] = chapter.read
                            it[ChapterUserTable.lastPageRead] = chapter.last_page_read
                            it[ChapterUserTable.isBookmarked] = chapter.bookmark
                        }
                    } else {
                        val chapterId = dbChapter[ChapterTable.id].value
                        val chapterUserData = ChapterUserTable.select {
                            MangaUserTable.user eq userId and (ChapterUserTable.chapter eq chapterId)
                        }.firstOrNull()
                        if (chapterUserData != null) {
                            ChapterUserTable.update({ ChapterUserTable.id eq chapterUserData[ChapterUserTable.id] }) {
                                it[isRead] = chapter.read || chapterUserData[isRead]
                                it[lastPageRead] = max(chapter.last_page_read, chapterUserData[lastPageRead])
                                it[isBookmarked] = chapter.bookmark || chapterUserData[isBookmarked]
                            }
                        } else {
                            ChapterUserTable.insert {
                                it[ChapterUserTable.chapter] = chapterId
                                it[MangaUserTable.user] = userId
                                it[isRead] = chapter.read
                                it[lastPageRead] = chapter.last_page_read
                                it[isBookmarked] = chapter.bookmark
                            }
                        }
                    }
                }

                // merge categories
                categories.forEach { backupCategoryOrder ->
                    CategoryManga.addMangaToCategory(userId, mangaId, categoryMapping[backupCategoryOrder]!!)
                }
            }
        }

        // TODO: insert/merge history

        // TODO: insert/merge tracking
    }
}
