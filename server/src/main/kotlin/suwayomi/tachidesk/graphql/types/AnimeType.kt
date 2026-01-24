/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.anime.model.dataclass.AnimeDataClass
import suwayomi.tachidesk.anime.model.dataclass.toGenreList
import suwayomi.tachidesk.anime.model.table.AnimeStatus
import suwayomi.tachidesk.anime.impl.AnimeList
import suwayomi.tachidesk.anime.model.table.AnimeTable
import java.util.concurrent.CompletableFuture

class AnimeType(
    val id: Int,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val backgroundUrl: String?,
    val initialized: Boolean,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val status: AnimeStatus,
    val inLibrary: Boolean,
    val inLibraryAt: Long,
    val updateStrategy: AnimeUpdateStrategy,
    val fetchType: FetchType,
    val seasonNumber: Double,
) : Node {
    constructor(row: ResultRow) : this(
        row[AnimeTable.id].value,
        row[AnimeTable.sourceReference],
        row[AnimeTable.url],
        row[AnimeTable.title],
        AnimeList.proxyThumbnailUrl(row[AnimeTable.id].value),
        row[AnimeTable.background_url],
        row[AnimeTable.initialized],
        row[AnimeTable.artist],
        row[AnimeTable.author],
        row[AnimeTable.description],
        row[AnimeTable.genre].toGenreList(),
        AnimeStatus.valueOf(row[AnimeTable.status]),
        row[AnimeTable.inLibrary],
        row[AnimeTable.inLibraryAt],
        AnimeUpdateStrategy.valueOf(row[AnimeTable.updateStrategy]),
        FetchType.valueOf(row[AnimeTable.fetchType]),
        row[AnimeTable.seasonNumber],
    )

    constructor(dataClass: AnimeDataClass) : this(
        dataClass.id,
        dataClass.sourceId.toLong(),
        dataClass.url,
        dataClass.title,
        dataClass.thumbnailUrl,
        dataClass.backgroundUrl,
        dataClass.initialized,
        dataClass.artist,
        dataClass.author,
        dataClass.description,
        dataClass.genre,
        AnimeStatus.valueOf(dataClass.status),
        dataClass.inLibrary,
        dataClass.inLibraryAt,
        dataClass.updateStrategy,
        dataClass.fetchType,
        dataClass.seasonNumber,
    )

    fun episodes(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<EpisodeNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, EpisodeNodeList>("EpisodesForAnimeDataLoader", id)

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<AnimeSourceType?> =
        dataFetchingEnvironment.getValueFromDataLoader<Long, AnimeSourceType?>("AnimeSourceDataLoader", sourceId)
}

data class AnimeNodeList(
    override val nodes: List<AnimeType>,
    override val edges: List<AnimeEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class AnimeEdge(
        override val cursor: Cursor,
        override val node: AnimeType,
    ) : Edge()

    companion object {
        fun List<AnimeType>.toNodeList(): AnimeNodeList =
            AnimeNodeList(
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

        private fun List<AnimeType>.getEdges(): List<AnimeEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                AnimeEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                AnimeEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
