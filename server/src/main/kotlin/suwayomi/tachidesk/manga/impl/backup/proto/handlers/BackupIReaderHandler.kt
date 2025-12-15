package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupIReaderChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupIReaderData
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupIReaderNovel
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupIReaderSourcePreference
import suwayomi.tachidesk.manga.model.table.IReaderChapterTable
import suwayomi.tachidesk.manga.model.table.IReaderNovelTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceMetaTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable

/**
 * Handler for backing up and restoring IReader (novel) data.
 */
object BackupIReaderHandler {
    private val logger = KotlinLogging.logger {}

    /**
     * Create backup of all IReader data.
     */
    fun backup(flags: BackupFlags): BackupIReaderData {
        if (!flags.includeManga) {
            return BackupIReaderData()
        }

        return transaction {
            val novels = backupNovels(flags)
            val sourcePrefs = backupSourcePreferences()

            BackupIReaderData(
                novels = novels,
                sourcePreferences = sourcePrefs,
            )
        }
    }

    private fun backupNovels(flags: BackupFlags): List<BackupIReaderNovel> {
        // Only backup novels in library
        val novels =
            IReaderNovelTable.selectAll()
                .where { IReaderNovelTable.inLibrary eq true }
                .toList()

        return novels.map { novelRow ->
            val novelId = novelRow[IReaderNovelTable.id].value

            val chapters =
                if (flags.includeChapters) {
                    IReaderChapterTable.selectAll()
                        .where { IReaderChapterTable.novel eq novelId }
                        .map { chapterRow ->
                            BackupIReaderChapter(
                                url = chapterRow[IReaderChapterTable.url],
                                name = chapterRow[IReaderChapterTable.name],
                                dateUpload = chapterRow[IReaderChapterTable.dateUpload],
                                chapterNumber = chapterRow[IReaderChapterTable.chapterNumber],
                                scanlator = chapterRow[IReaderChapterTable.scanlator],
                                isRead = chapterRow[IReaderChapterTable.isRead],
                                isBookmarked = chapterRow[IReaderChapterTable.isBookmarked],
                                lastPageRead = chapterRow[IReaderChapterTable.lastPageRead],
                                sourceOrder = chapterRow[IReaderChapterTable.sourceOrder],
                            )
                        }
                } else {
                    emptyList()
                }

            BackupIReaderNovel(
                sourceId = novelRow[IReaderNovelTable.sourceReference],
                url = novelRow[IReaderNovelTable.url],
                title = novelRow[IReaderNovelTable.title],
                artist = novelRow[IReaderNovelTable.artist],
                author = novelRow[IReaderNovelTable.author],
                description = novelRow[IReaderNovelTable.description],
                genre = novelRow[IReaderNovelTable.genre],
                status = novelRow[IReaderNovelTable.status],
                thumbnailUrl = novelRow[IReaderNovelTable.thumbnailUrl],
                inLibrary = novelRow[IReaderNovelTable.inLibrary],
                chapters = chapters,
                categories = emptyList(), // TODO: Add category support for IReader
                meta = emptyMap(),
            )
        }
    }

    private fun backupSourcePreferences(): List<BackupIReaderSourcePreference> {
        return IReaderSourceMetaTable.selectAll().map { row ->
            BackupIReaderSourcePreference(
                sourceId = row[IReaderSourceMetaTable.ref],
                key = row[IReaderSourceMetaTable.key],
                value = row[IReaderSourceMetaTable.value],
            )
        }
    }

    /**
     * Restore IReader data from backup.
     */
    fun restore(
        data: BackupIReaderData,
        flags: BackupFlags,
    ) {
        if (!flags.includeManga) {
            return
        }

        transaction {
            // Restore novels
            data.novels.forEach { backupNovel ->
                restoreNovel(backupNovel, flags)
            }

            // Restore source preferences
            data.sourcePreferences.forEach { pref ->
                restoreSourcePreference(pref)
            }
        }
    }

    private fun restoreNovel(
        backupNovel: BackupIReaderNovel,
        flags: BackupFlags,
    ) {
        // Check if source exists
        val sourceExists =
            IReaderSourceTable.selectAll()
                .where { IReaderSourceTable.id eq backupNovel.sourceId }
                .count() > 0

        if (!sourceExists) {
            logger.warn { "Skipping novel '${backupNovel.title}' - source ${backupNovel.sourceId} not installed" }
            return
        }

        // Find or create novel
        val existingNovel =
            IReaderNovelTable.selectAll()
                .where {
                    (IReaderNovelTable.sourceReference eq backupNovel.sourceId) and
                        (IReaderNovelTable.url eq backupNovel.url)
                }
                .firstOrNull()

        val novelId =
            if (existingNovel != null) {
                // Update existing novel
                IReaderNovelTable.update({
                    (IReaderNovelTable.sourceReference eq backupNovel.sourceId) and
                        (IReaderNovelTable.url eq backupNovel.url)
                }) {
                    it[title] = backupNovel.title
                    it[artist] = backupNovel.artist
                    it[author] = backupNovel.author
                    it[description] = backupNovel.description
                    it[genre] = backupNovel.genre
                    it[status] = backupNovel.status
                    it[thumbnailUrl] = backupNovel.thumbnailUrl
                    it[inLibrary] = backupNovel.inLibrary
                }
                existingNovel[IReaderNovelTable.id].value
            } else {
                // Insert new novel
                IReaderNovelTable.insert {
                    it[sourceReference] = backupNovel.sourceId
                    it[url] = backupNovel.url
                    it[title] = backupNovel.title
                    it[artist] = backupNovel.artist
                    it[author] = backupNovel.author
                    it[description] = backupNovel.description
                    it[genre] = backupNovel.genre
                    it[status] = backupNovel.status
                    it[thumbnailUrl] = backupNovel.thumbnailUrl
                    it[inLibrary] = backupNovel.inLibrary
                }[IReaderNovelTable.id].value
            }

        // Restore chapters
        if (flags.includeChapters && backupNovel.chapters.isNotEmpty()) {
            restoreChapters(novelId, backupNovel.chapters)
        }

        logger.debug { "Restored novel: ${backupNovel.title}" }
    }

    private fun restoreChapters(
        novelId: Int,
        chapters: List<BackupIReaderChapter>,
    ) {
        chapters.forEach { backupChapter ->
            val existingChapter =
                IReaderChapterTable.selectAll()
                    .where {
                        (IReaderChapterTable.novel eq novelId) and
                            (IReaderChapterTable.url eq backupChapter.url)
                    }
                    .firstOrNull()

            if (existingChapter != null) {
                // Update reading progress
                IReaderChapterTable.update({
                    (IReaderChapterTable.novel eq novelId) and
                        (IReaderChapterTable.url eq backupChapter.url)
                }) {
                    it[isRead] = backupChapter.isRead
                    it[isBookmarked] = backupChapter.isBookmarked
                    it[lastPageRead] = backupChapter.lastPageRead
                }
            } else {
                // Insert new chapter
                IReaderChapterTable.insert {
                    it[novel] = novelId
                    it[url] = backupChapter.url
                    it[name] = backupChapter.name
                    it[dateUpload] = backupChapter.dateUpload
                    it[chapterNumber] = backupChapter.chapterNumber
                    it[scanlator] = backupChapter.scanlator
                    it[isRead] = backupChapter.isRead
                    it[isBookmarked] = backupChapter.isBookmarked
                    it[lastPageRead] = backupChapter.lastPageRead
                    it[sourceOrder] = backupChapter.sourceOrder
                }
            }
        }
    }

    private fun restoreSourcePreference(pref: BackupIReaderSourcePreference) {
        val existing =
            IReaderSourceMetaTable.selectAll()
                .where {
                    (IReaderSourceMetaTable.ref eq pref.sourceId) and
                        (IReaderSourceMetaTable.key eq pref.key)
                }
                .firstOrNull()

        if (existing != null) {
            IReaderSourceMetaTable.update({
                (IReaderSourceMetaTable.ref eq pref.sourceId) and
                    (IReaderSourceMetaTable.key eq pref.key)
            }) {
                it[value] = pref.value
            }
        } else {
            IReaderSourceMetaTable.insert {
                it[ref] = pref.sourceId
                it[key] = pref.key
                it[value] = pref.value
            }
        }
    }
}
