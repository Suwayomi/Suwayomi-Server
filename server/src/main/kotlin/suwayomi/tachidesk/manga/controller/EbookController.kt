package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpStatus
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.ebook.EpubBuilder
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation
import java.io.ByteArrayInputStream

object EbookController {
    /**
     * Streams the chapter as an EPUB file. The chapter must already be
     * downloaded on the server (folder or CBZ both work). The filename
     * is set with Content-Disposition to "{Manga Title} - {Chapter Name}.epub"
     * (override- and scanlator-alias-aware via MangaTable.toDataClass).
     */
    val downloadChapterEpub =
        handler(
            pathParam<Int>("chapterId"),
            documentWith = {
                withOperation {
                    summary("Download chapter as EPUB")
                    description(
                        "Stream a chapter as an image-only EPUB. The chapter must be downloaded on " +
                            "the server. Filename is built from override-aware manga title + chapter name.",
                    )
                }
            },
            behaviorOf = { ctx, chapterId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()

                val info =
                    transaction {
                        val chapterRow =
                            ChapterTable
                                .selectAll()
                                .where { ChapterTable.id eq chapterId }
                                .firstOrNull() ?: return@transaction null
                        val mangaId = chapterRow[ChapterTable.manga].value
                        val mangaRow =
                            MangaTable
                                .selectAll()
                                .where { MangaTable.id eq mangaId }
                                .first()
                        val mangaData = MangaTable.toDataClass(mangaRow)
                        Triple(mangaId, mangaData, chapterRow[ChapterTable.name])
                    }
                if (info == null) {
                    ctx.status(HttpStatus.NOT_FOUND)
                    return@handler
                }
                val (mangaId, mangaData, chapterName) = info

                val pages =
                    runCatching { EpubBuilder.pagesFromChapter(mangaId, chapterId) }
                        .getOrElse {
                            ctx.status(HttpStatus.NOT_FOUND)
                            ctx.result("Chapter has no downloaded pages.")
                            return@handler
                        }

                val rtl = serverConfig.ebookRtl.value
                val epub = EpubBuilder.build(mangaData.title, mangaData.author, pages, rtl)
                val rawName = "${mangaData.title} - $chapterName.epub"
                val filename = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(200)

                ctx.disableCompression()
                ctx.header("Content-Type", "application/epub+zip")
                ctx.header("Content-Disposition", "attachment; filename=\"$filename\"")
                ctx.header("Content-Length", epub.size.toString())
                ctx.result(ByteArrayInputStream(epub))
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
