package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.table.columns.jsonObject
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar

object ChapterTable : IntIdTable() {
    val url = varchar("url", 2048)
    val name = truncatingVarchar("name", 512)
    val date_upload = long("date_upload").default(0)
    val chapter_number = float("chapter_number").default(-1f)
    val scanlator = truncatingVarchar("scanlator", 256).nullable()

    val fetchedAt = long("fetched_at").default(0)

    val sourceOrder = integer("source_order")

    /** the real url of a chapter used for the "open in WebView" feature */
    val realUrl = varchar("real_url", 2048).nullable()

    val isDownloaded = bool("is_downloaded").default(false)

    val pageCount = integer("page_count").default(-1)

    val manga = reference("manga", MangaTable, ReferenceOption.CASCADE)

    val memo = jsonObject("memo")

    init {
        // Ordinary lookup index for chapter-url resolution during chapter-list updates
        // (created by M0070). Deliberately NON-unique like MangaTable's (source, url):
        // duplicates can exist from historical data and concurrent updates; deduplication
        // stays an application-layer invariant.
        index("chapter_manga_url", false, manga, url)
    }
}

fun ChapterTable.toDataClass(chapterEntry: ResultRow) =
    ChapterDataClass(
        id = chapterEntry[id].value,
        url = chapterEntry[url],
        name = chapterEntry[name],
        uploadDate = chapterEntry[date_upload],
        chapterNumber = chapterEntry[chapter_number],
        scanlator = chapterEntry[scanlator],
        mangaId = chapterEntry[manga].value,
        index = chapterEntry[sourceOrder],
        fetchedAt = chapterEntry[fetchedAt],
        realUrl = chapterEntry[realUrl],
        // per-user columns are only present when the row came from getWithUserData;
        // getOrNull covers both a missing join and a NULL left-join result
        read = chapterEntry.getOrNull(ChapterUserTable.isRead) ?: false,
        bookmarked = chapterEntry.getOrNull(ChapterUserTable.isBookmarked) ?: false,
        lastPageRead = chapterEntry.getOrNull(ChapterUserTable.lastPageRead) ?: 0,
        lastReadAt = chapterEntry.getOrNull(ChapterUserTable.lastReadAt) ?: 0,
        downloaded = chapterEntry[isDownloaded],
        pageCount = chapterEntry[pageCount],
        lastModifiedAt = chapterEntry.getOrNull(ChapterUserTable.lastModifiedAt) ?: 0,
        version = chapterEntry.getOrNull(ChapterUserTable.version) ?: 0,
        memo = chapterEntry[memo],
    )
