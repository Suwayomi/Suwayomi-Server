/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.http.Context
import suwayomi.tachidesk.graphql.impl.getGraphQLServer
import suwayomi.tachidesk.server.JavalinSetup.future

object GraphQLController {
    private val mapper = jacksonObjectMapper()
    private val tachideskGraphQLServer = getGraphQLServer(mapper)

    /** execute graphql query */
    fun execute(ctx: Context) {
        ctx.future(
            future {
                tachideskGraphQLServer.execute(ctx)
            }
        )
    }

    fun playground(ctx: Context) {
        val playgroundHtml = javaClass.getResource("/graphql-playground.html")

        val body = playgroundHtml.openStream().bufferedReader().use { reader ->
            val graphQLEndpoint = "graphql"
            val subscriptionsEndpoint = "graphql"

            reader.readText()
                .replace("\${graphQLEndpoint}", graphQLEndpoint)
                .replace("\${subscriptionsEndpoint}", subscriptionsEndpoint)
        }

        ctx.html(body ?: "Could not load playground")
    }
}
