/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import suwayomi.tachidesk.graphql.controller.GraphQLController

object GraphQL {
    fun defineEndpoints() {
        post("graphql", GraphQLController.execute)
        get("graphql") { ctx ->
            val html = javaClass.getResource("/graphql.html")?.readText()
            ctx.html(html ?: "Could not load playground")
        }
    }
}
