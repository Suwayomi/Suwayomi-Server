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
    val name: String,
    val key: String,
    val dateUpload: Long,
    val number: Float,
    val scanlator: String,
) {
    constructor(chapterInfo: ChapterInfo) : this(
        name = chapterInfo.name,
        key = chapterInfo.key,
        dateUpload = chapterInfo.dateUpload,
        number = chapterInfo.number,
        scanlator = chapterInfo.scanlator,
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
