/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.directives

import com.expediagroup.graphql.generator.annotations.GraphQLDirective
import graphql.introspection.Introspection.DirectiveLocation
import suwayomi.tachidesk.server.user.UserPermission

@GraphQLDirective(
    name = "requirePermissions",
    description = "Requires the user to have all of the given permissions",
    locations = [
        DirectiveLocation.FIELD_DEFINITION,
        DirectiveLocation.OBJECT,
    ],
)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequirePermissions(
    vararg val permission: UserPermission,
)
