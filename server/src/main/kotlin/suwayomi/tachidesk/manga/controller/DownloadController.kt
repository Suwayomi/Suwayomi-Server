package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpStatus
import io.javalin.websocket.WsConfig
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.requirePermissions
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation
import uy.kohesive.injekt.injectLazy

object DownloadController {
    private val json: Json by injectLazy()

    /** Download queue stats */
    fun downloadsWS(ws: WsConfig) {
        ws.onConnect { ctx ->
            ctx.getAttribute(Attribute.TachideskUser).requireUser()
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
    val start =
        handler(
            documentWith = {
                withOperation {
                    summary("Downloader start")
                    description("Start the downloader")
                }
            },
            behaviorOf = { ctx ->
                val user = ctx.getAttribute(Attribute.TachideskUser)
                user.requireUser()
                user.requirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
                DownloadManager.start()
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.FORBIDDEN)
            },
        )

    /** Stop the downloader */
    val stop =
        handler(
            documentWith = {
                withOperation {
                    summary("Downloader stop")
                    description("Stop the downloader")
                }
            },
            behaviorOf = { ctx ->
                val user = ctx.getAttribute(Attribute.TachideskUser)
                user.requireUser()
                user.requirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
                ctx.future {
                    future { DownloadManager.stop() }
                        .thenApply { ctx.status(HttpStatus.OK) }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.FORBIDDEN)
            },
        )

    /** clear download queue */
    val clear =
        handler(
            documentWith = {
                withOperation {
                    summary("Downloader clear")
                    description("Clear download queue")
                }
            },
            behaviorOf = { ctx ->
                val user = ctx.getAttribute(Attribute.TachideskUser)
                user.requireUser()
                user.requirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
                ctx.future {
                    future { DownloadManager.clear() }
                        .thenApply { ctx.status(HttpStatus.OK) }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.FORBIDDEN)
            },
        )

    /** clear download queue */
    val reorderChapter =
        handler(
            pathParam<Int>("chapterIndex"),
            pathParam<Int>("mangaId"),
            pathParam<Int>("to"),
            documentWith = {
                withOperation {
                    summary("Downloader reorder chapter")
                    description("Reorder chapter in download queue")
                }
            },
            behaviorOf = { ctx, chapterIndex, mangaId, to ->
                val user = ctx.getAttribute(Attribute.TachideskUser)
                user.requireUser()
                user.requirePermissions(UserPermission.DOWNLOAD_CHAPTERS)
                DownloadManager.reorder(chapterIndex, mangaId, to)
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.FORBIDDEN)
            },
        )
}
