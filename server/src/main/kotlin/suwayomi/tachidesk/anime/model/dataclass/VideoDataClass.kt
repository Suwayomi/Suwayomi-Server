package suwayomi.tachidesk.anime.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video

data class VideoDataClass(
    val videoUrl: String,
    val videoTitle: String,
    val resolution: Int?,
    val bitrate: Int?,
    val headers: List<Pair<String, String>>?,
    val preferred: Boolean,
    val isHls: Boolean,
    val subtitleTracks: List<Track>,
    val audioTracks: List<Track>,
    val timestamps: List<TimeStamp>,
    val mpvArgs: List<Pair<String, String>>,
    val ffmpegStreamArgs: List<Pair<String, String>>,
    val ffmpegVideoArgs: List<Pair<String, String>>,
    val internalData: String,
    val initialized: Boolean,
    val proxyUrl: String,
) {
    companion object {
        fun fromVideo(
            video: Video,
            proxyUrl: String,
        ): VideoDataClass =
            VideoDataClass(
                videoUrl = video.videoUrl,
                videoTitle = video.videoTitle,
                resolution = video.resolution,
                bitrate = video.bitrate,
                headers = video.headers?.toList(),
                preferred = video.preferred,
                isHls = video.videoUrl.contains(".m3u8", ignoreCase = true),
                subtitleTracks = video.subtitleTracks,
                audioTracks = video.audioTracks,
                timestamps = video.timestamps,
                mpvArgs = video.mpvArgs,
                ffmpegStreamArgs = video.ffmpegStreamArgs,
                ffmpegVideoArgs = video.ffmpegVideoArgs,
                internalData = video.internalData,
                initialized = video.initialized,
                proxyUrl = proxyUrl,
            )
    }
}
