package suwayomi.anime.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.anime.model.table.AnimeStatus

data class AnimeDataClass(
    val id: Int,
    val sourceId: String,

    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,

    val initialized: Boolean = false,

    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: String = AnimeStatus.UNKNOWN.name,
    val inLibrary: Boolean = false,
    val source: AnimeSourceDataClass? = null,

    val freshData: Boolean = false
)

data class PagedAnimeListDataClass(
    val mangaList: List<AnimeDataClass>,
    val hasNextPage: Boolean
)
