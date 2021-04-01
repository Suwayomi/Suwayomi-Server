package ir.armor.tachidesk.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class ChapterDataClass(
    val url: String,
    val name: String,
    val date_upload: Long,
    val chapter_number: Float,
    val scanlator: String?,
    val mangaId: Int,

    /** this chapter's index */
    val chapterIndex: Int? = null,

    /** total chapter count, used to calculate if there's a next and prev chapter */
    val chapterCount: Int? = null,

    /** used to construct pages in the front-end */
    val pageCount: Int? = null,
)
