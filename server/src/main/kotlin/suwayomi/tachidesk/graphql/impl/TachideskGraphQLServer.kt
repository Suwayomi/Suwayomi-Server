/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.impl

import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.http.Context
import suwayomi.tachidesk.graphql.dataLoaders.tachideskDataLoaderRegistryFactory

class TachideskGraphQLServer(
    requestParser: JavalinGraphQLRequestParser,
    contextFactory: TachideskGraphQLContextFactory,
    requestHandler: GraphQLRequestHandler
) : GraphQLServer<Context>(requestParser, contextFactory, requestHandler)

fun getGraphQLServer(mapper: ObjectMapper): TachideskGraphQLServer {
    val requestParser = JavalinGraphQLRequestParser(mapper)
    val contextFactory = TachideskGraphQLContextFactory()
    val graphQL = getGraphQLObject()
    val requestHandler = GraphQLRequestHandler(graphQL, tachideskDataLoaderRegistryFactory)

    return TachideskGraphQLServer(requestParser, contextFactory, requestHandler)
}
