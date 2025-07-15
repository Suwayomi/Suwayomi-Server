package suwayomi.tachidesk.global.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.ContentType
import io.javalin.http.HttpStatus
import io.javalin.websocket.WsConfig
import suwayomi.tachidesk.global.impl.WebView
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import java.util.Locale

object WebViewController {
    val webview =
        handler(
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("WebView")
                    description("Opens and browses WebView")
                }
            },
            behaviorOf = { ctx, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.contentType(ContentType.TEXT_HTML)
                ctx.render(
                    "Webview.jte",
                    mapOf(
                        "locale" to locale,
                    ),
                )
            },
            withResults = { mime<String>(HttpStatus.OK, "text/html") },
        )

    fun webviewWS(ws: WsConfig) {
        ws.onConnect { ctx -> WebView.addClient(ctx) }
        ws.onMessage { ctx -> WebView.handleRequest(ctx) }
        ws.onClose { ctx -> WebView.removeClient(ctx) }
    }
}
