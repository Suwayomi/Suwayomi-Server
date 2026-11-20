package suwayomi.tachidesk.global

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.ws
import suwayomi.tachidesk.global.controller.SettingsController
import suwayomi.tachidesk.global.controller.WebViewController

object GlobalAPI {
    fun defineEndpoints() {
        path("settings") {
            get("about", SettingsController.about)
        }
        path("webview") {
            get("", WebViewController.webview)
            ws("", WebViewController::webviewWS)
        }
    }
}
