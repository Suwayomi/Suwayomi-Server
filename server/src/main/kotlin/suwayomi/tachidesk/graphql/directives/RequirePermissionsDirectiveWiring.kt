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
import graphql.schema.GraphQLFieldDefinition
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.requirePermissions

class RequirePermissionsDirectiveWiring : KotlinSchemaDirectiveWiring {
    override fun onField(environment: KotlinFieldDirectiveEnvironment): GraphQLFieldDefinition {
        val originalDataFetcher = environment.getDataFetcher()

        val rawPermissionValue: Any? = environment.directive.getArgument("permission")?.getValue<Any?>()
        val permissions =
            when (rawPermissionValue) {
                null -> emptyList()
                is Array<*> -> rawPermissionValue.filterIsInstance<UserPermission>()
                is List<*> -> rawPermissionValue.filterIsInstance<UserPermission>()
                else -> emptyList()
            }

        val authDataFetcher =
            DataFetcher { env ->
                val user = env.graphQlContext.getAttribute(Attribute.TachideskUser)
                user.requirePermissions(*permissions.toTypedArray())

                originalDataFetcher.get(env)
            }

        environment.setDataFetcher(authDataFetcher)
        return environment.element
    }
}
