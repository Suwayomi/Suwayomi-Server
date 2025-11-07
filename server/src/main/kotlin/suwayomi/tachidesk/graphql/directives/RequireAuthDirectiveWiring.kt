/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.directives

import com.expediagroup.graphql.generator.directives.KotlinFieldDirectiveEnvironment
import com.expediagroup.graphql.generator.directives.KotlinSchemaDirectiveWiring
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironmentImpl
import graphql.schema.GraphQLFieldDefinition
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.requireUser

private const val USER_ID_PARAM = "userId"

class RequireAuthDirectiveWiring : KotlinSchemaDirectiveWiring {
    override fun onField(environment: KotlinFieldDirectiveEnvironment): GraphQLFieldDefinition {
        val originalDataFetcher = environment.getDataFetcher()

        val authDataFetcher =
            DataFetcher { env ->
                val user = env.graphQlContext.getAttribute(Attribute.TachideskUser)
                val userId = user.requireUser()

                if (env.arguments.containsKey(USER_ID_PARAM)) {
                    throw Exception("\"$USER_ID_PARAM\" is a reserved parameter for RequireAuth")
                }

                // Create a new environment with userId added to arguments
                val newArguments: MutableMap<String, Any> = env.arguments.toMutableMap()
                newArguments[USER_ID_PARAM] = userId

                val modifiedEnv =
                    DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(env)
                        .arguments(newArguments)
                        .build()

                originalDataFetcher.get(modifiedEnv)
            }

        environment.setDataFetcher(authDataFetcher)
        return environment.element
    }
}
