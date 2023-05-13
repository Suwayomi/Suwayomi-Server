/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.server.execution.GraphQLRequestParser
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import io.javalin.http.Context
import java.io.IOException

class JavalinGraphQLRequestParser : GraphQLRequestParser<Context> {

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun parseRequest(context: Context): GraphQLServerRequest? = try {
        context.bodyAsClass(GraphQLServerRequest::class.java)
    } catch (e: IOException) {
        null
    }
}
