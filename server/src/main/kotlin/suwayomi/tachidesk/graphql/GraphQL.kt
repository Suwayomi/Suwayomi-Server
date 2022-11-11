/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql

import io.javalin.apibuilder.ApiBuilder.*
import suwayomi.tachidesk.graphql.controller.GraphQLController

object GraphQL {
    fun defineEndpoints() {
        post("graphql", GraphQLController::execute)

        // graphql playground
        get("graphql", GraphQLController::playground)
    }
}
