/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.http.HttpCode
import suwayomi.tachidesk.graphql.impl.getGraphQLServer
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.withOperation

object GraphQLController {
    private val mapper = jacksonObjectMapper()
    private val tachideskGraphQLServer = getGraphQLServer(mapper)

    /** execute graphql query */
    val execute = handler(
        documentWith = {
            withOperation {
                summary("GraphQL endpoint")
                description("Endpoint for GraphQL endpoints")
            }
        },

        behaviorOf = { ctx ->
            ctx.future(
                future {
                    tachideskGraphQLServer.execute(ctx)
                }
            )
        },
        withResults = {
            json<Any>(HttpCode.OK)
        }
    )
}
