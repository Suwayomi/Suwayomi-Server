package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.Video as SourceVideo
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.HttpException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import okhttp3.Response
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrStub
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.model.dataclass.HosterDataClass
import suwayomi.tachidesk.anime.model.dataclass.VideoDataClass
import suwayomi.tachidesk.anime.model.dataclass.toDataClass
import suwayomi.tachidesk.anime.model.table.AnimeTable
import eu.kanade.tachiyomi.animesource.model.Track
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Headers
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

object AnimeVideo {
    private val logger = KotlinLogging.logger { }
    private val episodeVideoCache = ConcurrentHashMap<String, VideoCacheEntry>()
    private val cacheTtlSeconds = 600L
    private val segmentTokenCache = ConcurrentHashMap<String, SegmentCacheEntry>()
    private val segmentCacheTtlSeconds = 900L
    private val subtitleTokenCache = ConcurrentHashMap<String, SubtitleCacheEntry>()
    private val subtitleCacheTtlSeconds = 900L
    private val hlsDecisionCache = ConcurrentHashMap<String, Boolean>()

    private data class VideoCacheEntry(
        val videos: List<SourceVideo>,
        val timestampSeconds: Long,
    )

    private data class SegmentCacheEntry(
        val url: String,
        val timestampSeconds: Long,
    )

    data class SubtitleCacheEntry(
        val url: String,
        val headers: List<Pair<String, String>>?,
        val timestampSeconds: Long,
    )

    private fun cacheKey(animeId: Int, episodeIndex: Int) = "$animeId:$episodeIndex"

    private fun getCachedVideos(animeId: Int, episodeIndex: Int, allowExpired: Boolean = false): List<SourceVideo>? {
        val entry = episodeVideoCache[cacheKey(animeId, episodeIndex)] ?: return null
        val isExpired = Instant.now().epochSecond - entry.timestampSeconds > cacheTtlSeconds
        if (isExpired && !allowExpired) {
            return null
        }
        if (isExpired && allowExpired) {
            return entry.videos
        }
        return entry.videos
    }

    private fun cacheVideos(animeId: Int, episodeIndex: Int, videos: List<SourceVideo>) {
        if (videos.isEmpty()) {
            return
        }
        episodeVideoCache[cacheKey(animeId, episodeIndex)] =
            VideoCacheEntry(videos, Instant.now().epochSecond)
    }

    private suspend fun fetchEpisodeVideos(
        source: AnimeHttpSource,
        animeId: Int,
        episodeIndex: Int,
        forceFetch: Boolean = false,
    ): List<SourceVideo> {
        logger.debug {
            "Fetching episode videos animeId=$animeId episodeIndex=$episodeIndex source=${source.name} forceFetch=$forceFetch"
        }
        if (!forceFetch) {
            val cachedVideos = getCachedVideos(animeId, episodeIndex)
            if (cachedVideos != null) {
                logger.debug {
                    "Using cached episode videos animeId=$animeId episodeIndex=$episodeIndex count=${cachedVideos.size}"
                }
                return cachedVideos
            }
        }

        var lastError: Exception? = null
        val episode = getEpisodeData(animeId, episodeIndex)
        var attempt = 0
        while (attempt < 2) {
            try {
                val videos = source.getVideoList(episode.toSEpisode())
                logger.debug {
                    "Fetched episode videos animeId=$animeId episodeIndex=$episodeIndex count=${videos.size}"
                }
                cacheVideos(animeId, episodeIndex, videos)
                return videos
            } catch (e: Exception) {
                lastError = e
                val shouldRetry = e is HttpException && e.code in listOf(500, 504, 525)
                logger.warn(e) {
                    "Episode video fetch failed animeId=$animeId episodeIndex=$episodeIndex retry=${shouldRetry && attempt == 0}"
                }
                if (!shouldRetry || attempt >= 1) {
                    break
                }
                delay(500)
            }
            attempt += 1
        }

        throw lastError ?: IllegalStateException("Failed to fetch episode videos")
    }

    suspend fun getEpisodeVideos(
        animeId: Int,
        episodeIndex: Int,
        forceFetch: Boolean = false,
    ): List<VideoDataClass> {
        val source = getCatalogueSourceOrStub(getSourceId(animeId))
        if (source !is AnimeHttpSource) {
            logger.warn { "Anime source not available for animeId=$animeId" }
            return emptyList()
        }
        return try {
            val videos = fetchEpisodeVideos(source, animeId, episodeIndex, forceFetch)
            videos.mapIndexed { index, video ->
                buildVideoDataClass(source, animeId, episodeIndex, index, video)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get episode videos for animeId=$animeId episodeIndex=$episodeIndex" }
            val staleVideos = getCachedVideos(animeId, episodeIndex, allowExpired = true)
            if (staleVideos.isNullOrEmpty()) {
                emptyList()
            } else {
                staleVideos.mapIndexed { index, video ->
                    buildVideoDataClass(source, animeId, episodeIndex, index, video)
                }
            }
        }
    }

    suspend fun getHosters(
        animeId: Int,
        episodeIndex: Int,
    ): List<HosterDataClass> {
        val source = getCatalogueSourceOrStub(getSourceId(animeId))
        val episode = getEpisodeData(animeId, episodeIndex)
        return try {
            source.getHosterList(episode.toSEpisode()).toDataClass()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get hosters for animeId=$animeId episodeIndex=$episodeIndex" }
            emptyList()
        }
    }

    suspend fun getHosterVideos(
        animeId: Int,
        episodeIndex: Int,
        hosterIndex: Int,
    ): List<VideoDataClass> {
        val source = getCatalogueSourceOrStub(getSourceId(animeId))
        if (source !is AnimeHttpSource) {
            logger.warn { "Anime source not available for animeId=$animeId" }
            return emptyList()
        }
        val episode = getEpisodeData(animeId, episodeIndex)
        val hosters = source.getHosterList(episode.toSEpisode())
        val hoster = hosters.getOrNull(hosterIndex) ?: return emptyList()
        val videos = source.getVideoList(hoster)
        return videos.mapIndexed { index, video ->
            buildVideoDataClass(source, animeId, episodeIndex, index, video, hosterIndex)
        }
    }

    suspend fun getEpisodeVideoResponse(
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
        rangeHeader: String?,
    ): Response {
        val source = getCatalogueSourceOrStub(getSourceId(animeId)) as AnimeHttpSource
        val videos =
            runCatching { fetchEpisodeVideos(source, animeId, episodeIndex) }
                .getOrElse {
                    logger.warn(it) {
                        "Failed to refresh episode videos for animeId=$animeId episodeIndex=$episodeIndex"
                    }
                    getCachedVideos(animeId, episodeIndex, allowExpired = true).orEmpty()
                }

        val video = videos.getOrNull(videoIndex) ?: throw IllegalArgumentException("Video not found")
        val response = getVideoResponse(source, video, rangeHeader)
        val contentType = response.header("Content-Type").orEmpty()
        val responseUrl = response.request.url.toString()
        val isPlaylistResponse =
            contentType.contains("mpegurl", ignoreCase = true) || responseUrl.endsWith(".m3u8", ignoreCase = true)
        if (isPlaylistResponse) {
            val preview = response.peekBody(512).string().trim()
            if (preview.startsWith("#EXTM3U")) {
                val bodyText = response.body?.string().orEmpty()
                cacheHlsDecision(video.videoUrl, true)
                val rewritten = rewritePlaylist(bodyText, responseUrl, animeId, episodeIndex, videoIndex)
                return response.newBuilder()
                    .body(rewritten.toResponseBody("application/vnd.apple.mpegurl".toMediaTypeOrNull()))
                    .build()
            }
            if (contentType.contains("mp2t", ignoreCase = true) || contentType.startsWith("video/", ignoreCase = true)) {
                cacheHlsDecision(video.videoUrl, false)
            }
        } else if (contentType.startsWith("video/", ignoreCase = true)) {
            cacheHlsDecision(video.videoUrl, false)
        }
        return response
    }

    suspend fun getHosterVideoResponse(
        animeId: Int,
        episodeIndex: Int,
        hosterIndex: Int,
        videoIndex: Int,
        rangeHeader: String?,
    ): Response {
        val source = getCatalogueSourceOrStub(getSourceId(animeId)) as AnimeHttpSource
        val episode = getEpisodeData(animeId, episodeIndex)
        val hosters = source.getHosterList(episode.toSEpisode())
        val hoster = hosters.getOrNull(hosterIndex) ?: throw IllegalArgumentException("Hoster not found")
        val videos = source.getVideoList(hoster)
        val video = videos.getOrNull(videoIndex) ?: throw IllegalArgumentException("Video not found")
        return getVideoResponse(source, video, rangeHeader)
    }

    private suspend fun getVideoResponse(
        source: AnimeHttpSource,
        video: SourceVideo,
        rangeHeader: String?,
    ): Response {
        val resolvedVideo = resolveVideo(source, video)
        val resolvedUrl = resolvedVideo.videoUrl
        val resolvedHost = resolvedUrl.toHttpUrlOrNull()?.host
        val isHls = resolvedUrl.contains(".m3u8", ignoreCase = true)
        val (start, end) = parseRange(rangeHeader)
        val baseRequest =
            if (start != null) {
                source.videoRequest(resolvedVideo, start, end ?: -1)
            } else {
                source.safeVideoRequest(resolvedVideo)
            }
        val request = baseRequest.newBuilder().apply {
            if (isHls && baseRequest.header("Accept") == null) {
                addHeader("Accept", "application/vnd.apple.mpegurl")
            }
            if (baseRequest.header("Referer") == null && resolvedUrl.startsWith("http")) {
                addHeader("Referer", resolvedUrl)
            }
            if (baseRequest.header("Origin") == null) {
                val origin = resolvedUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
                if (origin != null) {
                    addHeader("Origin", origin)
                }
            }
        }.build()
        val response = source.getVideo(
            request,
            object : ProgressListener {
                override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                    // no-op
                }
            },
        )
        return response
    }

    private suspend fun resolveVideo(
        source: AnimeHttpSource,
        video: SourceVideo,
    ): SourceVideo {
        val resolvedVideo = source.resolveVideo(video) ?: video
        if (resolvedVideo !== video) {
            logger.debug {
                "Resolved video url from=${video.videoUrl} to=${resolvedVideo.videoUrl}"
            }
        }
        if (resolvedVideo.videoUrl.isBlank() || resolvedVideo.videoUrl == "null") {
            resolvedVideo.videoUrl = source.getVideoUrl(resolvedVideo)
            logger.debug { "Filled video url via getVideoUrl: ${resolvedVideo.videoUrl}" }
        }
        return resolvedVideo
    }

    private fun buildVideoDataClass(
        source: AnimeHttpSource,
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
        video: SourceVideo,
        hosterIndex: Int? = null,
    ): VideoDataClass {
        val dataClass = VideoDataClass.fromVideo(video, proxyUrl(animeId, episodeIndex, videoIndex, hosterIndex))
        return dataClass.copy(
            isHls = resolveIsHls(source, video, dataClass.isHls),
            subtitleTracks = proxySubtitleTracks(animeId, episodeIndex, videoIndex, video),
        )
    }

    private fun resolveIsHls(
        source: AnimeHttpSource,
        video: SourceVideo,
        fallbackIsHls: Boolean,
    ): Boolean {
        if (!fallbackIsHls) return false
        hlsDecisionCache[video.videoUrl]?.let { return it }
        return true
    }

    private fun cacheHlsDecision(
        url: String,
        isHls: Boolean,
    ) {
        if (url.isNotBlank()) {
            hlsDecisionCache[url] = isHls
        }
    }

    private fun parseRange(rangeHeader: String?): Pair<Long?, Long?> {
        if (rangeHeader.isNullOrBlank()) return null to null
        val match = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader) ?: return null to null
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        return start to end
    }

    private suspend fun getEpisodeData(animeId: Int, episodeIndex: Int) =
        Episode.getEpisodeByIndex(animeId, episodeIndex)

    private fun getSourceId(animeId: Int): Long =
        transaction {
            AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first()[AnimeTable.sourceReference]
        }

    private fun proxyUrl(
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
        hosterIndex: Int? = null,
    ): String =
        if (hosterIndex == null) {
            "/api/v1/anime/$animeId/episode/$episodeIndex/video/$videoIndex"
        } else {
            "/api/v1/anime/$animeId/episode/$episodeIndex/hoster/$hosterIndex/video/$videoIndex"
        }

    suspend fun getEpisodeVideoPlaylist(
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
    ): String {
        val source = getCatalogueSourceOrStub(getSourceId(animeId)) as AnimeHttpSource
        var videos = fetchEpisodeVideos(source, animeId, episodeIndex)
        var video = videos.getOrNull(videoIndex) ?: throw IllegalArgumentException("Video not found")
        var resolvedVideo = resolveVideo(source, video)
        var attempt = 0
        val retryDelays = listOf(5000L, 15000L)

        while (true) {
            val response = getVideoResponse(source, resolvedVideo, null)
            val contentType = response.header("Content-Type").orEmpty()
            val preview = response.peekBody(512).string().trim()
            if (!preview.startsWith("#EXTM3U")) {
                cacheHlsDecision(resolvedVideo.videoUrl, false)
                val fallbackSegment = segmentProxyUrl(resolvedVideo.videoUrl, animeId, episodeIndex, videoIndex)
                return listOf(
                    "#EXTM3U",
                    "#X-MANGATAN-DIRECT",
                    "#EXT-X-VERSION:3",
                    "#EXT-X-TARGETDURATION:30",
                    "#EXTINF:30,",
                    fallbackSegment,
                    "#EXT-X-ENDLIST",
                ).joinToString("\n")
            }
            val playlist = response.body?.string().orEmpty()
            val hasNonMediaSegments = playlistHasNonMediaSegments(playlist)
            val shouldMarkAudioFix =
                hasNonMediaSegments ||
                    !contentType.contains("mpegurl", ignoreCase = true) ||
                    shouldMarkAudioFix(resolvedVideo.videoUrl)
            val cleanedPlaylist = if (hasNonMediaSegments) filterPlaylistNonMediaSegments(playlist) else playlist
            val cleanedHasMedia = playlistHasMediaSegments(cleanedPlaylist)
            val finalPlaylist = if (cleanedHasMedia) cleanedPlaylist else playlist
            if (!hasNonMediaSegments || finalPlaylist != playlist || shouldMarkAudioFix) {
                cacheHlsDecision(resolvedVideo.videoUrl, true)
                val markers = buildList {
                    if (shouldMarkAudioFix) add("#X-MANGATAN-AUDIOFIX")
                    if (hasNonMediaSegments) add("#X-MANGATAN-NONMEDIA")
                }
                val annotated =
                    if (markers.isNotEmpty() && finalPlaylist.contains("#EXTM3U")) {
                        finalPlaylist.replaceFirst("#EXTM3U", "#EXTM3U\n${markers.joinToString("\n")}")
                    } else {
                        finalPlaylist
                    }
                return rewritePlaylist(annotated, resolvedVideo.videoUrl, animeId, episodeIndex, videoIndex)
            }
            if (attempt >= retryDelays.size) {
                val fallbackSegment = segmentProxyUrl(resolvedVideo.videoUrl, animeId, episodeIndex, videoIndex)
                cacheHlsDecision(resolvedVideo.videoUrl, false)
                return listOf(
                    "#EXTM3U",
                    "#X-MANGATAN-DIRECT",
                    "#EXT-X-VERSION:3",
                    "#EXT-X-TARGETDURATION:30",
                    "#EXTINF:30,",
                    fallbackSegment,
                    "#EXT-X-ENDLIST",
                ).joinToString("\n")
            }

            val delayMs = retryDelays[attempt]
            delay(delayMs)
            videos = fetchEpisodeVideos(source, animeId, episodeIndex, forceFetch = true)
            video = videos.getOrNull(videoIndex) ?: video
            resolvedVideo = resolveVideo(source, video)
            attempt += 1
        }
    }

    suspend fun getEpisodeVideoSegment(
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
        segmentUrl: String,
        rangeHeader: String?,
    ): Response {
        val source = getCatalogueSourceOrStub(getSourceId(animeId)) as AnimeHttpSource
        val videos = fetchEpisodeVideos(source, animeId, episodeIndex)
        val video = videos.getOrNull(videoIndex) ?: throw IllegalArgumentException("Video not found")
        val resolvedVideo = resolveVideo(source, video)
        val resolvedSegmentUrl = resolveUrl(resolvedVideo.videoUrl, segmentUrl)
        val headersBuilder = Headers.Builder().apply {
            resolvedVideo.headers?.forEach { (key, value) ->
                add(key, value)
            }
            if (!rangeHeader.isNullOrBlank()) {
                add("Range", rangeHeader)
            }
        }
        if (headersBuilder.get("Referer") == null && resolvedVideo.videoUrl.startsWith("http")) {
            headersBuilder.add("Referer", resolvedVideo.videoUrl)
        }
        if (headersBuilder.get("Origin") == null) {
            val origin = resolvedVideo.videoUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
            if (origin != null) {
                headersBuilder.add("Origin", origin)
            }
        }
        val headers = headersBuilder.build()

        val request = Request.Builder().url(resolvedSegmentUrl).headers(headers).build()
        val response = source.client.newCall(request).execute()
        val contentType = response.header("Content-Type").orEmpty()
        val responseUrl = response.request.url.toString()
        val isPlaylistResponse =
            contentType.contains("mpegurl", ignoreCase = true) || responseUrl.endsWith(".m3u8", ignoreCase = true)
        if (isPlaylistResponse) {
            val preview = response.peekBody(512).string().trim()
            if (preview.startsWith("#EXTM3U")) {
                val playlist = response.body?.string().orEmpty()
                val rewritten = rewritePlaylist(playlist, responseUrl, animeId, episodeIndex, videoIndex)
                return response.newBuilder()
                    .body(rewritten.toResponseBody("application/vnd.apple.mpegurl".toMediaTypeOrNull()))
                    .build()
            }
        }
        return response
    }

    suspend fun getEpisodeSubtitleResponse(
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
        token: String,
        fallbackUrl: String? = null,
    ): Response {
        val source = getCatalogueSourceOrStub(getSourceId(animeId)) as AnimeHttpSource
        val videos =
            runCatching { fetchEpisodeVideos(source, animeId, episodeIndex) }
                .getOrElse { getCachedVideos(animeId, episodeIndex, allowExpired = true).orEmpty() }
        val video = videos.getOrNull(videoIndex)
        if (video == null) {
            logger.warn {
                "Subtitle request video not found animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex"
            }
        }
        val resolvedEntry = resolveSubtitleToken(token) ?: findSubtitleEntryByToken(videos, token)
        if (resolvedEntry == null) {
            logger.warn {
                "Subtitle token not found animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex token=$token"
            }
        }
        if (resolvedEntry != null && subtitleTokenCache[token] == null) {
            subtitleTokenCache[token] = resolvedEntry
            logger.debug {
                "Subtitle token resolved from video list animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex"
            }
        }
        val subtitleUrl =
            resolvedEntry?.url
                ?: fallbackUrl?.takeIf { it.startsWith("http") }
                ?: throw IllegalArgumentException("Subtitle not found")
        if (resolvedEntry == null && fallbackUrl != null) {
            logger.warn {
                "Subtitle proxy using fallback url animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex"
            }
        }
        if (!subtitleUrl.startsWith("http")) {
            logger.warn { "Skipping unsupported subtitle url=$subtitleUrl" }
            throw IllegalArgumentException("Unsupported subtitle url")
        }

        val headers = Headers.Builder().apply {
            resolvedEntry?.headers?.forEach { (key, value) ->
                add(key, value)
            }
            video?.headers?.forEach { (key, value) ->
                if (get(key) == null) {
                    add(key, value)
                }
            }
        }.build()

        val request = Request.Builder().url(subtitleUrl).headers(headers).build()
        return source.client.newCall(request).execute()
    }

    private fun rewritePlaylist(
        playlist: String,
        baseUrl: String,
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
    ): String {
        if (playlist.isBlank()) return playlist

        return playlist.lineSequence().map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#") -> rewriteTagUris(trimmed, baseUrl, animeId, episodeIndex, videoIndex)
                trimmed.isBlank() -> line
                else -> segmentProxyUrl(resolveUrl(baseUrl, trimmed), animeId, episodeIndex, videoIndex)
            }
        }.joinToString("\n")
    }

    private fun rewriteTagUris(
        line: String,
        baseUrl: String,
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
    ): String {
        val regex = Regex("URI=\"([^\"]+)\"")
        val matches = regex.findAll(line).toList()
        if (matches.isEmpty()) {
            return line
        }

        var updatedLine = line
        matches.forEach { match ->
            val uri = match.groupValues[1]
            val resolved = resolveUrl(baseUrl, uri)
            val proxied = segmentProxyUrl(resolved, animeId, episodeIndex, videoIndex)
            updatedLine = updatedLine.replace(uri, proxied)
        }
        return updatedLine
    }

    private fun segmentProxyUrl(
        url: String,
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
    ): String {
        val token = storeSegmentToken(url)
        return "/api/v1/anime/$animeId/episode/$episodeIndex/video/$videoIndex/segment/$token"
    }

    private fun resolveUrl(baseUrl: String, url: String): String {
        val resolved = baseUrl.toHttpUrlOrNull()?.resolve(url)?.toString()
        return resolved ?: url
    }

    private fun playlistHasNonMediaSegments(playlist: String): Boolean {
        val nonMediaPattern = Regex("\\.(jpg|jpeg|png|webp|gif|css|js|html|htm|txt|ico)(\\?.*)?$", RegexOption.IGNORE_CASE)
        return playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .any { line -> nonMediaPattern.containsMatchIn(line) }
    }

    private fun playlistHasMediaSegments(playlist: String): Boolean {
        return playlist.lineSequence()
            .map { it.trim() }
            .any { line -> line.isNotBlank() && !line.startsWith("#") }
    }

    private fun filterPlaylistNonMediaSegments(playlist: String): String {
        val nonMediaPattern = Regex("\\.(jpg|jpeg|png|webp|gif|css|js|html|htm|txt|ico)(\\?.*)?$", RegexOption.IGNORE_CASE)
        val output = mutableListOf<String>()
        var pendingExtinf: String? = null
        playlist.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                pendingExtinf = line
                return@forEach
            }
            if (trimmed.isEmpty()) {
                output.add(line)
                return@forEach
            }
            if (trimmed.startsWith("#")) {
                output.add(line)
                return@forEach
            }
            if (nonMediaPattern.containsMatchIn(trimmed)) {
                pendingExtinf = null
                return@forEach
            }
            if (pendingExtinf != null) {
                output.add(pendingExtinf!!)
                pendingExtinf = null
            }
            output.add(line)
        }
        return output.joinToString("\n")
    }

    private fun shouldMarkAudioFix(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/_v") || lower.contains("/hls-playback/")
    }


    private fun storeSegmentToken(url: String): String {
        val token = java.util.UUID.randomUUID().toString()
        segmentTokenCache[token] = SegmentCacheEntry(url, Instant.now().epochSecond)
        return token
    }

    fun resolveSegmentToken(token: String): String? {
        val entry = segmentTokenCache[token] ?: return null
        val isExpired = Instant.now().epochSecond - entry.timestampSeconds > segmentCacheTtlSeconds
        if (isExpired) {
            segmentTokenCache.remove(token)
            return null
        }
        return entry.url
    }

    private fun buildSubtitleToken(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun findSubtitleEntryByToken(
        videos: List<SourceVideo>,
        token: String,
    ): SubtitleCacheEntry? {
        videos.forEach { video ->
            val match = video.subtitleTracks.firstOrNull { track ->
                if (!track.url.startsWith("http")) {
                    return@firstOrNull false
                }
                val hashed = buildSubtitleToken(track.url)
                hashed == token || track.url.contains(token)
            }
            if (match != null) {
                return SubtitleCacheEntry(match.url, video.headers?.toList(), Instant.now().epochSecond)
            }
        }
        return null
    }

    private fun proxySubtitleTracks(
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
        video: SourceVideo,
    ): List<Track> {
        if (video.subtitleTracks.isEmpty()) {
            return emptyList()
        }

        val headers = video.headers?.toList()
        return video.subtitleTracks.mapNotNull { track ->
            if (!track.url.startsWith("http")) {
                logger.warn { "Skipping non-http subtitle url=${track.url}" }
                return@mapNotNull null
            }
            val token = storeSubtitleToken(track.url, headers)
            if (logger.isDebugEnabled()) {
                logger.debug {
                    "Subtitle token stored animeId=$animeId episodeIndex=$episodeIndex videoIndex=$videoIndex lang=${track.lang} token=$token"
                }
            }
            val encodedUrl = URLEncoder.encode(track.url, StandardCharsets.UTF_8.toString())
            Track(
                url = "/api/v1/anime/$animeId/episode/$episodeIndex/video/$videoIndex/subtitle/$token?url=$encodedUrl",
                lang = track.lang,
            )
        }
    }

    private fun storeSubtitleToken(url: String, headers: List<Pair<String, String>>?): String {
        val token = buildSubtitleToken(url)
        subtitleTokenCache[token] = SubtitleCacheEntry(url, headers, Instant.now().epochSecond)
        return token
    }

    fun resolveSubtitleToken(token: String): SubtitleCacheEntry? {
        val entry = subtitleTokenCache[token] ?: return null
        val isExpired = Instant.now().epochSecond - entry.timestampSeconds > subtitleCacheTtlSeconds
        if (isExpired) {
            subtitleTokenCache.remove(token)
            return null
        }
        return entry
    }

}
