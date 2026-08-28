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
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.Permissions
import suwayomi.tachidesk.server.user.requirePermissions

class RequirePermissionsDirectiveWiring : KotlinSchemaDirectiveWiring {
    override fun onField(environment: KotlinFieldDirectiveEnvironment): GraphQLFieldDefinition {
        val originalDataFetcher = environment.getDataFetcher()

        val rawPermissionValue: Any? = environment.directive.getArgument("permission")?.getValue<Any?>()
        val permissionNames =
            when (rawPermissionValue) {
                null -> emptyList()
                is Array<*> -> rawPermissionValue.filterIsInstance<String>()
                is List<*> -> rawPermissionValue.mapNotNull { it as? String }
                else -> emptyList()
            }

        val permissions =
            permissionNames.mapNotNull { name ->
                Permissions.entries.find { it.name == name }
            }

        val authDataFetcher =
            DataFetcher { env ->
                if (permissions.size != permissionNames.size) {
                    // an unknown permission name was used in the directive: fail closed
                    throw ForbiddenException()
                }

                val user = env.graphQlContext.getAttribute(Attribute.TachideskUser)
                user.requirePermissions(*permissions.toTypedArray())

                originalDataFetcher.get(env)
            }

        environment.setDataFetcher(authDataFetcher)
        return environment.element
    }
}
