package suwayomi.tachidesk.global

import io.javalin.Javalin
import suwayomi.tachidesk.global.impl.About
import suwayomi.tachidesk.global.impl.AppUpdate
import suwayomi.tachidesk.server.JavalinSetup

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object GlobalAPI {
    fun defineEndpoints(app: Javalin) {
        // returns some static info about the current app build
        app.get("/api/v1/settings/about/") { ctx ->
            ctx.json(About.getAbout())
        }

        // check for app updates
        app.get("/api/v1/settings/check-update/") { ctx ->

            ctx.json(
                JavalinSetup.future { AppUpdate.checkUpdate() }
            )
        }
    }
}
