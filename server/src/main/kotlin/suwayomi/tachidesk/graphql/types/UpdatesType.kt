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

class UpdatesType(
    val manga: MangaType,
    val chapter: ChapterType
) : Node {
    constructor(row: ResultRow) : this(
        manga = MangaType(row),
        chapter = ChapterType(row)
    )
}

data class UpdatesNodeList(
    override val nodes: List<UpdatesType>,
    override val edges: List<UpdatesEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class UpdatesEdge(
        override val cursor: Cursor,
        override val node: UpdatesType
    ) : Edge()

    companion object {
        fun List<UpdatesType>.toNodeList(): UpdatesNodeList {
            return UpdatesNodeList(
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

        private fun List<UpdatesType>.getEdges(): List<UpdatesEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                UpdatesEdge(
                    cursor = Cursor("0"),
                    node = first()
                ),
                UpdatesEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last()
                )
            )
        }
    }
}
