package suwayomi.tachidesk.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class ChapterDataClass(
    val url: String,
    val name: String,
    val uploadDate: Long,
    val chapterNumber: Float,
    val scanlator: String?,
    val mangaId: Int,

    /** chapter is read */
    val read: Boolean,

    /** chapter is bookmarked */
    val bookmarked: Boolean,

    /** last read page, zero means not read/no data */
    val lastPageRead: Int,

    /** last read page offset, zero means no offset data */
    val lastPageReadOffset: Int,

    /** this chapter's index, starts with 1 */
    val index: Int,

    /** total chapter count, used to calculate if there's a next and prev chapter */
    val chapterCount: Int? = null,

    /** used to construct pages in the front-end */
    val pageCount: Int? = null,
)
