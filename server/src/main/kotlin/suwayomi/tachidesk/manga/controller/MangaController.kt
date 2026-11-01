package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.Page
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.user.requireUserWithBasicFallback
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.days

object MangaController {
    private val json: Json by injectLazy()

    /** manga thumbnail */
    val thumbnail =
        handler(
            pathParam<Int>("mangaId"),
            queryParam<Boolean?>("opds"),
            documentWith = {
                withOperation {
                    summary("Get a manga thumbnail")
                    description("Get a manga thumbnail from the source or the cache.")
                }
            },
            behaviorOf = { ctx, mangaId, opds ->
                if (opds == true) {
                    ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                } else {
                    ctx.getAttribute(Attribute.TachideskUser).requireUser()
                }
                ctx.future {
                    future { Manga.getMangaThumbnail(mangaId) }
                        .thenApply {
                            ctx.header("content-type", it.second)
                            val httpCacheSeconds = 1.days.inWholeSeconds
                            ctx.header("cache-control", "max-age=$httpCacheSeconds")
                            ctx.result(it.first)
                        }
                }
            },
            withResults = {
                image(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** get page at index "index" */
    val pageRetrieve =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterIndex"),
            pathParam<Int>("index"),
            queryParam<Boolean?>("updateProgress"),
            queryParam<String?>("format"),
            queryParam<Boolean?>("opds"),
            documentWith = {
                withOperation {
                    summary("Get a chapter page")
                    description(
                        "Get a chapter page for a given index. Cache use can be disabled so it only retrieves it directly from the source.",
                    )
                }
            },
            behaviorOf = { ctx, mangaId, chapterIndex, index, updateProgress, format, opds ->
                val userId =
                    if (opds == true) {
                        ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                    } else {
                        ctx.getAttribute(Attribute.TachideskUser).requireUser()
                    }
                ctx.future {
                    future {
                        Page.getPageImageServe(
                            userId = userId,
                            mangaId = mangaId,
                            chapterIndex = chapterIndex,
                            index = index,
                            format = format,
                        )
                    }.thenApply {
                        ctx.header("content-type", it.second)
                        val httpCacheSeconds = 1.days.inWholeSeconds
                        ctx.header("cache-control", "max-age=$httpCacheSeconds")
                        ctx.result(it.first)

                        if (updateProgress == true) {
                            val chapterId = Chapter.updateChapterProgress(userId, mangaId, chapterIndex, pageNo = index)
                            // Sync progress with KoreaderSync if chapter update was successful
                            if (chapterId != -1) {
                                KoreaderSyncService.schedulePushProgress(userId, chapterId)
                            }
                        }
                    }
                }
            },
            withResults = {
                image(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    val downloadChapter =
        handler(
            pathParam<Int>("chapterId"),
            queryParam<Boolean?>("markAsRead"),
            queryParam<Boolean?>("opds"),
            documentWith = {
                withOperation {
                    summary("Download chapter as CBZ")
                    description("Get the CBZ file of the specified chapter, or its metadata via a HEAD request.")
                }
            },
            behaviorOf = { ctx, chapterId, markAsRead, opds ->
                val userId =
                    if (opds == true) {
                        ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                    } else {
                        ctx.getAttribute(Attribute.TachideskUser).requireUser()
                    }
                ctx.disableCompression()
                val contentType = userConfig.opdsCbzMimetype.value(userId).mediaType
                if (ctx.method() == HandlerType.HEAD) {
                    ctx.future {
                        future { ChapterDownloadHelper.getCbzMetadataForDownload(chapterId) }
                            .thenApply { (fileName, fileSize) ->
                                ctx.header("Content-Type", contentType)
                                ctx.header("Content-Disposition", "attachment; filename=\"$fileName\"")
                                ctx.header("Content-Length", fileSize.toString())
                                ctx.status(HttpStatus.OK)
                            }
                    }
                } else {
                    val shouldMarkAsRead = markAsRead ?: false
                    ctx.future {
                        future { ChapterDownloadHelper.getCbzForDownload(userId, chapterId, shouldMarkAsRead) }
                            .thenApply { (inputStream, fileName, fileSize) ->
                                ctx.header("Content-Type", contentType)
                                ctx.header("Content-Disposition", "attachment; filename=\"$fileName\"")
                                ctx.header("Content-Length", fileSize.toString())
                                ctx.result(inputStream)
                            }
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
