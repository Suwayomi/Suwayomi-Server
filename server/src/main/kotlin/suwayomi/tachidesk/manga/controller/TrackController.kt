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
import suwayomi.tachidesk.manga.model.dataclass.TrackerDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import kotlin.time.Duration.Companion.days

object TrackController {
    private val json by DI.global.instance<Json>()
    private val logger = KotlinLogging.logger {}

    val list =
        handler(
            documentWith = {
                withOperation {
                    summary("List Supported Trackers")
                    description("List all supported Trackers")
                }
            },
            behaviorOf = { ctx ->
                ctx.json(Track.getTrackerList())
            },
            withResults = {
                json<Array<TrackerDataClass>>(HttpCode.OK)
            },
        )

    val login =
        handler(
            documentWith = {
                withOperation {
                    summary("Tracker Login")
                    description("Login to a tracker")
                }
                body<Track.LoginInput>()
            },
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.LoginInput>(ctx.body())
                logger.debug { "tracker login $input" }
                ctx.future(future { Track.login(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    val logout =
        handler(
            documentWith = {
                withOperation {
                    summary("Tracker Logout")
                    description("Logout of a Tracker")
                }
                body<Track.LogoutInput>()
            },
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.LogoutInput>(ctx.body())
                logger.debug { "tracker logout $input" }
                ctx.future(future { Track.logout(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    val search =
        handler(
            documentWith = {
                withOperation {
                    summary("Tracker Search")
                    description("Search for a title on a tracker")
                }
                body<Track.SearchInput>()
            },
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.SearchInput>(ctx.body())
                logger.debug { "tracker search $input" }
                ctx.future(future { Track.search(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )

    val bind =
        handler(
            queryParam<Int>("mangaId"),
            queryParam<Int>("trackerId"),
            queryParam<String>("remoteId"),
            documentWith = {
                withOperation {
                    summary("Track Record Bind")
                    description("Bind a Track Record to a Manga")
                }
            },
            behaviorOf = { ctx, mangaId, trackerId, remoteId ->
                ctx.future(future { Track.bind(mangaId, trackerId, remoteId.toLong()) })
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

    val update =
        handler(
            documentWith = {
                withOperation {
                    summary("Track Update")
                    description("Update a Track Record with the Tracker")
                }
                body<Track.UpdateInput>()
            },
            behaviorOf = { ctx ->
                val input = json.decodeFromString<Track.UpdateInput>(ctx.body())
                logger.debug { "tracker update $input" }
                ctx.future(future { Track.update(input) })
            },
            withResults = {
                httpCode(HttpCode.OK)
            },
        )

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
                ctx.future(
                    future { Track.getTrackerThumbnail(trackerId) }
                        .thenApply {
                            ctx.header("content-type", it.second)
                            val httpCacheSeconds = 1.days.inWholeSeconds
                            ctx.header("cache-control", "max-age=$httpCacheSeconds")
                            it.first
                        },
                )
            },
            withResults = {
                image(HttpCode.OK)
                httpCode(HttpCode.NOT_FOUND)
            },
        )
}
