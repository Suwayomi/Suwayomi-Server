package suwayomi.tachidesk.manga.model.dataclass

import eu.kanade.tachiyomi.source.model.SChapter

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class ChapterDataClass(
    val id: Int,
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
    /** the website url of this chapter*/
    val realUrl: String? = null,
    /** is chapter downloaded */
    val downloaded: Boolean,
    /** used to construct pages in the front-end */
    val pageCount: Int = -1,
    /** total chapter count, used to calculate if there's a next and prev chapter */
    val chapterCount: Int? = null,
    /** used to store client specific values */
    val meta: Map<String, String> = emptyMap(),
) {
    companion object {
        fun fromSChapter(
            sChapter: SChapter,
            id: Int,
            index: Int,
            fetchedAt: Long,
            mangaId: Int,
            realUrl: String?,
        ): ChapterDataClass {
            return ChapterDataClass(
                id = id,
                url = sChapter.url,
                name = sChapter.name,
                uploadDate = sChapter.date_upload,
                chapterNumber = sChapter.chapter_number,
                scanlator = sChapter.scanlator,
                index = index,
                fetchedAt = fetchedAt,
                realUrl = realUrl,
                mangaId = mangaId,
                read = false,
                bookmarked = false,
                lastPageRead = 0,
                lastReadAt = 0,
                downloaded = false,
            )
        }
    }
}
