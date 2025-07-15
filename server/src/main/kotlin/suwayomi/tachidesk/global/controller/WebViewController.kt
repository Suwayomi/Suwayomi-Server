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
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.server.JavalinSetup
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
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.contentType(ContentType.TEXT_HTML)
                ctx.result(
                    JavalinSetup.loadPage(
                        "/webview.html",
                        mapOf(
                            "i18n.title" to MR.strings.webview_label_title.localized(locale),
                            "i18n.url.placeholder" to MR.strings.webview_placeholder_url.localized(locale),
                            "i18n.reverse_scroll" to MR.strings.webview_label_reversescroll.localized(locale),
                            "i18n.bindings_note" to MR.strings.webview_label_bindingshint.localized(locale),
                            "i18n.init" to MR.strings.webview_label_init.localized(locale),
                        ),
                        mapOf(
                            "i18n.disconnected" to MR.strings.webview_label_disconnected.localized(locale),
                            "i18n.get_started" to MR.strings.webview_label_getstarted.localized(locale),
                            "i18n.loading" to MR.strings.webview_label_loading.localized(locale),
                            "i18n.error" to MR.strings.label_error.localized(locale),
                        ),
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
