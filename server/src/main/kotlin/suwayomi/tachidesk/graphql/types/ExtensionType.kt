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
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import java.util.concurrent.CompletableFuture

class ExtensionType(
    val apkName: String,
    val iconUrl: String,

    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val isNsfw: Boolean,

    val isInstalled: Boolean,
    val hasUpdate: Boolean,
    val isObsolete: Boolean
) : Node {
    constructor(row: ResultRow) : this(
        apkName = row[ExtensionTable.apkName],
        iconUrl = row[ExtensionTable.iconUrl],
        name = row[ExtensionTable.name],
        pkgName = row[ExtensionTable.pkgName],
        versionName = row[ExtensionTable.versionName],
        versionCode = row[ExtensionTable.versionCode],
        lang = row[ExtensionTable.lang],
        isNsfw = row[ExtensionTable.isNsfw],
        isInstalled = row[ExtensionTable.isInstalled],
        hasUpdate = row[ExtensionTable.hasUpdate],
        isObsolete = row[ExtensionTable.isObsolete]
    )

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<SourceNodeList> {
        return dataFetchingEnvironment.getValueFromDataLoader<String, SourceNodeList>("SourcesForExtensionDataLoader", pkgName)
    }
}

data class ExtensionNodeList(
    override val nodes: List<ExtensionType>,
    override val edges: List<ExtensionEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int
) : NodeList() {
    data class ExtensionEdge(
        override val cursor: Cursor,
        override val node: ExtensionType
    ) : Edge()

    companion object {
        fun List<ExtensionType>.toNodeList(): ExtensionNodeList {
            return ExtensionNodeList(
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

        private fun List<ExtensionType>.getEdges(): List<ExtensionEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                ExtensionEdge(
                    cursor = Cursor("0"),
                    node = first()
                ),
                ExtensionEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last()
                )
            )
        }
    }
}
