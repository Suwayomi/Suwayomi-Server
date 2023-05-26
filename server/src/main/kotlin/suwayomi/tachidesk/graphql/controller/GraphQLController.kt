/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.controller

import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.websocket.WsConfig
import suwayomi.tachidesk.graphql.server.TachideskGraphQLServer
import suwayomi.tachidesk.server.JavalinSetup.future

object GraphQLController {
    private val server = TachideskGraphQLServer.create()

    /** execute graphql query */
    fun execute(ctx: Context) {
        ctx.future(
            future {
                server.execute(ctx)
            }
        )
    }

    fun playground(ctx: Context) {
        ctx.contentType(ContentType.TEXT_HTML)
        ctx.result(javaClass.getResourceAsStream("/graphql-playground.html")!!)
    }

    fun webSocket(ws: WsConfig) {
        ws.onMessage { ctx ->
            server.handleSubscriptionMessage(ctx)
        }
        ws.onClose { ctx ->
            server.handleSubscriptionDisconnect(ctx)
        }
    }
}
