/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.types.MangaNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.util.concurrent.CompletableFuture

class CategoryType(
    val id: Int,
    val order: Int,
    val name: String,
    val default: Boolean,
    val includeInUpdate: IncludeOrExclude,
    val includeInDownload: IncludeOrExclude,
    val isDefaultCategory: Boolean = id == 0,
    val contentType: SourceContentType = SourceContentType.MANGA,
) : Node {
    constructor(
        row: ResultRow,
        contentType: SourceContentType? = null,
    ) : this(
        row[CategoryTable.id].value,
        row[CategoryTable.order],
        row[CategoryTable.name],
        row[CategoryTable.isDefault],
        IncludeOrExclude.fromValue(row[CategoryTable.includeInUpdate]),
        IncludeOrExclude.fromValue(row[CategoryTable.includeInDownload]),
        contentType = contentType ?: row[CategoryTable.contentType],
    )

    constructor(dataClass: CategoryDataClass) : this(
        dataClass.id,
        dataClass.order,
        dataClass.name,
        dataClass.default,
        dataClass.includeInUpdate,
        dataClass.includeInDownload,
        contentType = dataClass.contentType,
    )

    fun mangas(
        dataFetchingEnvironment: DataFetchingEnvironment,
        contentType: SourceContentType? = this.contentType,
        inLibrary: Boolean? = null,
    ): CompletableFuture<MangaNodeList> {
        val future =
            dataFetchingEnvironment.getValueFromDataLoader<Int, MangaNodeList>("MangaForCategoryDataLoader", id)
        return if (contentType == null && inLibrary == null) {
            future
        } else {
            future.thenApply { list ->
                list.nodes
                    .filter { item ->
                        (contentType == null || item.contentType == contentType) &&
                            (inLibrary == null || item.inLibrary == inLibrary)
                    }.toNodeList()
            }
        }
    }

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<CategoryMetaType>> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, List<CategoryMetaType>>("CategoryMetaDataLoader", id)
}

data class CategoryNodeList(
    override val nodes: List<CategoryType>,
    override val edges: List<CategoryEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class CategoryEdge(
        override val cursor: Cursor,
        override val node: CategoryType,
    ) : Edge()

    companion object {
        fun List<CategoryType>.toNodeList(): CategoryNodeList =
            CategoryNodeList(
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

        private fun List<CategoryType>.getEdges(): List<CategoryEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                CategoryEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                CategoryEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
