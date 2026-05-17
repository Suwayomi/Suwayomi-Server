package suwayomi.tachidesk.anime.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import suwayomi.tachidesk.anime.model.table.AnimeStatus
import suwayomi.tachidesk.manga.impl.util.lang.trimAll

data class AnimeDataClass(
    val id: Int,
    val sourceId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val backgroundUrl: String? = null,
    val initialized: Boolean = false,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: String = AnimeStatus.UNKNOWN.name,
    val inLibrary: Boolean = false,
    val inLibraryAt: Long = 0,
    val updateStrategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    val fetchType: FetchType = FetchType.Episodes,
    val seasonNumber: Double = -1.0,
) {
    override fun toString(): String = "\"$title\" (id= $id) (sourceId= $sourceId)"
}

data class PagedAnimeListDataClass(
    val animeList: List<AnimeDataClass>,
    val hasNextPage: Boolean,
)

internal fun String?.toGenreList() = this?.split(",")?.trimAll().orEmpty()
