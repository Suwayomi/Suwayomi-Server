package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.manga.impl.util.lang.trimAll
import suwayomi.tachidesk.manga.model.table.MangaStatus

data class MangaDataClass(
    val id: Int,
    val sourceId: String,

    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,

    val initialized: Boolean = false,

    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: String = MangaStatus.UNKNOWN.name,
    val inLibrary: Boolean = false,
    val inLibraryAt: Long = 0,
    val source: SourceDataClass? = null,

    /** meta data for clients */
    val meta: Map<String, String> = emptyMap(),

    val realUrl: String? = null,
    var lastFetchedAt: Long? = 0,
    var chaptersLastFetchedAt: Long? = 0,

    val freshData: Boolean = false,
    var unreadCount: Int? = null,
    var downloadCount: Int? = null,
    var chapterCount: Int? = null
)

data class PagedMangaListDataClass(
    val mangaList: List<MangaDataClass>,
    val hasNextPage: Boolean
)

internal fun String?.toGenreList() = this?.split(",")?.trimAll().orEmpty()
