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
import suwayomi.tachidesk.anime.impl.extension.AnimeExtension
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import java.util.concurrent.CompletableFuture

class AnimeExtensionType(
    val repo: String?,
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
    val isObsolete: Boolean,
) : Node {
    constructor(row: ResultRow) : this(
        repo = row[AnimeExtensionTable.repo],
        apkName = row[AnimeExtensionTable.apkName],
        iconUrl = AnimeExtension.getExtensionIconUrl(row[AnimeExtensionTable.apkName]),
        name = row[AnimeExtensionTable.name],
        pkgName = row[AnimeExtensionTable.pkgName],
        versionName = row[AnimeExtensionTable.versionName],
        versionCode = row[AnimeExtensionTable.versionCode],
        lang = row[AnimeExtensionTable.lang],
        isNsfw = row[AnimeExtensionTable.isNsfw],
        isInstalled = row[AnimeExtensionTable.isInstalled],
        hasUpdate = row[AnimeExtensionTable.hasUpdate],
        isObsolete = row[AnimeExtensionTable.isObsolete],
    )

    fun sources(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<AnimeSourceNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader<String, AnimeSourceNodeList>("AnimeSourcesForExtensionDataLoader", pkgName)
}

data class AnimeExtensionNodeList(
    override val nodes: List<AnimeExtensionType>,
    override val edges: List<AnimeExtensionEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class AnimeExtensionEdge(
        override val cursor: Cursor,
        override val node: AnimeExtensionType,
    ) : Edge()

    companion object {
        fun List<AnimeExtensionType>.toNodeList(): AnimeExtensionNodeList =
            AnimeExtensionNodeList(
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

        private fun List<AnimeExtensionType>.getEdges(): List<AnimeExtensionEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                AnimeExtensionEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                AnimeExtensionEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
