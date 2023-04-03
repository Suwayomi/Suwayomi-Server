/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import org.jetbrains.exposed.sql.ResultRow

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
    override val edges: UpdatesEdges,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class UpdatesEdges(
        override val cursor: Cursor,
        override val node: UpdatesType?
    ) : Edges()

    companion object {
        fun List<UpdatesType>.toNodeList(): UpdatesNodeList {
            return UpdatesNodeList(
                nodes = this,
                edges = UpdatesEdges(
                    cursor = lastIndex,
                    node = lastOrNull()
                ),
                pageInfo = PageInfo(
                    hasNextPage = false,
                    hasPreviousPage = false,
                    startCursor = 0,
                    endCursor = lastIndex
                ),
                totalCount = size
            )
        }
    }
}
