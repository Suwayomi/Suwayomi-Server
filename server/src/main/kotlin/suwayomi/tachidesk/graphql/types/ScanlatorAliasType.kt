/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.model.dataclass.ScanlatorAliasDataClass
import suwayomi.tachidesk.manga.model.table.ScanlatorAliasTable

class ScanlatorAliasType(
    val id: Int,
    val scanlator: String,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
) : Node {
    constructor(row: ResultRow) : this(
        row[ScanlatorAliasTable.id].value,
        row[ScanlatorAliasTable.scanlator],
        row[ScanlatorAliasTable.displayName],
        row[ScanlatorAliasTable.createdAt],
        row[ScanlatorAliasTable.updatedAt],
    )

    constructor(dataClass: ScanlatorAliasDataClass) : this(
        dataClass.id,
        dataClass.scanlator,
        dataClass.displayName,
        dataClass.createdAt,
        dataClass.updatedAt,
    )
}

data class ScanlatorAliasNodeList(
    override val nodes: List<ScanlatorAliasType>,
    override val edges: List<ScanlatorAliasEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class ScanlatorAliasEdge(
        override val cursor: Cursor,
        override val node: ScanlatorAliasType,
    ) : Edge()

    companion object {
        fun List<ScanlatorAliasType>.toNodeList(): ScanlatorAliasNodeList =
            ScanlatorAliasNodeList(
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

        private fun List<ScanlatorAliasType>.getEdges(): List<ScanlatorAliasEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                ScanlatorAliasEdge(cursor = Cursor("0"), node = first()),
                ScanlatorAliasEdge(cursor = Cursor(lastIndex.toString()), node = last()),
            )
        }
    }
}
