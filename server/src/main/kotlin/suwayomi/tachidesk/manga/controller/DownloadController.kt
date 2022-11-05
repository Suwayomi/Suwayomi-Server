package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpCode
import io.javalin.websocket.WsConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.DownloadManager.EnqueueInput
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation

object DownloadController {
    private val json by DI.global.instance<Json>()

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
    val start = handler(
        documentWith = {
            withOperation {
                summary("Downloader start")
                description("Start the downloader")
            }
        },
        behaviorOf = { ctx ->
            DownloadManager.start()

            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )

    /** Stop the downloader */
    val stop = handler(
        documentWith = {
            withOperation {
                summary("Downloader stop")
                description("Stop the downloader")
            }
        },
        behaviorOf = { ctx ->
            DownloadManager.stop()

            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )

    /** clear download queue */
    val clear = handler(
        documentWith = {
            withOperation {
                summary("Downloader clear")
                description("Clear download queue")
            }
        },
        behaviorOf = { ctx ->
            DownloadManager.clear()

            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )

    /** Queue single chapter for download */
    val queueChapter = handler(
        pathParam<Int>("chapterIndex"),
        pathParam<Int>("mangaId"),
        documentWith = {
            withOperation {
                summary("Downloader add single chapter")
                description("Queue single chapter for download")
            }
        },
        behaviorOf = { ctx, chapterIndex, mangaId ->
            ctx.future(
                future {
                    DownloadManager.enqueueMultiple(listOf(EnqueueInput(mangaId, chapterIndex)))
                }
            )
        },
        withResults = {
            httpCode(HttpCode.OK)
            httpCode(HttpCode.NOT_FOUND)
        }
    )

    val queueChapters = handler(
        documentWith = {
            withOperation {
                summary("Downloader add multiple chapters")
                description("Queue multiple chapters for download")
            }
        },
        behaviorOf = { ctx ->
            val inputs = json.decodeFromString<List<EnqueueInput>>(ctx.body())
            ctx.future(
                future {
                    DownloadManager.enqueueMultiple(inputs)
                }
            )
            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )

    /** delete chapter from download queue */
    val unqueueChapter = handler(
        pathParam<Int>("chapterIndex"),
        pathParam<Int>("mangaId"),
        documentWith = {
            withOperation {
                summary("Downloader remove chapter")
                description("Delete chapter from download queue")
            }
        },
        behaviorOf = { ctx, chapterIndex, mangaId ->
            DownloadManager.unqueue(chapterIndex, mangaId)

            ctx.status(200)
        },
        withResults = {
            httpCode(HttpCode.OK)
        }
    )
}
