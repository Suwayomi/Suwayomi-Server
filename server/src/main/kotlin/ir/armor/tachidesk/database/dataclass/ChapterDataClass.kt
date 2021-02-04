package ir.armor.tachidesk.database.dataclass

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class ChapterDataClass(
    val id: Int,
    val url: String,
    val name: String,
    val date_upload: Long,
    val chapter_number: Float,
    val scanlator: String?,
    val mangaId: Int,
    val pageCount: Int? = null,
)
