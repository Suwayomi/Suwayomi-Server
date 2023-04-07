/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.util.concurrent.CompletableFuture

class SourceType(
    val id: Long,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val isConfigurable: Boolean,
    val isNsfw: Boolean,
    val displayName: String
) : Node {
    constructor(source: SourceDataClass) : this(
        id = source.id.toLong(),
        name = source.name,
        lang = source.lang,
        iconUrl = source.iconUrl,
        supportsLatest = source.supportsLatest,
        isConfigurable = source.isConfigurable,
        isNsfw = source.isNsfw,
        displayName = source.displayName
    )

    constructor(row: ResultRow, sourceExtension: ResultRow, catalogueSource: CatalogueSource) : this(
        id = row[SourceTable.id].value,
        name = row[SourceTable.name],
        lang = row[SourceTable.lang],
        iconUrl = Extension.getExtensionIconUrl(sourceExtension[ExtensionTable.apkName]),
        supportsLatest = catalogueSource.supportsLatest,
        isConfigurable = catalogueSource is ConfigurableSource,
        isNsfw = row[SourceTable.isNsfw],
        displayName = catalogueSource.toString()
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MangaNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, MangaNodeList>("MangaForSourceDataLoader", id)
    }

    fun extension(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ExtensionType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, ExtensionType>("ExtensionForSourceDataLoader", id)
    }
}

fun SourceType(row: ResultRow): SourceType? {
    val catalogueSource = GetCatalogueSource
        .getCatalogueSourceOrNull(row[SourceTable.id].value)
        ?: return null
    val sourceExtension = if (row.hasValue(ExtensionTable.id)) {
        row
    } else {
        ExtensionTable
            .select { ExtensionTable.id eq row[SourceTable.extension] }
            .first()
    }

    return SourceType(row, sourceExtension, catalogueSource)
}

data class SourceNodeList(
    override val nodes: List<SourceType>,
    override val edges: List<SourceEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class SourceEdge(
        override val cursor: Cursor,
        override val node: SourceType
    ) : Edge()

    companion object {
        fun List<SourceType>.toNodeList(): SourceNodeList {
            return SourceNodeList(
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

        private fun List<SourceType>.getEdges(): List<SourceEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                SourceEdge(
                    cursor = Cursor("0"),
                    node = first()
                ),
                SourceEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last()
                )
            )
        }
    }
}
