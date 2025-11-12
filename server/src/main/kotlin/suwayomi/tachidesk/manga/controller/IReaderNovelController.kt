package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpStatus
import ireader.core.source.model.MangaInfo
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation

object IReaderNovelController {
    
    val popular =
        handler(
            pathParam<Long>("sourceId"),
            pathParam<Int>("page"),
            documentWith = {
                withOperation {
                    summary("Get popular novels from source")
                    description("Get a list of popular novels from an IReader source")
                }
            },
            behaviorOf = { ctx, sourceId, page ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val result = IReaderNovel.getPopularNovels(sourceId, page)
                ctx.json(result)
            },
            withResults = {
                json<Any>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
    
    val latest =
        handler(
            pathParam<Long>("sourceId"),
            pathParam<Int>("page"),
            documentWith = {
                withOperation {
                    summary("Get latest novels from source")
                    description("Get a list of latest novels from an IReader source")
                }
            },
            behaviorOf = { ctx, sourceId, page ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val result = IReaderNovel.getLatestNovels(sourceId, page)
                ctx.json(result)
            },
            withResults = {
                json<Any>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
    
    val search =
        handler(
            pathParam<Long>("sourceId"),
            queryParam<String>("query"),
            documentWith = {
                withOperation {
                    summary("Search novels")
                    description("Search for novels in an IReader source")
                }
            },
            behaviorOf = { ctx, sourceId, query ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
                val result = IReaderNovel.searchNovels(sourceId, query, page)
                ctx.json(result)
            },
            withResults = {
                json<Any>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
    
    val details =
        handler(
            pathParam<Long>("sourceId"),
            queryParam<String>("novelUrl"),
            documentWith = {
                withOperation {
                    summary("Get novel details")
                    description("Get detailed information about a novel")
                }
            },
            behaviorOf = { ctx, sourceId, novelUrl ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val result = IReaderNovel.getNovelDetails(sourceId, novelUrl)
                ctx.json(result)
            },
            withResults = {
                json<MangaInfo>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
    
    val chapters =
        handler(
            pathParam<Long>("sourceId"),
            queryParam<String>("novelUrl"),
            documentWith = {
                withOperation {
                    summary("Get novel chapters")
                    description("Get the list of chapters for a novel")
                }
            },
            behaviorOf = { ctx, sourceId, novelUrl ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val result = IReaderNovel.getChapterList(sourceId, novelUrl)
                ctx.json(result)
            },
            withResults = {
                json<Any>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
    
    val chapterContent =
        handler(
            pathParam<Long>("sourceId"),
            queryParam<String>("chapterUrl"),
            documentWith = {
                withOperation {
                    summary("Get chapter content")
                    description("Get the content/pages of a chapter")
                }
            },
            behaviorOf = { ctx, sourceId, chapterUrl ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val result = IReaderNovel.getChapterContent(sourceId, chapterUrl)
                ctx.json(result)
            },
            withResults = {
                json<Any>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
