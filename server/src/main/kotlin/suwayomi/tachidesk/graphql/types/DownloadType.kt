/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.types.DownloadState.FINISHED
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.model.Status
import java.util.concurrent.CompletableFuture
import suwayomi.tachidesk.manga.impl.download.model.DownloadState as OtherDownloadState

data class DownloadStatus(
    val state: DownloaderState,
    val queue: List<DownloadType>,
) {
    constructor(downloadStatus: DownloadStatus) : this(
        when (downloadStatus.status) {
            Status.Stopped -> DownloaderState.STOPPED
            Status.Started -> DownloaderState.STARTED
        },
        downloadStatus.queue.map { DownloadType(it) },
    )
}

class DownloadType(
    @get:GraphQLIgnore
    val chapterId: Int,
    @get:GraphQLIgnore
    val mangaId: Int,
    val state: DownloadState,
    val progress: Float,
    val tries: Int,
) : Node {
    constructor(downloadChapter: DownloadChapter) : this(
        downloadChapter.chapter.id,
        downloadChapter.mangaId,
        when (downloadChapter.state) {
            OtherDownloadState.Queued -> DownloadState.QUEUED
            OtherDownloadState.Downloading -> DownloadState.DOWNLOADING
            OtherDownloadState.Finished -> DownloadState.FINISHED
            OtherDownloadState.Error -> DownloadState.ERROR
        },
        downloadChapter.progress,
        downloadChapter.tries,
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaType> {
        val clearCache = state == FINISHED
        if (clearCache) {
            MangaType.clearCacheFor(mangaId, dataFetchingEnvironment)
        }

        return dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", mangaId)
    }

    fun chapter(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ChapterType> {
        val clearCache = state == FINISHED
        if (clearCache) {
            ChapterType.clearCacheFor(chapterId, mangaId, dataFetchingEnvironment)
        }

        return dataFetchingEnvironment.getValueFromDataLoader<Int, ChapterType>("ChapterDataLoader", chapterId)
    }
}

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    FINISHED,
    ERROR,
}

enum class DownloaderState {
    STARTED,
    STOPPED,
}

data class DownloadNodeList(
    override val nodes: List<DownloadType>,
    override val edges: List<DownloadEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class DownloadEdge(
        override val cursor: Cursor,
        override val node: DownloadType,
    ) : Edge()

    companion object {
        fun List<DownloadType>.toNodeList(): DownloadNodeList {
            return DownloadNodeList(
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
        }

        private fun List<DownloadType>.getEdges(): List<DownloadEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                DownloadEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                DownloadEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
