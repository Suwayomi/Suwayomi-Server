package suwayomi.tachidesk.anime.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.anime.impl.Anime
import suwayomi.tachidesk.anime.impl.AnimeDownload
import suwayomi.tachidesk.anime.impl.AnimeLibrary
import suwayomi.tachidesk.anime.impl.AnimeVideo
import suwayomi.tachidesk.anime.impl.Episode
import suwayomi.tachidesk.anime.model.dataclass.AnimeDataClass
import suwayomi.tachidesk.anime.model.dataclass.EpisodeDataClass
import suwayomi.tachidesk.anime.model.dataclass.HosterDataClass
import suwayomi.tachidesk.anime.model.dataclass.VideoDataClass
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.anime.model.table.toDataClass
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.Response
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.days
import uy.kohesive.injekt.injectLazy

object AnimeController {
    private val logger = KotlinLogging.logger {}
    private val json: Json by injectLazy()

    private class ResponseInputStream(
        private val response: Response,
        private val delegate: InputStream,
    ) : InputStream() {
        override fun read(): Int = readSafely { delegate.read() }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = readSafely { delegate.read(b, off, len) }

        override fun close() {
            delegate.close()
            response.close()
        }

        private fun readSafely(readAction: () -> Int): Int {
            return try {
                readAction()
            } catch (e: IOException) {
                if (e.message?.contains("closed", ignoreCase = true) == true) {
                    -1
                } else {
                    throw e
                }
            }
        }
    }

    private fun streamProxyResponse(
        ctx: Context,
        response: Response,
    ) {
        val body = response.body
        if (body == null) {
            response.close()
            ctx.result(ByteArrayInputStream(ByteArray(0)))
            return
        }
        ctx.result(ResponseInputStream(response, body.byteStream()))
    }
    val retrieve =
        handler(
            pathParam<Int>("animeId"),
            queryParam("onlineFetch", false),
            documentWith = {
                withOperation {
                    summary("Get anime info")
                    description("Get an anime from the database using a specific id.")
                }
            },
            behaviorOf = { ctx, animeId, onlineFetch ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        Anime.getAnime(animeId, onlineFetch)
                    }.thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<AnimeDataClass>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** get episode list when showing an anime */
    val episodeList =
        handler(
            pathParam<Int>("animeId"),
            queryParam("onlineFetch", false),
            documentWith = {
                withOperation {
                    summary("Get anime episode list")
                    description("Get the anime episode list from the database or online.")
                }
            },
            behaviorOf = { ctx, animeId, onlineFetch ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { Episode.getEpisodeList(animeId, onlineFetch) }
                        .thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<Array<EpisodeDataClass>>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** get anime library list */
    val libraryList =
        handler(
            documentWith = {
                withOperation {
                    summary("Get anime library")
                    description("Get the list of anime in the library.")
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        val animeList =
                            transaction {
                                AnimeTable
                                    .selectAll()
                                    .where { AnimeTable.inLibrary eq true }
                                    .orderBy(AnimeTable.inLibraryAt to SortOrder.DESC)
                                    .map { AnimeTable.toDataClass(it) }
                            }
                        animeList
                    }.thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<Array<AnimeDataClass>>(HttpStatus.OK)
            },
        )

    /** adds the anime to library */
    val addToLibrary =
        handler(
            pathParam<Int>("animeId"),
            documentWith = {
                withOperation {
                    summary("Add anime to library")
                    description("Use an anime id to add the anime to your library.\nWill do nothing if anime is already in your library.")
                }
            },
            behaviorOf = { ctx, animeId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeLibrary.addAnimeToLibrary(animeId) }
                        .thenApply { ctx.status(HttpStatus.OK) }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** removes the anime from the library */
    val removeFromLibrary =
        handler(
            pathParam<Int>("animeId"),
            documentWith = {
                withOperation {
                    summary("Remove anime from library")
                    description("Use an anime id to remove the anime from your library.\nWill do nothing if anime is not in your library.")
                }
            },
            behaviorOf = { ctx, animeId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeLibrary.removeAnimeFromLibrary(animeId) }
                        .thenApply { ctx.status(HttpStatus.OK) }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** update episode states in batch */
    val episodeBatch =
        handler(
            documentWith = {
                withOperation {
                    summary("Update episodes")
                    description("Update episode read/download status in batch.")
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val input = json.decodeFromString<Episode.EpisodeBatchEditInput>(ctx.body())
                Episode.modifyEpisodes(input)
                ctx.status(HttpStatus.OK)
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    /** download a specific episode video */
    val downloadEpisodeVideo =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            pathParam<Int>("videoIndex"),
            documentWith = {
                withOperation {
                    summary("Download episode video")
                    description("Download a specific episode video and mark the episode as downloaded.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, videoIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeDownload.downloadEpisodeVideo(animeId, episodeIndex, videoIndex) }
                        .thenApply { ctx.status(HttpStatus.OK) }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    /** delete downloaded episode videos */
    val deleteEpisodeDownloads =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            documentWith = {
                withOperation {
                    summary("Delete episode downloads")
                    description("Delete downloaded files for an episode and mark it as not downloaded.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeDownload.deleteEpisodeDownloads(animeId, episodeIndex) }
                        .thenApply { ctx.status(HttpStatus.OK) }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    /** get episode info */
    val episodeRetrieve =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            documentWith = {
                withOperation {
                    summary("Get an episode")
                    description("Get the episode from the anime id and episode index.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { Episode.getEpisodeByIndex(animeId, episodeIndex) }
                        .thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<EpisodeDataClass>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** anime thumbnail */
    val thumbnail =
        handler(
            pathParam<Int>("animeId"),
            documentWith = {
                withOperation {
                    summary("Get an anime thumbnail")
                    description("Get an anime thumbnail from the source or the cache.")
                }
            },
            behaviorOf = { ctx, animeId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { Anime.fetchAnimeThumbnail(animeId) }
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

    /** get videos for episode */
    val episodeVideos =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            queryParam("onlineFetch", false),
            documentWith = {
                withOperation {
                    summary("Get episode videos")
                    description("Get the video list for the episode.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, onlineFetch ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeVideo.getEpisodeVideos(animeId, episodeIndex, onlineFetch) }
                        .thenApply { videos ->
                            ctx.json(videos.map { it.copy(videoUrl = "") })
                        }
                }
            },
            withResults = {
                json<Array<VideoDataClass>>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** get hosters for episode */
    val episodeHosters =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            documentWith = {
                withOperation {
                    summary("Get episode hosters")
                    description("Get the hoster list for the episode.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeVideo.getHosters(animeId, episodeIndex) }
                        .thenApply { ctx.json(it) }
                }
            },
            withResults = {
                json<Array<HosterDataClass>>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** get videos for hoster */
    val hosterVideos =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            pathParam<Int>("hosterIndex"),
            documentWith = {
                withOperation {
                    summary("Get hoster videos")
                    description("Get the video list for a hoster.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, hosterIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeVideo.getHosterVideos(animeId, episodeIndex, hosterIndex) }
                        .thenApply { videos ->
                            ctx.json(videos.map { it.copy(videoUrl = "") })
                        }
                }
            },
            withResults = {
                json<Array<VideoDataClass>>(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** proxy a video stream */
    val videoProxy =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            pathParam<Int>("videoIndex"),
            documentWith = {
                withOperation {
                    summary("Proxy episode video")
                    description("Stream the selected video through the server.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, videoIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.disableCompression()
                logger.info { "Anime video proxy request animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex range=${ctx.header("Range")}" }
                ctx.future {
                    future {
                        AnimeVideo.getEpisodeVideoResponse(animeId, episodeIndex, videoIndex, ctx.header("Range"))
                    }.thenApply { response ->
                        ctx.status(response.code)
                        response.header("Content-Type")?.let { ctx.header("Content-Type", it) }
                        response.body?.contentLength()?.takeIf { it >= 0 }?.let {
                            ctx.header("Content-Length", it.toString())
                        }
                        response.header("Content-Range")?.let { ctx.header("Content-Range", it) }
                        response.header("Accept-Ranges")?.let { ctx.header("Accept-Ranges", it) }
                        ctx.header("cache-control", "max-age=${1.days.inWholeSeconds}")
                        logger.info {
                            "Anime video proxy response animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex status=${response.code} contentType=${response.header("Content-Type")}" }
                        streamProxyResponse(ctx, response)
                    }.exceptionally { throwable ->
                        val cause = throwable.cause ?: throwable
                        if (cause is java.net.SocketTimeoutException) {
                            ctx.status(HttpStatus.GATEWAY_TIMEOUT)
                            ctx.result("Video proxy timed out")
                        } else {
                            ctx.status(HttpStatus.NOT_FOUND)
                            ctx.result("Video not found")
                        }
                        logger.warn(cause) {
                            "Anime video proxy failed animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex"
                        }
                        null
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.PARTIAL_CONTENT)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** proxy a video playlist */
    val videoPlaylist =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            pathParam<Int>("videoIndex"),
            documentWith = {
                withOperation {
                    summary("Proxy episode playlist")
                    description("Proxy the HLS playlist through the server.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, videoIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future { AnimeVideo.getEpisodeVideoPlaylist(animeId, episodeIndex, videoIndex) }
                        .thenApply {
                            ctx.header("Content-Type", "application/vnd.apple.mpegurl")
                            ctx.result(it)
                        }
                }
            },
            withResults = {
                httpCode(HttpStatus.NOT_FOUND)
                httpCode(HttpStatus.OK)
            },
        )

    /** proxy a video segment */
    val videoSegmentProxy =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            pathParam<Int>("videoIndex"),
            pathParam<String>("token"),
            documentWith = {
                withOperation {
                    summary("Proxy episode segment")
                    description("Proxy a single segment or key from the playlist through the server.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, videoIndex, token ->
                val context: Context = ctx
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.disableCompression()
                ctx.future {
                    future {
                        val resolvedUrl = AnimeVideo.resolveSegmentToken(token)
                            ?: throw IllegalArgumentException("Segment not found")
                        AnimeVideo.getEpisodeVideoSegment(
                            animeId,
                            episodeIndex,
                            videoIndex,
                            resolvedUrl,
                            context.header("Range"),
                        )
                    }.thenApply { response ->
                        context.status(response.code)
                        response.header("Content-Type")?.let { context.header("Content-Type", it) }
                        response.body?.contentLength()?.takeIf { it >= 0 }?.let {
                            context.header("Content-Length", it.toString())
                        }
                        response.header("Content-Range")?.let { context.header("Content-Range", it) }
                        response.header("Accept-Ranges")?.let { context.header("Accept-Ranges", it) }
                        context.header("cache-control", "max-age=${1.days.inWholeSeconds}")
                        streamProxyResponse(context, response)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.PARTIAL_CONTENT)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** proxy a subtitle track */
    val subtitleProxy =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            pathParam<Int>("videoIndex"),
            pathParam<String>("token"),
            documentWith = {
                withOperation {
                    summary("Proxy subtitle track")
                    description("Proxy a subtitle track through the server.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, videoIndex, token ->
                val context: Context = ctx
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.disableCompression()
                ctx.future {
                    future {
                        AnimeVideo.getEpisodeSubtitleResponse(
                            animeId,
                            episodeIndex,
                            videoIndex,
                            token,
                            context.queryParam("url"),
                        )
                    }.thenApply { response ->
                        context.status(response.code)
                        response.header("Content-Type")?.let { context.header("Content-Type", it) }
                        response.header("Content-Length")?.let { context.header("Content-Length", it) }
                        context.header("cache-control", "max-age=${1.days.inWholeSeconds}")
                        streamProxyResponse(context, response)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** proxy a hoster video stream */
    val hosterVideoProxy =
        handler(
            pathParam<Int>("animeId"),
            pathParam<Int>("episodeIndex"),
            pathParam<Int>("hosterIndex"),
            pathParam<Int>("videoIndex"),
            documentWith = {
                withOperation {
                    summary("Proxy hoster video")
                    description("Stream the selected hoster video through the server.")
                }
            },
            behaviorOf = { ctx, animeId, episodeIndex, hosterIndex, videoIndex ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.disableCompression()
                ctx.future {
                    future {
                        AnimeVideo.getHosterVideoResponse(animeId, episodeIndex, hosterIndex, videoIndex, ctx.header("Range"))
                    }.thenApply { response ->
                        ctx.status(response.code)
                        response.header("Content-Type")?.let { ctx.header("Content-Type", it) }
                        response.header("Content-Length")?.let { ctx.header("Content-Length", it) }
                        response.header("Content-Range")?.let { ctx.header("Content-Range", it) }
                        response.header("Accept-Ranges")?.let { ctx.header("Accept-Ranges", it) }
                        ctx.header("cache-control", "max-age=${1.days.inWholeSeconds}")
                        streamProxyResponse(ctx, response)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.PARTIAL_CONTENT)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
