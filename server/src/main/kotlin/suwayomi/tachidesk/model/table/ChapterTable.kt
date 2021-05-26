package suwayomi.tachidesk.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.model.dataclass.ChapterDataClass

object ChapterTable : IntIdTable() {
    val url = varchar("url", 2048)
    val name = varchar("name", 512)
    val date_upload = long("date_upload").default(0)
    val chapter_number = float("chapter_number").default(-1f)
    val scanlator = varchar("scanlator", 128).nullable()

    val isRead = bool("read").default(false)
    val isBookmarked = bool("bookmark").default(false)
    val lastPageRead = integer("last_page_read").default(0)

    // index is reserved by a function
    val chapterIndex = integer("index")

    val manga = reference("manga", MangaTable)
}

fun ChapterTable.toDataClass(chapterEntry: ResultRow) =
    ChapterDataClass(
        chapterEntry[url],
        chapterEntry[name],
        chapterEntry[date_upload],
        chapterEntry[chapter_number],
        chapterEntry[scanlator],
        chapterEntry[manga].value,
        chapterEntry[isRead],
        chapterEntry[isBookmarked],
        chapterEntry[lastPageRead],
        chapterEntry[chapterIndex],
    )
