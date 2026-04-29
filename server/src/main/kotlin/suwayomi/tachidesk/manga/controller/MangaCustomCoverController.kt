package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpStatus
import suwayomi.tachidesk.manga.impl.MangaUserOverride
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation
import java.nio.file.Files
import kotlin.time.Duration.Companion.hours

object MangaCustomCoverController {
    /** Serves the user-uploaded cover override for a manga, if one exists. */
    val serve =
        handler(
            pathParam<Int>("mangaId"),
            documentWith = {
                withOperation {
                    summary("Get a manga's user-uploaded custom cover")
                    description(
                        "Returns the custom cover image previously uploaded for this manga via the GraphQL " +
                            "setMangaCustomCover mutation. 404 if none exists.",
                    )
                }
            },
            behaviorOf = { ctx, mangaId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val path = MangaUserOverride.customCoverPath(mangaId)
                if (path == null) {
                    ctx.status(HttpStatus.NOT_FOUND)
                } else {
                    val mime = Files.probeContentType(path) ?: "application/octet-stream"
                    ctx.contentType(mime)
                    ctx.header("cache-control", "max-age=${1.hours.inWholeSeconds}")
                    ctx.result(Files.newInputStream(path))
                }
            },
            withResults = {
                image(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
