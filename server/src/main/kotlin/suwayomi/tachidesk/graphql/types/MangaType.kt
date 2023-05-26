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
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.time.Instant
import java.util.concurrent.CompletableFuture

class MangaType(
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
    val status: MangaStatus,
    val inLibrary: Boolean,
    val inLibraryAt: Long,
    val realUrl: String?,
    var lastFetchedAt: Long?, // todo
    var chaptersLastFetchedAt: Long? // todo
) : Node {
    constructor(row: ResultRow) : this(
        row[MangaTable.id].value,
        row[MangaTable.sourceReference],
        row[MangaTable.url],
        row[MangaTable.title],
        row[MangaTable.thumbnail_url],
        row[MangaTable.initialized],
        row[MangaTable.artist],
        row[MangaTable.author],
        row[MangaTable.description],
        row[MangaTable.genre].toGenreList(),
        MangaStatus.valueOf(row[MangaTable.status]),
        row[MangaTable.inLibrary],
        row[MangaTable.inLibraryAt],
        row[MangaTable.realUrl],
        row[MangaTable.lastFetchedAt],
        row[MangaTable.chaptersLastFetchedAt]
    )

    constructor(dataClass: MangaDataClass) : this(
        dataClass.id,
        dataClass.sourceId.toLong(),
        dataClass.url,
        dataClass.title,
        dataClass.thumbnailUrl,
        dataClass.initialized,
        dataClass.artist,
        dataClass.author,
        dataClass.description,
        dataClass.genre,
        MangaStatus.valueOf(dataClass.status),
        dataClass.inLibrary,
        dataClass.inLibraryAt,
        dataClass.realUrl,
        dataClass.lastFetchedAt,
        dataClass.chaptersLastFetchedAt
    )

    fun chapters(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, ChapterNodeList>("ChaptersForMangaDataLoader", id)
    }

    fun age(): Long? {
        if (lastFetchedAt == null) return null
        return Instant.now().epochSecond.minus(lastFetchedAt!!)
    }

    fun chaptersAge(): Long? {
        if (chaptersLastFetchedAt == null) return null

        return Instant.now().epochSecond.minus(chaptersLastFetchedAt!!)
    }

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<MangaMetaType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, List<MangaMetaType>>("MangaMetaDataLoader", id)
    }

    fun categories(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<CategoryNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, CategoryNodeList>("CategoriesForMangaDataLoader", id)
    }

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<SourceType?> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, SourceType?>("SourceDataLoader", sourceId)
    }
}

data class MangaNodeList(
    override val nodes: List<MangaType>,
    override val edges: List<MangaEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class MangaEdge(
        override val cursor: Cursor,
        override val node: MangaType
    ) : Edge()

    companion object {
        fun List<MangaType>.toNodeList(): MangaNodeList {
            return MangaNodeList(
                nodes = this,
                edges = getEdges(),
                pageInfo = PageInfo(
                    hasNextPage = false,
                    hasPreviousPage = false,
                    startCursor = Cursor(0.toString()),
                    endCursor = Cursor(lastIndex.toString())
                ),
                totalCount = size
            )
        }

        private fun List<MangaType>.getEdges(): List<MangaEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                MangaEdge(
                    cursor = Cursor("0"),
                    node = first()
                ),
                MangaEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last()
                )
            )
        }
    }
}
