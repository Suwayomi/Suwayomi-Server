package suwayomi.tachidesk.anime.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.SEpisode

data class EpisodeDataClass(
    val id: Int,
    val url: String,
    val name: String,
    val uploadDate: Long,
    val episodeNumber: Float,
    val fillermark: Boolean,
    val scanlator: String?,
    val summary: String?,
    val previewUrl: String?,
    val animeId: Int,
    /** this episode's index, starts with 1 */
    val index: Int,
    /** the date we fist saw this episode */
    val fetchedAt: Long,
    /** the website url of this episode */
    val realUrl: String? = null,
    val isRead: Boolean = false,
    val isDownloaded: Boolean = false,
    val lastReadAt: Long = 0,
) {
    companion object {
        fun fromSEpisode(
            sEpisode: SEpisode,
            id: Int,
            index: Int,
            fetchedAt: Long,
            animeId: Int,
            realUrl: String?,
            isRead: Boolean,
            isDownloaded: Boolean,
            lastReadAt: Long,
        ): EpisodeDataClass =
            EpisodeDataClass(
                id = id,
                url = sEpisode.url,
                name = sEpisode.name,
                uploadDate = sEpisode.date_upload,
                episodeNumber = sEpisode.episode_number,
                fillermark = sEpisode.fillermark,
                scanlator = sEpisode.scanlator,
                summary = sEpisode.summary,
                previewUrl = sEpisode.preview_url,
                index = index,
                fetchedAt = fetchedAt,
                realUrl = realUrl,
                animeId = animeId,
                isRead = isRead,
                isDownloaded = isDownloaded,
                lastReadAt = lastReadAt,
            )
    }

    fun toSEpisode(): SEpisode =
        SEpisode.create().also { sEpisode ->
            sEpisode.url = url
            sEpisode.name = name
            sEpisode.date_upload = uploadDate
            sEpisode.episode_number = episodeNumber
            sEpisode.fillermark = fillermark
            sEpisode.scanlator = scanlator
            sEpisode.summary = summary
            sEpisode.preview_url = previewUrl
        }
}
