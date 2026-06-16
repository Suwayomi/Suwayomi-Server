package suwayomi.tachidesk.manga.model.dataclass

import com.fasterxml.jackson.annotation.JsonIgnore
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.Chapter.getChapterMetaMap
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable

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
    val lastModifiedAt: Long = 0,
    val version: Long = 0,
    @JsonIgnore
    val memo: JsonObject = JsonObject.EMPTY,
) {
    companion object {
        fun fromSChapter(
            sChapter: SChapter,
            id: Int,
            index: Int,
            fetchedAt: Long,
            mangaId: Int,
            realUrl: String?,
        ): ChapterDataClass =
            ChapterDataClass(
                id = id,
                url = sChapter.url,
                name = sChapter.name,
                uploadDate = sChapter.date_upload,
                chapterNumber = sChapter.chapter_number,
                scanlator = sChapter.scanlator,
                memo = sChapter.memo,
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

    @Deprecated("Remove with V1 Api")
    val chapterCount: Int by lazy {
        transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .count()
                .toInt()
        }
    }

    @Deprecated("Remove with V1 Api")
    val meta: Map<String, String> by lazy {
        getChapterMetaMap(id)
    }
}
