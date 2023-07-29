/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.generator.extensions.toGraphQLContext
import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import io.javalin.http.Context
import io.javalin.websocket.WsContext
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute

/**
 * Custom logic for how Tachidesk should create its context given the [Context]
 */
class TachideskGraphQLContextFactory : GraphQLContextFactory<Context> {
    override suspend fun generateContext(request: Context): GraphQLContext {
        return mapOf(
            Context::class to request,
            request.getPair(Attribute.TachideskUser)
        ).toGraphQLContext()
    }

    private fun <T : Any> Context.getPair(attribute: Attribute<T>) =
        attribute to getAttribute(attribute)

    fun generateContextMap(request: WsContext): Map<*, Any> = emptyMap<Any, Any>()
}

fun <T : Any> GraphQLContext.getAttribute(attribute: Attribute<T>): T {
    return get(attribute)
}

fun <T : Any> DataFetchingEnvironment.getAttribute(attribute: Attribute<T>): T {
    return graphQlContext.get(attribute)
}
