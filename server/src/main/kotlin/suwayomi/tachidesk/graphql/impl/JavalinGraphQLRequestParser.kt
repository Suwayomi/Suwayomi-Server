/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.impl

import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.http.Context
import java.io.IOException

/**
 * Custom logic for how Javalin parses the incoming [Context] into the [GraphQLServerRequest]
 */
class JavalinGraphQLRequestParser(
    private val mapper: ObjectMapper
) : GraphQLRequestParser<Context> {

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun parseRequest(context: Context): GraphQLServerRequest = try {
        val rawRequest = context.body()
        mapper.readValue(rawRequest, GraphQLServerRequest::class.java)
    } catch (e: IOException) {
        throw IOException("Unable to parse GraphQL payload.")
    }
}
