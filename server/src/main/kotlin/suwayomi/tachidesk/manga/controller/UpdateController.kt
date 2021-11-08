package suwayomi.tachidesk.manga.controller

import io.javalin.http.Context
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.server.JavalinSetup.future

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object UpdateController {
    /** get recently updated manga chapters */
    fun recentChapters(ctx: Context) {
        val pageNum = ctx.pathParam("pageNum").toInt()

        ctx.future(
            future {
                Chapter.getRecentChapters(pageNum)
            }
        )
    }
}
