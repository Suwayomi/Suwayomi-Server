/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.anime.model.dataclass.HosterDataClass
import suwayomi.tachidesk.anime.model.dataclass.VideoDataClass

data class VideoType(
    val videoUrl: String,
    val videoTitle: String,
    val resolution: Int?,
    val bitrate: Int?,
    val headers: List<KeyValueType>?,
    val preferred: Boolean,
    val isHls: Boolean,
    val subtitleTracks: List<TrackType>,
    val audioTracks: List<TrackType>,
    val timestamps: List<TimeStampType>,
    val mpvArgs: List<KeyValueType>,
    val ffmpegStreamArgs: List<KeyValueType>,
    val ffmpegVideoArgs: List<KeyValueType>,
    val internalData: String,
    val initialized: Boolean,
    val proxyUrl: String,
) {
    constructor(dataClass: VideoDataClass) : this(
        dataClass.videoUrl,
        dataClass.videoTitle,
        dataClass.resolution,
        dataClass.bitrate,
        dataClass.headers?.map { KeyValueType(it.first, it.second) },
        dataClass.preferred,
        dataClass.isHls,
        dataClass.subtitleTracks.map { TrackType(it.url, it.lang) },
        dataClass.audioTracks.map { TrackType(it.url, it.lang) },
        dataClass.timestamps.map { timestamp ->
            TimeStampType(
                start = timestamp.start,
                end = timestamp.end,
                name = timestamp.name,
                type = VideoChapterType.valueOf(timestamp.type.name),
            )
        },
        dataClass.mpvArgs.map { KeyValueType(it.first, it.second) },
        dataClass.ffmpegStreamArgs.map { KeyValueType(it.first, it.second) },
        dataClass.ffmpegVideoArgs.map { KeyValueType(it.first, it.second) },
        dataClass.internalData,
        dataClass.initialized,
        dataClass.proxyUrl,
    )
}

data class KeyValueType(
    val key: String,
    val value: String,
)

data class TrackType(
    val url: String,
    val lang: String,
)

enum class VideoChapterType {
    Opening,
    Ending,
    Recap,
    MixedOp,
    Other,
}

data class TimeStampType(
    val start: Double,
    val end: Double,
    val name: String,
    val type: VideoChapterType,
)

data class HosterType(
    val index: Int,
    val hosterUrl: String,
    val hosterName: String,
    val lazy: Boolean,
    val videoCount: Int?,
) {
    constructor(dataClass: HosterDataClass) : this(
        dataClass.index,
        dataClass.hosterUrl,
        dataClass.hosterName,
        dataClass.lazy,
        dataClass.videoCount,
    )
}
