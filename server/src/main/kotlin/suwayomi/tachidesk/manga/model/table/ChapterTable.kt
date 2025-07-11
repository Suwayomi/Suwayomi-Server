package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.Chapter.getChapterMetaMap
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar

object ChapterTable : IntIdTable() {
    val url = varchar("url", 4096)
    val name = truncatingVarchar("name", 512)
    val date_upload = long("date_upload").default(0)
    val chapter_number = float("chapter_number").default(-1f)
    val scanlator = truncatingVarchar("scanlator", 256).nullable()

    val isRead = bool("read").default(false)
    val isBookmarked = bool("bookmark").default(false)
    val lastPageRead = integer("last_page_read").default(0)
    val lastReadAt = long("last_read_at").default(0)
    val fetchedAt = long("fetched_at").default(0)

    val sourceOrder = integer("source_order")

    /** the real url of a chapter used for the "open in WebView" feature */
    val realUrl = varchar("real_url", 4096).nullable()

    val isDownloaded = bool("is_downloaded").default(false)

    val pageCount = integer("page_count").default(-1)

    val manga = reference("manga", MangaTable, ReferenceOption.CASCADE)
}

fun ChapterTable.toDataClass(
    chapterEntry: ResultRow,
    includeChapterCount: Boolean = true,
    includeChapterMeta: Boolean = true,
) = ChapterDataClass(
    id = chapterEntry[id].value,
    url = chapterEntry[url],
    name = chapterEntry[name],
    uploadDate = chapterEntry[date_upload],
    chapterNumber = chapterEntry[chapter_number],
    scanlator = chapterEntry[scanlator],
    mangaId = chapterEntry[manga].value,
    read = chapterEntry[isRead],
    bookmarked = chapterEntry[isBookmarked],
    lastPageRead = chapterEntry[lastPageRead],
    lastReadAt = chapterEntry[lastReadAt],
    index = chapterEntry[sourceOrder],
    fetchedAt = chapterEntry[fetchedAt],
    realUrl = chapterEntry[realUrl],
    downloaded = chapterEntry[isDownloaded],
    pageCount = chapterEntry[pageCount],
    chapterCount =
        if (includeChapterCount) {
            transaction {
                ChapterTable
                    .selectAll()
                    .where { manga eq chapterEntry[manga].value }
                    .count()
                    .toInt()
            }
        } else {
            null
        },
    meta =
        if (includeChapterMeta) {
            getChapterMetaMap(chapterEntry[id])
        } else {
            emptyMap()
        },
)
