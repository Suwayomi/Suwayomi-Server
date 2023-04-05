/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edges
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadState
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

class DownloadType(
    val chapterId: Int,
    val chapterIndex: Int,
    val mangaId: Int,
    var state: DownloadState = DownloadState.Queued,
    var progress: Float = 0f,
    var tries: Int = 0,
    @GraphQLIgnore
    var mangaDataClass: MangaDataClass,
    @GraphQLIgnore
    var chapterDataClass: ChapterDataClass
) : Node {
    constructor(downloadChapter: DownloadChapter) : this(
        downloadChapter.chapter.id,
        downloadChapter.chapterIndex,
        downloadChapter.mangaId,
        downloadChapter.state,
        downloadChapter.progress,
        downloadChapter.tries,
        downloadChapter.manga,
        downloadChapter.chapter
    )

    fun manga(): MangaType {
        return MangaType(mangaDataClass)
    }

    fun chapter(): ChapterType {
        return ChapterType(chapterDataClass)
    }
}

data class DownloadNodeList(
    override val nodes: List<DownloadType>,
    override val edges: DownloadEdges,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class DownloadEdges(
        override val cursor: Cursor,
        override val node: DownloadType
    ) : Edges()

    companion object {
        fun List<DownloadType>.toNodeList(): DownloadNodeList {
            return DownloadNodeList(
                nodes = this,
                edges = DownloadEdges(
                    cursor = Cursor(lastIndex.toString()),
                    node = last()
                ),
                pageInfo = PageInfo(
                    hasNextPage = false,
                    hasPreviousPage = false,
                    startCursor = Cursor(0.toString()),
                    endCursor = Cursor(lastIndex.toString())
                ),
                totalCount = size
            )
        }
    }
}
