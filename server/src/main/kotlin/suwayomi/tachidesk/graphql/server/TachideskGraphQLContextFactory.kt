/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import graphql.GraphQLContext
import io.javalin.http.Context
import io.javalin.websocket.WsContext

/**
 * Custom logic for how Suwayomi-Server should create its context given the [Context]
 */
class TachideskGraphQLContextFactory : GraphQLContextFactory<Context> {
    override suspend fun generateContext(request: Context): GraphQLContext = emptyMap<Any, Any>().toGraphQLContext()
//        mutableMapOf<Any, Any>(
//            "user" to User(
//                email = "fake@site.com",
//                firstName = "Someone",
//                lastName = "You Don't know",
//                universityId = 4
//            )
//        ).also { map ->
//            request.headers["my-custom-header"]?.let { customHeader ->
//                map["customHeader"] = customHeader
//            }
//        }.toGraphQLContext()

    fun generateContextMap(
        @Suppress("UNUSED_PARAMETER") request: WsContext,
    ): Map<*, Any> = emptyMap<Any, Any>()
}

/**
 * Create a [GraphQLContext] from [this] map
 * @return a new [GraphQLContext]
 */
fun Map<*, Any?>.toGraphQLContext(): graphql.GraphQLContext = graphql.GraphQLContext.of(this)
