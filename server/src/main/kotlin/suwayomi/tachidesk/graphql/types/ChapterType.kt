/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import java.util.concurrent.CompletableFuture

data class SyncConflictInfoType(
    val deviceName: String,
    val remotePage: Int,
)

class ChapterType(
    val id: Int,
    val url: String,
    val name: String,
    val uploadDate: Long,
    val chapterNumber: Float,
    val scanlator: String?,
    val mangaId: Int,
    val sourceOrder: Int,
    val realUrl: String?,
    val fetchedAt: Long,
    val pageCount: Int,
    @GraphQLDeprecated("Use user.isRead instead")
    val isRead: Boolean,
    @GraphQLDeprecated("Use user.isBookmarked instead")
    val isBookmarked: Boolean,
    @GraphQLDeprecated("Use user.lastPageRead instead")
    val lastPageRead: Int,
    @GraphQLDeprecated("Use user.lastReadAt instead")
    val lastReadAt: Long,
    @GraphQLDeprecated("Use user.isDownloaded instead")
    val isDownloaded: Boolean,
) : Node {
    companion object {
        fun clearCacheFor(
            chapterId: Int,
            mangaId: Int,
            dataFetchingEnvironment: DataFetchingEnvironment,
        ) {
            dataFetchingEnvironment.getDataLoader<Int, ChapterType>("ChapterDataLoader")?.clear(chapterId)
            dataFetchingEnvironment.getDataLoader<Int, ChapterNodeList>("ChaptersForMangaDataLoader")?.clear(mangaId)
            dataFetchingEnvironment.getDataLoader<Int, Int>("DownloadedChapterCountForMangaDataLoader")?.clear(mangaId)
            dataFetchingEnvironment.getDataLoader<Int, ChapterType>("LastReadChapterForMangaDataLoader")?.clear(mangaId)
            dataFetchingEnvironment.getDataLoader<Int, ChapterUserType>("ChapterUserForChapterDataLoader")?.clear(chapterId)
        }
    }

    constructor(row: ResultRow) : this(
        row[ChapterTable.id].value,
        row[ChapterTable.url],
        row[ChapterTable.name],
        row[ChapterTable.date_upload],
        row[ChapterTable.chapter_number],
        row[ChapterTable.scanlator],
        row[ChapterTable.manga].value,
        row[ChapterTable.sourceOrder],
        row[ChapterTable.realUrl],
        row[ChapterTable.fetchedAt],
        row[ChapterTable.pageCount],
        row.getOrNull(ChapterUserTable.isRead) ?: false,
        row.getOrNull(ChapterUserTable.isBookmarked) ?: false,
        row.getOrNull(ChapterUserTable.lastPageRead) ?: 0,
        row.getOrNull(ChapterUserTable.lastReadAt) ?: 0,
        row.getOrNull(ChapterUserTable.isDownloaded) ?: false,
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", mangaId)

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<ChapterMetaType>> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, List<ChapterMetaType>>("ChapterMetaDataLoader", id)

    fun user(dataFetchingEnvironment: DataFetchingEnvironment): ChapterUserType =
        ChapterUserType(
            chapterId = id,
            isRead = isRead,
            isBookmarked = isBookmarked,
            lastPageRead = lastPageRead,
            lastReadAt = lastReadAt,
            isDownloaded = isDownloaded,
        )
}

data class ChapterNodeList(
    override val nodes: List<ChapterType>,
    override val edges: List<ChapterEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class ChapterEdge(
        override val cursor: Cursor,
        override val node: ChapterType,
    ) : Edge()

    companion object {
        fun List<ChapterType>.toNodeList(): ChapterNodeList =
            ChapterNodeList(
                nodes = this,
                edges = getEdges(),
                pageInfo =
                    PageInfo(
                        hasNextPage = false,
                        hasPreviousPage = false,
                        startCursor = Cursor(0.toString()),
                        endCursor = Cursor(lastIndex.toString()),
                    ),
                totalCount = size,
            )

        private fun List<ChapterType>.getEdges(): List<ChapterEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                ChapterEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                ChapterEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
