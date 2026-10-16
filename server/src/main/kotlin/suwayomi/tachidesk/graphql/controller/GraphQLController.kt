/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.NotFoundResponse
import io.javalin.websocket.WsConfig
import suwayomi.tachidesk.graphql.server.TachideskGraphQLServer
import suwayomi.tachidesk.graphql.server.TemporaryFileStorage
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.server.user.requireUser
import xyz.nulldev.androidcompat.util.SafePath
import java.io.IOException
import kotlin.io.path.inputStream

object GraphQLController {
    private val logger = KotlinLogging.logger {}
    private val server = TachideskGraphQLServer.create()

    /** execute graphql query */
    fun execute(ctx: Context) {
        val contentType = ctx.contentType()
        val isFormContentType =
            contentType?.contains("application/x-www-form-urlencoded", true) == true ||
                contentType?.contains("multipart/form-data", true) == true
        if (ctx.contentType()?.contains("application/json", true) != true && !isFormContentType) {
            ctx.status(400)
            ctx.result("Invalid mime type")
            return
        }
        ctx.future {
            future {
                server.execute(ctx)
            }.thenApply {
                if (it != null) {
                    ctx.json(it)
                } else {
                    ctx.status(HttpStatus.BAD_REQUEST)
                }
            }
        }
    }

    fun playground(ctx: Context) {
        val stream = javaClass.getResourceAsStream("/graphql-playground.html")
        if (stream == null) {
            logger.error { "graphql-playground.html is missing from the classpath" }
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
            return
        }
        ctx.contentType(ContentType.TEXT_HTML)
        ctx.result(stream)
    }

    fun retrieveFile(ctx: Context) {
        // backup downloads are authenticated: the download URL is only ever handed out to a
        // logged-in user, and the file name is unguessable on top of that
        val user = ctx.attribute<UserType>(Attribute.TachideskUser.name) ?: UserType.Visitor
        user.requireUser()

        val filename = ctx.pathParam("file")
        val file = TemporaryFileStorage.retrieveFile(filename)
        ctx.contentType("application/octet-stream")
        ctx.header("Content-Disposition", """attachment; filename="${SafePath.buildValidFilename(filename)}"""")
        ctx.result(
            try {
                file.inputStream()
            } catch (e: IOException) {
                // the file may have been evicted between the existence check and opening it
                throw NotFoundResponse("File not found")
            },
        )
    }

    fun webSocket(ws: WsConfig) {
        ws.onConnect { ctx ->
            server.handleSubscriptionConnect(ctx)
        }
        ws.onMessage { ctx ->
            server.handleSubscriptionMessage(ctx)
        }
        ws.onClose { ctx ->
            server.handleSubscriptionDisconnect(ctx)
        }
    }
}
