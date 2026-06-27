package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Page
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrNull
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrStub
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Text-based (novel) chapter content. The image pipeline ([Page]) does not apply: a novel chapter
 * is a single page whose body is HTML/text returned by [eu.kanade.tachiyomi.source.Source.fetchPageText].
 */
object Novel {
    private val logger = KotlinLogging.logger {}

    const val DOWNLOAD_FILE_NAME = "0.html"

    private val isNovelCache = ConcurrentHashMap<Long, Boolean>()

    fun isNovelSource(sourceId: Long): Boolean {
        isNovelCache[sourceId]?.let { return it }
        val source = getSourceOrNull(sourceId) ?: return false
        return source.isNovelSource.also { isNovelCache[sourceId] = it }
    }

    /** Source ids whose catalogue source serves novels. Used to keep novels out of manga queries. */
    fun novelSourceIds(): Set<Long> {
        val ids = transaction { SourceTable.selectAll().map { it[SourceTable.id].value } }
        return ids.filterTo(mutableSetOf()) { isNovelSource(it) }
    }

    /**
     * Returns the HTML/text body of a novel chapter. Reads the downloaded file when present,
     * otherwise fetches live from the source.
     */
    suspend fun getChapterText(
        mangaId: Int,
        chapterIndex: Int,
    ): String {
        val (chapterId, chapterUrl, isDownloaded, sourceId) =
            transaction {
                val chapterRow =
                    ChapterTable
                        .selectAll()
                        .where { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                        .firstOrNull()
                        ?: throw IllegalArgumentException("Chapter $chapterIndex not found for manga $mangaId")
                val sourceRef =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .firstOrNull()
                        ?.get(MangaTable.sourceReference)
                        ?: throw IllegalArgumentException("MangaId $mangaId not found")
                ChapterTextInfo(
                    chapterId = chapterRow[ChapterTable.id].value,
                    chapterUrl = chapterRow[ChapterTable.url],
                    isDownloaded = chapterRow[ChapterTable.isDownloaded],
                    sourceId = sourceRef,
                )
            }

        if (isDownloaded) {
            val file = File(getChapterDownloadPath(mangaId, chapterId), DOWNLOAD_FILE_NAME)
            if (file.exists()) {
                return file.readText()
            }
            logger.warn { "novel chapter $chapterId marked downloaded but $DOWNLOAD_FILE_NAME missing, fetching live" }
        }

        val source = getSourceOrStub(sourceId)
        return source.fetchPageText(Page(0, chapterUrl))
    }

    /**
     * Fetches and writes the novel chapter body to the download folder. Returns true on success.
     * Marking [ChapterTable.isDownloaded] is handled by the caller (the downloader).
     */
    suspend fun downloadChapterText(
        mangaId: Int,
        chapterId: Int,
    ): Boolean {
        val (chapterUrl, sourceId) =
            transaction {
                val chapterRow =
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.id eq chapterId }
                        .firstOrNull()
                        ?: throw IllegalArgumentException("ChapterId $chapterId not found")
                val sourceRef =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .firstOrNull()
                        ?.get(MangaTable.sourceReference)
                        ?: throw IllegalArgumentException("MangaId $mangaId not found")
                chapterRow[ChapterTable.url] to sourceRef
            }

        val source = getSourceOrStub(sourceId)
        val text = source.fetchPageText(Page(0, chapterUrl))
        if (text.isBlank()) {
            return false
        }

        val folder = File(getChapterDownloadPath(mangaId, chapterId))
        folder.mkdirs()
        File(folder, DOWNLOAD_FILE_NAME).writeText(text)
        return true
    }

    private data class ChapterTextInfo(
        val chapterId: Int,
        val chapterUrl: String,
        val isDownloaded: Boolean,
        val sourceId: Long,
    )
}
