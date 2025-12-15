/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.IReaderChapterTable
import suwayomi.tachidesk.manga.model.table.IReaderNovelTable
import java.util.concurrent.CompletableFuture

@GraphQLDescription("Novel information from IReader source")
class IReaderNovelType(
    val id: Int,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val initialized: Boolean,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val status: Long,
    val inLibrary: Boolean,
    val inLibraryAt: Long,
    val lastFetchedAt: Long?,
    val chaptersLastFetchedAt: Long?,
) : Node {
    constructor(row: ResultRow) : this(
        row[IReaderNovelTable.id].value,
        row[IReaderNovelTable.sourceReference],
        row[IReaderNovelTable.url],
        row[IReaderNovelTable.title],
        row[IReaderNovelTable.thumbnailUrl],
        row[IReaderNovelTable.initialized],
        row[IReaderNovelTable.artist],
        row[IReaderNovelTable.author],
        row[IReaderNovelTable.description],
        row[IReaderNovelTable.genre].toGenreList(),
        row[IReaderNovelTable.status],
        row[IReaderNovelTable.inLibrary],
        row[IReaderNovelTable.inLibraryAt],
        row[IReaderNovelTable.lastFetchedAt],
        row[IReaderNovelTable.chaptersLastFetchedAt],
    )

    constructor(mangaInfo: MangaInfo, sourceId: Long = 0, id: Int = 0) : this(
        id = id,
        sourceId = sourceId,
        url = mangaInfo.key,
        title = mangaInfo.title,
        thumbnailUrl = mangaInfo.cover,
        initialized = true,
        artist = mangaInfo.artist,
        author = mangaInfo.author,
        description = mangaInfo.description,
        genre = mangaInfo.genres,
        status = mangaInfo.status,
        inLibrary = false,
        inLibraryAt = 0,
        lastFetchedAt = null,
        chaptersLastFetchedAt = null,
    )

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<IReaderSourceType?> =
        dataFetchingEnvironment.getValueFromDataLoader<Long, IReaderSourceType?>("IReaderSourceDataLoader", sourceId)
}

data class IReaderNovelNodeList(
    override val nodes: List<IReaderNovelType>,
    override val edges: List<IReaderNovelEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class IReaderNovelEdge(
        override val cursor: Cursor,
        override val node: IReaderNovelType,
    ) : Edge()

    companion object {
        fun List<IReaderNovelType>.toNodeList(): IReaderNovelNodeList =
            IReaderNovelNodeList(
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

        private fun List<IReaderNovelType>.getEdges(): List<IReaderNovelEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                IReaderNovelEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                IReaderNovelEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}

@GraphQLDescription("Chapter information from IReader source")
data class IReaderChapterType(
    val id: Int,
    val name: String,
    val url: String,
    val dateUpload: Long,
    val chapterNumber: Float,
    val scanlator: String?,
    val novelId: Int,
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
    val fetchedAt: Long,
    val sourceOrder: Int,
) {
    constructor(row: ResultRow) : this(
        id = row[IReaderChapterTable.id].value,
        name = row[IReaderChapterTable.name],
        url = row[IReaderChapterTable.url],
        dateUpload = row[IReaderChapterTable.dateUpload],
        chapterNumber = row[IReaderChapterTable.chapterNumber],
        scanlator = row[IReaderChapterTable.scanlator],
        novelId = row[IReaderChapterTable.novel].value,
        isRead = row[IReaderChapterTable.isRead],
        isBookmarked = row[IReaderChapterTable.isBookmarked],
        lastPageRead = row[IReaderChapterTable.lastPageRead],
        lastReadAt = row[IReaderChapterTable.lastReadAt],
        fetchedAt = row[IReaderChapterTable.fetchedAt],
        sourceOrder = row[IReaderChapterTable.sourceOrder],
    )

    constructor(chapterInfo: ChapterInfo, novelId: Int = 0, id: Int = 0, sourceOrder: Int = 0) : this(
        id = id,
        name = chapterInfo.name,
        url = chapterInfo.key,
        dateUpload = chapterInfo.dateUpload,
        chapterNumber = chapterInfo.number,
        scanlator = chapterInfo.scanlator,
        novelId = novelId,
        isRead = false,
        isBookmarked = false,
        lastPageRead = 0,
        lastReadAt = 0,
        fetchedAt = 0,
        sourceOrder = sourceOrder,
    )
}

@GraphQLDescription("Page of novels from IReader source")
data class IReaderNovelsPageType(
    val novels: List<IReaderNovelType>,
    val hasNextPage: Boolean,
) {
    constructor(pageInfo: MangasPageInfo, sourceId: Long = 0) : this(
        novels = pageInfo.mangas.map { IReaderNovelType(it, sourceId) },
        hasNextPage = pageInfo.hasNextPage,
    )
}

@GraphQLDescription("Chapter content page")
data class IReaderPageType(
    val text: String,
) {
    companion object {
        fun fromPage(page: Page): IReaderPageType =
            when (page) {
                is Text -> IReaderPageType(page.text)
                else -> IReaderPageType("")
            }
    }
}
