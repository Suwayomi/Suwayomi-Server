package suwayomi.tachidesk.anime.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class EpisodeDataClass(
    val url: String,
    val name: String,
    val uploadDate: Long,
    val episodeNumber: Float,
    val scanlator: String?,
    val animeId: Int,

    /** chapter is read */
    val read: Boolean,

    /** chapter is bookmarked */
    val bookmarked: Boolean,

    /** last read page, zero means not read/no data */
    val lastPageRead: Int,

    /** this chapter's index, starts with 1 */
    val index: Int,

    /** total episode count, used to calculate if there's a next and prev episode */
    val episodeCount: Int? = null,

    /** used to construct pages in the front-end */
    val linkUrl: String? = null,
)
