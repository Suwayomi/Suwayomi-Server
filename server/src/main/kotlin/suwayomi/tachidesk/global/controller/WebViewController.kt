package suwayomi.tachidesk.global.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.ContentType
import io.javalin.http.HttpStatus
import io.javalin.http.RedirectResponse
import io.javalin.websocket.WsConfig
import org.eclipse.jetty.websocket.core.CloseStatus
import suwayomi.tachidesk.global.impl.WebView
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UnauthorizedException
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.requirePermissions
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.ServerSubpath
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import java.net.URLEncoder
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
                // intentionally not user-protected, this pages handles login by itself in UI_LOGIN mode
                // for SIMPLE_LOGIN, we need to manually redirect to make this work
                // for BASIC_AUTH, JavalinSetup already handles this
                if (serverConfig.authMode.value == AuthMode.SIMPLE_LOGIN) {
                    try {
                        ctx.getAttribute(Attribute.TachideskUser).requireUser()
                    } catch (_: UnauthorizedException) {
                        val loginPath = ServerSubpath.maybeAddAsPrefix("/login.html")
                        val url =
                            "$loginPath?redirect=" +
                                URLEncoder.encode(ctx.path() + (ctx.queryString()?.let { "?" + it } ?: ""), Charsets.UTF_8)
                        ctx.header("Location", url)
                        throw RedirectResponse(HttpStatus.SEE_OTHER)
                    }
                }
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.contentType(ContentType.TEXT_HTML)
                ctx.render(
                    "Webview.jte",
                    mapOf(
                        "locale" to locale,
                        // "/" when no webUI subpath is configured, else "/<subpath>"
                        "subpath" to ServerSubpath.normalized(),
                    ),
                )
            },
            withResults = { mime<String>(HttpStatus.OK, "text/html") },
        )

    fun webviewWS(ws: WsConfig) {
        ws.onConnect { ctx ->
            val user = ctx.getAttribute(Attribute.TachideskUser)
            try {
                user.requireUser()
                // same permission as the GraphQL WebviewMutation, see RequirePermissions directive
                user.requirePermissions(UserPermission.MANAGE_CACHE)
            } catch (_: UnauthorizedException) {
                // Keep the 1011/"Unauthorized" contract that Webview.kte relies on to
                // show its in-page login dialog (same as Javalin's WsExceptionMapper
                // produced for the previously uncaught UnauthorizedException).
                ctx.closeSession(CloseStatus(1011, "Unauthorized"))
                return@onConnect
            } catch (e: ForbiddenException) {
                // Authenticated but lacking MANAGE_CACHE: keep 1011 (the client only
                // special-cases reason "Unauthorized"); the reason distinguishes it.
                ctx.closeSession(CloseStatus(1011, e.message ?: "Forbidden"))
                return@onConnect
            }
            WebView.addClient(ctx)
        }
        ws.onMessage { ctx ->
            WebView.handleRequest(ctx)
        }
        ws.onClose { ctx ->
            WebView.removeClient(ctx)
        }
    }
}
