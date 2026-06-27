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
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.io.File

/**
 * Text-based (novel) chapter content. The image pipeline ([Page]) does not apply: a novel chapter
 * is a single page whose body is HTML/text returned by [eu.kanade.tachiyomi.source.Source.fetchPageText].
 */
object Novel {
    private val logger = KotlinLogging.logger {}

    const val DOWNLOAD_FILE_NAME = "0.html"

    fun isNovelSource(sourceId: Long): Boolean = getCatalogueSourceOrStub(sourceId).isNovelSource

    /** Source ids whose catalogue source serves novels. Used to keep novels out of manga queries. */
    fun novelSourceIds(): Set<Long> {
        val ids = transaction { SourceTable.selectAll().map { it[SourceTable.id].value } }
        return ids.filterTo(mutableSetOf()) { isNovelSource(it) }
    }

    /**
     * Minimal self-contained HTML reader page for a novel chapter (no WebUI build required).
     * Renders the chapter body with prev/next navigation and basic font controls.
     */
    suspend fun getChapterReaderHtml(
        mangaId: Int,
        chapterIndex: Int,
    ): String {
        val body = getChapterText(mangaId, chapterIndex)

        val (mangaTitle, chapterName, minIdx, maxIdx) =
            transaction {
                val title = MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()[MangaTable.title]
                val chapters =
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.manga eq mangaId }
                        .toList()
                val name =
                    chapters.firstOrNull { it[ChapterTable.sourceOrder] == chapterIndex }?.get(ChapterTable.name)
                        ?: "Chapter"
                val orders = chapters.map { it[ChapterTable.sourceOrder] }
                ReaderMeta(title, name, orders.minOrNull() ?: chapterIndex, orders.maxOrNull() ?: chapterIndex)
            }

        // Lower sourceOrder = newer; reading forward goes toward lower order numbers.
        val prevIdx = (chapterIndex + 1).takeIf { it <= maxIdx }
        val nextIdx = (chapterIndex - 1).takeIf { it >= minIdx }

        fun navLink(
            idx: Int?,
            label: String,
        ): String =
            if (idx == null) {
                "<span class=\"nav disabled\">$label</span>"
            } else {
                "<a class=\"nav\" href=\"/api/v1/manga/$mangaId/chapter/$idx/read\">$label</a>"
            }

        val titleEsc = htmlEscape(mangaTitle)
        val chapterEsc = htmlEscape(chapterName)

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>$titleEsc · $chapterEsc</title>
              <style>
                :root { --fs: 19px; }
                body { margin: 0; background: #15171a; color: #d7dce0; font-family: Georgia, serif; }
                header { position: sticky; top: 0; background: #1d2025; padding: 10px 16px;
                         display: flex; gap: 12px; align-items: center; border-bottom: 1px solid #2c3036; }
                header .t { font-family: system-ui, sans-serif; font-size: 14px; color: #9aa3ad; }
                .nav { font-family: system-ui, sans-serif; font-size: 14px; color: #6cb6ff; text-decoration: none;
                       padding: 6px 10px; border: 1px solid #2c3036; border-radius: 6px; }
                .nav.disabled { color: #4a5159; border-color: #23272c; }
                button { font-family: system-ui, sans-serif; background: #2c3036; color: #d7dce0;
                         border: none; border-radius: 6px; padding: 6px 10px; cursor: pointer; }
                article { max-width: 760px; margin: 0 auto; padding: 28px 20px 120px;
                          font-size: var(--fs); line-height: 1.75; }
                article p { margin: 0 0 1.1em; }
                footer { position: fixed; bottom: 0; left: 0; right: 0; background: #1d2025;
                         border-top: 1px solid #2c3036; padding: 10px 16px; display: flex;
                         gap: 12px; justify-content: center; }
              </style>
            </head>
            <body>
              <header>
                <span class="t">$titleEsc · $chapterEsc</span>
                <span style="flex:1"></span>
                <button onclick="adj(-1)">A-</button>
                <button onclick="adj(1)">A+</button>
              </header>
              <article id="content">$body</article>
              <footer>
                ${navLink(prevIdx, "◀ Prev")}
                ${navLink(nextIdx, "Next ▶")}
              </footer>
              <script>
                function adj(d){var r=document.documentElement;var fs=parseFloat(getComputedStyle(r).getPropertyValue('--fs'));r.style.setProperty('--fs',(fs+d*2)+'px');}
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun htmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val scriptRegex = Regex("<script\\b[^<]*(?:(?!</script>)<[^<]*)*</script>", RegexOption.IGNORE_CASE)
    private val eventHandlerRegex = Regex("\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE)
    private val jsUrlRegex = Regex("(href|src)\\s*=\\s*([\"']?)\\s*javascript:[^\"'>]*\\2", RegexOption.IGNORE_CASE)

    private fun sanitize(html: String): String {
        var out = scriptRegex.replace(html, "")
        out = eventHandlerRegex.replace(out, "")
        out = jsUrlRegex.replace(out, "")
        return out
    }

    private data class ReaderMeta(
        val mangaTitle: String,
        val chapterName: String,
        val minIdx: Int,
        val maxIdx: Int,
    )

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
                        .first()
                val sourceRef =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .first()[MangaTable.sourceReference]
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
                return sanitize(file.readText())
            }
            logger.warn { "novel chapter $chapterId marked downloaded but $DOWNLOAD_FILE_NAME missing, fetching live" }
        }

        val source = getCatalogueSourceOrStub(sourceId)
        return sanitize(source.fetchPageText(Page(0, chapterUrl)))
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
                        .first()
                val sourceRef =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .first()[MangaTable.sourceReference]
                chapterRow[ChapterTable.url] to sourceRef
            }

        val source = getCatalogueSourceOrStub(sourceId)
        val text = sanitize(source.fetchPageText(Page(0, chapterUrl)))
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
