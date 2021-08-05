package suwayomi.tachidesk.global.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import suwayomi.tachidesk.global.impl.About
import suwayomi.tachidesk.global.impl.AppUpdate
import suwayomi.tachidesk.server.JavalinSetup

/** Settings Page/Screen */
object SettingsController {
    /** returns some static info about the current app build */
    fun about(ctx: Context): Context {
        return ctx.json(About.getAbout())
    }

    /** check for app updates */
    fun checkUpdate(ctx: Context): Context {
        return ctx.json(
            JavalinSetup.future { AppUpdate.checkUpdate() }
        )
    }
}
