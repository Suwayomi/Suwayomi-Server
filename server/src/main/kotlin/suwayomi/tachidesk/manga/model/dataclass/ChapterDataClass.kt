package suwayomi.tachidesk.manga.model.dataclass

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

    /** last read page, zero means not read/no data */
    val lastReadAt: Long,

    // TODO(v0.6.0): rename to sourceOrder
    /** this chapter's index, starts with 1 */
    val index: Int,

    /** the date we fist saw this chapter*/
    val fetchedAt: Long,

    /** is chapter downloaded */
    val downloaded: Boolean,

    /** used to construct pages in the front-end */
    val pageCount: Int = -1,

    /** total chapter count, used to calculate if there's a next and prev chapter */
    val chapterCount: Int? = null,

    /** used to store client specific values */
    val meta: Map<String, String> = emptyMap(),
)
