package suwayomi.tachidesk.global

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import suwayomi.tachidesk.global.controller.SettingsController

object GlobalAPI {
    fun defineEndpoints(app: Javalin) {
        app.routes {
            path("api/v1/settings") {
                get("about", SettingsController::about)
                get("check-update", SettingsController::checkUpdate)
            }
        }
    }
}
