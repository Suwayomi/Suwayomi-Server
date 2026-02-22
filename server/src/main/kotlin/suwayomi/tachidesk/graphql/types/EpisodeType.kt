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
import suwayomi.tachidesk.anime.model.dataclass.EpisodeDataClass
import suwayomi.tachidesk.anime.model.table.EpisodeTable
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import java.util.concurrent.CompletableFuture

class EpisodeType(
    val id: Int,
    val url: String,
    val name: String,
    val uploadDate: Long,
    val episodeNumber: Float,
    val fillermark: Boolean,
    val scanlator: String?,
    val summary: String?,
    val previewUrl: String?,
    val animeId: Int,
    val sourceOrder: Int,
    val fetchedAt: Long,
    val realUrl: String?,
    val isRead: Boolean,
    val isDownloaded: Boolean,
    val lastReadAt: Long,
) : Node {
    constructor(row: ResultRow) : this(
        row[EpisodeTable.id].value,
        row[EpisodeTable.url],
        row[EpisodeTable.name],
        row[EpisodeTable.date_upload],
        row[EpisodeTable.episode_number],
        row[EpisodeTable.fillermark],
        row[EpisodeTable.scanlator],
        row[EpisodeTable.summary],
        row[EpisodeTable.preview_url],
        row[EpisodeTable.anime].value,
        row[EpisodeTable.sourceOrder],
        row[EpisodeTable.fetchedAt],
        row[EpisodeTable.realUrl],
        row[EpisodeTable.isRead],
        row[EpisodeTable.isDownloaded],
        row[EpisodeTable.lastReadAt],
    )

    constructor(dataClass: EpisodeDataClass) : this(
        dataClass.id,
        dataClass.url,
        dataClass.name,
        dataClass.uploadDate,
        dataClass.episodeNumber,
        dataClass.fillermark,
        dataClass.scanlator,
        dataClass.summary,
        dataClass.previewUrl,
        dataClass.animeId,
        dataClass.index,
        dataClass.fetchedAt,
        dataClass.realUrl,
        dataClass.isRead,
        dataClass.isDownloaded,
        dataClass.lastReadAt,
    )

    fun anime(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<AnimeType> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, AnimeType>("AnimeDataLoader", animeId)
}

data class EpisodeNodeList(
    override val nodes: List<EpisodeType>,
    override val edges: List<EpisodeEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class EpisodeEdge(
        override val cursor: Cursor,
        override val node: EpisodeType,
    ) : Edge()

    companion object {
        fun List<EpisodeType>.toNodeList(): EpisodeNodeList =
            EpisodeNodeList(
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

        private fun List<EpisodeType>.getEdges(): List<EpisodeEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                EpisodeEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                EpisodeEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
