package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import io.javalin.websocket.WsConfig
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.model.dataclass.MangaIdChapterIdDataClass


object DownloadController {
    /** Download queue stats */
    fun downloadsWS(ws: WsConfig) {
        ws.onConnect { ctx ->
            DownloadManager.addClient(ctx)
            DownloadManager.notifyClient(ctx)
        }
        ws.onMessage { ctx ->
            DownloadManager.handleRequest(ctx)
        }
        ws.onClose { ctx ->
            DownloadManager.removeClient(ctx)
        }
    }

    /** Start the downloader */
    fun start(ctx: Context) {
        DownloadManager.start()

        ctx.status(200)
    }

    /** Stop the downloader */
    fun stop(ctx: Context) {
        DownloadManager.stop()

        ctx.status(200)
    }

    /** clear download queue */
    fun clear(ctx: Context) {
        DownloadManager.clear()

        ctx.status(200)
    }

    /** Queue chapter for download */
    fun queueChapter(ctx: Context) {
        val chapterIndex = ctx.pathParam("chapterIndex").toInt()
        val mangaId = ctx.pathParam("mangaId").toInt()

        DownloadManager.enqueue(chapterIndex, mangaId)

        ctx.status(200)
    }

    /** delete chapter from download queue */
    fun unqueueChapter(ctx: Context) {
        val chapterIndex = ctx.pathParam("chapterIndex").toInt()
        val mangaId = ctx.pathParam("mangaId").toInt()

        DownloadManager.unqueue(chapterIndex, mangaId)

        ctx.status(200)
    }

    private val classOfMangaIdChapterIdDataClassList = emptyList<MangaIdChapterIdDataClass>()::class.java
    /** Bulk queue chapters for download */
    fun bulkQueueChapter(ctx: Context) {
        val mangaChapters = ctx.bodyAsClass(classOfMangaIdChapterIdDataClassList)

        DownloadManager.bulkEnqueue(mangaChapters)

        ctx.status(200)
    }
}
