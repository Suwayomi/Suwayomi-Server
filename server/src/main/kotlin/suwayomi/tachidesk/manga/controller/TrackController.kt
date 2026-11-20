package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.HttpStatus
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.withOperation
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.days

object TrackController {
    private val json: Json by injectLazy()
    private val logger = KotlinLogging.logger {}

    val thumbnail =
        handler(
            pathParam<Int>("trackerId"),
            documentWith = {
                withOperation {
                    summary("Get a tracker thumbnail")
                    description("Get a tracker thumbnail from the resources.")
                }
            },
            behaviorOf = { ctx, trackerId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { Track.getTrackerThumbnail(trackerId) }
                        .thenApply {
                            ctx.header("content-type", it.second)
                            val httpCacheSeconds = 1.days.inWholeSeconds
                            ctx.header("cache-control", "max-age=$httpCacheSeconds")
                            ctx.result(it.first)
                        }
                }
            },
            withResults = {
                image(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
