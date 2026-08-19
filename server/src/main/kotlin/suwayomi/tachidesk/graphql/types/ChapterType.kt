/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
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
    val isDownloaded: Boolean,
    val pageCount: Int,
    @get:GraphQLIgnore
    val lastReadAt: Long? = null,
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
        row[ChapterTable.isDownloaded],
        row[ChapterTable.pageCount],
        row.getOrNull(ChapterUserTable.lastReadAt),
    )

    constructor(dataClass: ChapterDataClass) : this(
        dataClass.id,
        dataClass.url,
        dataClass.name,
        dataClass.uploadDate,
        dataClass.chapterNumber,
        dataClass.scanlator,
        dataClass.mangaId,
        dataClass.index,
        dataClass.realUrl,
        dataClass.fetchedAt,
        dataClass.downloaded,
        dataClass.pageCount,
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", mangaId)

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<ChapterMetaType>> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, List<ChapterMetaType>>("ChapterMetaDataLoader", id)

    fun user(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterUserType?> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, ChapterUserType?>("ChapterUserForChapterDataLoader", id)

    @GraphQLDeprecated("Use user.isRead instead")
    fun isRead(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<Boolean> =
        user(dataFetchingEnvironment).thenApply {
            it?.isRead == true
        }

    @GraphQLDeprecated("Use user.isBookmarked instead")
    fun isBookmarked(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<Boolean> =
        user(dataFetchingEnvironment).thenApply {
            it?.isBookmarked == true
        }

    @GraphQLDeprecated("Use user.lastPageRead instead")
    fun lastPageRead(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<Int> =
        user(dataFetchingEnvironment).thenApply {
            it?.lastPageRead ?: 0
        }

    @GraphQLDeprecated("Use user.lastReadAt instead")
    fun lastReadAt(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<Long> =
        user(dataFetchingEnvironment).thenApply {
            it?.lastReadAt ?: 0
        }
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
