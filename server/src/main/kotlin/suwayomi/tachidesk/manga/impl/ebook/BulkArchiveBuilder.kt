package suwayomi.tachidesk.manga.impl.ebook

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BulkArchiveBuilder {
    private val logger = KotlinLogging.logger {}

    enum class Format { CBZ, EPUB }

    data class Result(
        val bytes: ByteArray,
        val filename: String,
    )

    /**
     * Build a single ZIP archive containing one CBZ or EPUB per chapter id.
     *
     * Each chapter must already be downloaded on the server. The internal
     * filename of every entry mirrors what individual download endpoints
     * use ("{Title} ({Scanlator}) - {Chapter}.cbz/epub" with override and
     * scanlator alias applied through ChapterDownloadHelper / EpubBuilder).
     *
     * The outer ZIP is named after the dominant manga title in the
     * selection (`{Title} - {N} chapters.zip`) — falling back to a generic
     * name if multiple mangas are mixed in the same selection.
     */
    fun build(
        chapterIds: List<Int>,
        format: Format,
    ): Result {
        require(chapterIds.isNotEmpty()) { "No chapter ids provided" }

        // Pull chapter rows + parent manga titles in a single round trip.
        val rows =
            transaction {
                (ChapterTable innerJoin MangaTable)
                    .selectAll()
                    .where { ChapterTable.id inList chapterIds }
                    .map { row ->
                        Triple(
                            row[ChapterTable.id].value,
                            row[ChapterTable.manga].value,
                            MangaTable.toDataClass(row),
                        ) to row[ChapterTable.name]
                    }
            }
        require(rows.isNotEmpty()) { "No chapters matched" }

        val baos = ByteArrayOutputStream(2 * 1024 * 1024)
        ZipOutputStream(baos).use { zip ->
            for (entryPair in rows) {
                val triple = entryPair.first
                val chapterId = triple.first
                val mangaId = triple.second
                val manga = triple.third
                val chapterName = entryPair.second
                val produced =
                    when (format) {
                        Format.CBZ -> bytesAndNameForCbz(mangaId, chapterId)
                        Format.EPUB ->
                            bytesAndNameForEpub(manga.title, manga.author, mangaId, chapterId, chapterName)
                    }
                val data = produced.first ?: continue
                val filename = produced.second
                val zipEntry = ZipEntry(safe(filename))
                zip.putNextEntry(zipEntry)
                zip.write(data)
                zip.closeEntry()
            }
        }

        val titles = rows.map { it.first.third.title }.toSet()
        val outerName =
            when {
                titles.size == 1 -> "${titles.single()} - ${rows.size} chapters.zip"
                else -> "Manga selection - ${rows.size} chapters.zip"
            }
        logger.info {
            "Built bulk archive format=$format chapters=${rows.size} bytes=${baos.size()} name='$outerName'"
        }
        return Result(baos.toByteArray(), safe(outerName))
    }

    private fun bytesAndNameForCbz(
        mangaId: Int,
        chapterId: Int,
    ): Pair<ByteArray?, String> {
        val (stream, filename, _) =
            runCatching { ChapterDownloadHelper.getCbzForDownload(chapterId, markAsRead = null) }
                .onFailure { logger.warn(it) { "CBZ unavailable for chapter $chapterId" } }
                .getOrElse { return null to "chapter-$chapterId.cbz" }
        val data = stream.use { it.readAllBytes() }
        return data to filename
    }

    private fun bytesAndNameForEpub(
        mangaTitle: String,
        mangaAuthor: String?,
        mangaId: Int,
        chapterId: Int,
        chapterName: String,
    ): Pair<ByteArray?, String> {
        val pages =
            runCatching { EpubBuilder.pagesFromChapter(mangaId, chapterId) }
                .onFailure { logger.warn(it) { "EPUB pages unavailable for chapter $chapterId" } }
                .getOrNull() ?: return null to "chapter-$chapterId.epub"
        val rtl = suwayomi.tachidesk.server.serverConfig.ebookRtl.value
        val data = EpubBuilder.build(mangaTitle, mangaAuthor, pages, rtl)
        val filename = "$mangaTitle - $chapterName.epub"
        return data to filename
    }

    private fun safe(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(220).ifBlank { "archive.zip" }
}
