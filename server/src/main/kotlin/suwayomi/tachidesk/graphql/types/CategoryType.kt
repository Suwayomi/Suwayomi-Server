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
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.util.concurrent.CompletableFuture

class CategoryType(
    val id: Int,
    val order: Int,
    val name: String,
    val default: Boolean
) : Node {
    constructor(row: ResultRow) : this(
        row[CategoryTable.id].value,
        row[CategoryTable.order],
        row[CategoryTable.name],
        row[CategoryTable.isDefault]
    )

    fun mangas(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, MangaNodeList>("MangaForCategoryDataLoader", id)
    }

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<CategoryMetaType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, List<CategoryMetaType>>("CategoryMetaDataLoader", id)
    }
}

data class CategoryNodeList(
    override val nodes: List<CategoryType>,
    override val edges: List<CategoryEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class CategoryEdge(
        override val cursor: Cursor,
        override val node: CategoryType
    ) : Edge()

    companion object {
        fun List<CategoryType>.toNodeList(): CategoryNodeList {
            return CategoryNodeList(
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

        private fun List<CategoryType>.getEdges(): List<CategoryEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                CategoryEdge(
                    cursor = Cursor("0"),
                    node = first()
                ),
                CategoryEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last()
                )
            )
        }
    }
}
