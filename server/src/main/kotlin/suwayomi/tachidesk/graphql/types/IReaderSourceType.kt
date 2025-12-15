/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension
import suwayomi.tachidesk.manga.model.dataclass.IReaderSourceDataClass
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import java.util.concurrent.CompletableFuture

@GraphQLDescription("Represents an IReader source")
data class IReaderSourceType(
    val id: String,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val isConfigurable: Boolean,
    val isNsfw: Boolean,
    val displayName: String,
    val baseUrl: String?,
) : Node {
    constructor(dataClass: IReaderSourceDataClass) : this(
        id = dataClass.id,
        name = dataClass.name,
        lang = dataClass.lang,
        iconUrl = dataClass.iconUrl,
        supportsLatest = dataClass.supportsLatest,
        isConfigurable = dataClass.isConfigurable,
        isNsfw = dataClass.isNsfw,
        displayName = dataClass.displayName,
        baseUrl = dataClass.baseUrl,
    )

    fun extension(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<IReaderExtensionType?> =
        dataFetchingEnvironment.getValueFromDataLoader("IReaderExtensionForSourceDataLoader", id.toLong())

    companion object {
        fun fromResultRow(row: ResultRow): IReaderSourceType? {
            return try {
                IReaderSourceType(
                    id = row[IReaderSourceTable.id].value.toString(),
                    name = row[IReaderSourceTable.name],
                    lang = row[IReaderSourceTable.lang],
                    iconUrl = IReaderExtension.getExtensionIconUrl(row[IReaderExtensionTable.apkName]),
                    supportsLatest = true,
                    isConfigurable = true, // IReader sources have preferences via PreferenceStore
                    isNsfw = row[IReaderSourceTable.isNsfw],
                    displayName = row[IReaderSourceTable.name],
                    baseUrl = null,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class IReaderSourceNodeList(
    override val nodes: List<IReaderSourceType>,
    override val edges: List<IReaderSourceEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class IReaderSourceEdge(
        override val cursor: Cursor,
        override val node: IReaderSourceType,
    ) : Edge()

    companion object {
        fun List<IReaderSourceType>.toNodeList(): IReaderSourceNodeList =
            IReaderSourceNodeList(
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

        private fun List<IReaderSourceType>.getEdges(): List<IReaderSourceEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                IReaderSourceEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                IReaderSourceEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
