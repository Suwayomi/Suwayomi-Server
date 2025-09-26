/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.directives

import com.expediagroup.graphql.generator.directives.KotlinFieldDirectiveEnvironment
import com.expediagroup.graphql.generator.directives.KotlinSchemaDirectiveWiring
import graphql.schema.DataFetcherFactories
import graphql.schema.GraphQLFieldDefinition
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.UnauthorizedException
import suwayomi.tachidesk.server.user.requireUser
import java.util.function.BiFunction

class RequireAuthDirectiveWiring : KotlinSchemaDirectiveWiring {
    override fun onField(environment: KotlinFieldDirectiveEnvironment): GraphQLFieldDefinition {
        val originalDataFetcher = environment.getDataFetcher()

        val authDataFetcher =
            DataFetcherFactories.wrapDataFetcher(
                originalDataFetcher,
            ) { env, value ->
                val user = env.graphQlContext.getAttribute(Attribute.TachideskUser)
                user.requireUser()
                value
            }

        environment.setDataFetcher(authDataFetcher)
        return environment.element
    }
}
