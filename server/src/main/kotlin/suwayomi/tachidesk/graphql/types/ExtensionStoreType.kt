package suwayomi.tachidesk.graphql.types

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import java.util.concurrent.CompletableFuture

class ExtensionStoreType(
    val name: String,
    val badgeLabel: String,
    val signingKey: String,
    val contactWebsite: String,
    val contactDiscord: String?,
    val indexUrl: String,
    val isLegacy: Boolean,
    val extensionListUrl: String?,
) : Node {
    constructor(row: ResultRow) : this(
        name = row[ExtensionStoreTable.name],
        badgeLabel = row[ExtensionStoreTable.badgeLabel],
        signingKey = row[ExtensionStoreTable.signingKey],
        contactWebsite = row[ExtensionStoreTable.contactWebsite],
        contactDiscord = row[ExtensionStoreTable.contactDiscord],
        indexUrl = row[ExtensionStoreTable.indexUrl],
        isLegacy = row[ExtensionStoreTable.isLegacy],
        extensionListUrl = row[ExtensionStoreTable.extensionListUrl],
    )

    fun extensions(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ExtensionNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader<String, ExtensionNodeList>("ExtensionForExtensionStore", indexUrl)
}

data class ExtensionStoreNodeList(
    override val nodes: List<ExtensionStoreType>,
    override val edges: List<ExtensionStoreEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class ExtensionStoreEdge(
        override val cursor: Cursor,
        override val node: ExtensionStoreType,
    ) : Edge()

    companion object {
        fun List<ExtensionStoreType>.toNodeList(): ExtensionStoreNodeList =
            ExtensionStoreNodeList(
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

        private fun List<ExtensionStoreType>.getEdges(): List<ExtensionStoreEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                ExtensionStoreEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                ExtensionStoreEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
