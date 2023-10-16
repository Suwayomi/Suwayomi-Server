package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.HttpCode
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.model.dataclass.TrackSearchDataClass
import suwayomi.tachidesk.manga.model.dataclass.TrackerDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler

object TrackController {
    private val json by DI.global.instance<Json>()
    private val logger = KotlinLogging.logger {}

    val list =
        handler(
            behaviorOf = { ctx ->
                ctx.json(Track.getTrackerList())
            },
            withResults = {
                json<Array<TrackerDataClass>>(HttpCode.OK)
            },
        )

    val login =
        handler(
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.LoginInput>(ctx.body())
                logger.debug { "tracker login $input" }
                ctx.future(future { Track.login(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    val logout =
        handler(
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.LogoutInput>(ctx.body())
                logger.debug { "tracker logout $input" }
                ctx.future(future { Track.logout(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    val search =
        handler(
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.SearchInput>(ctx.body())
                logger.debug { "tracker search $input" }
                ctx.future(future { Track.search(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    val bind =
        handler(
            behaviorOf = { ctx ->
                val input = json.decodeFromString<TrackSearchDataClass>(ctx.body())
                logger.debug { "tracker bind $input" }
                ctx.future(future { Track.bind(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    val update =
        handler(
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.UpdateInput>(ctx.body())
                logger.debug { "tracker update $input" }
                ctx.future(future { Track.update(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )
}
