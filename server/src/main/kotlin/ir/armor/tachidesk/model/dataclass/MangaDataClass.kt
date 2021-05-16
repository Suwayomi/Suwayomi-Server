package ir.armor.tachidesk.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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
    val genre: String? = null,
    val status: String = MangaStatus.UNKNOWN.name,
    val inLibrary: Boolean = false,
    val source: SourceDataClass? = null,

    val freshData: Boolean = false
)

data class PagedMangaListDataClass(
    val mangaList: List<MangaDataClass>,
    val hasNextPage: Boolean
)

enum class MangaStatus(val status: Int) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3);

    companion object {
        fun valueOf(value: Int): MangaStatus = values().find { it.status == value } ?: UNKNOWN
    }
}
