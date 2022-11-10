/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.util.concurrent.CompletableFuture

class ChapterType(
    val id: Int,
    val url: String,
    val name: String,
    val uploadDate: Long,
    val chapterNumber: Float,
    val scanlator: String?,
    val mangaId: Int,
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
    val sourceOrder: Int,
    val fetchedAt: Long,
    val isDownloaded: Boolean,
    val pageCount: Int
//    val chapterCount: Int?,
//    val meta: Map<String, String> = emptyMap()
) {
    constructor(row: ResultRow) : this(
        row[ChapterTable.id].value,
        row[ChapterTable.url],
        row[ChapterTable.name],
        row[ChapterTable.date_upload],
        row[ChapterTable.chapter_number],
        row[ChapterTable.scanlator],
        row[ChapterTable.manga].value,
        row[ChapterTable.isRead],
        row[ChapterTable.isBookmarked],
        row[ChapterTable.lastPageRead],
        row[ChapterTable.lastReadAt],
        row[ChapterTable.sourceOrder],
        row[ChapterTable.fetchedAt],
        row[ChapterTable.isDownloaded],
        row[ChapterTable.pageCount]
//        transaction { ChapterTable.select { manga eq chapterEntry[manga].value }.count().toInt() },
//        Chapter.getChapterMetaMap(chapterEntry[id])
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", mangaId)
    }

//    fun chapters(): List<String> {
//        return listOf("Foo", "Bar", "Baz")
//    }
}
