/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.directives

import com.expediagroup.graphql.generator.annotations.GraphQLDirective
import graphql.introspection.Introspection.DirectiveLocation

// FIELD_DEFINITION only: the wiring is onField(), an OBJECT location would be ignored
@GraphQLDirective(
    name = "requireAuth",
    description = "Requires user authentication",
    locations = [
        DirectiveLocation.FIELD_DEFINITION,
    ],
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireAuth
