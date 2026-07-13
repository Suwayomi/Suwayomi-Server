/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import java.util.concurrent.CompletableFuture

class ExtensionType(
    val storeIndexUrl: String?,
    @GraphQLDeprecated("Removed in extension api v1.6", ReplaceWith("storeIndexUrl"))
    val repo: String?,
    @GraphQLDescription("This will be nullable in the future")
    val apkName: String?,
    val iconUrl: String,
    val name: String,
    val pkgName: String,
    val apkUrl: String?,
    val jarUrl: String?,
    val extensionLib: String?,
    val versionName: String,
    @GraphQLDeprecated(
        "Type was changed to Long, will be switched back to this variable name in the future.",
        ReplaceWith("versionCodeLong"),
    )
    val versionCode: Int,
    val versionCodeLong: Long,
    val lang: String,
    @GraphQLDeprecated("Removed in extension api v1.6", ReplaceWith("contentWarning"))
    val isNsfw: Boolean,
    val contentWarning: ContentWarning,
    val isInstalled: Boolean,
    val hasUpdate: Boolean,
    val isObsolete: Boolean,
) : Node {
    constructor(row: ResultRow) : this(
        storeIndexUrl = row[ExtensionTable.storeIndexUrl],
        repo = row[ExtensionTable.storeIndexUrl],
        apkName = row[ExtensionTable.apkName].orEmpty(),
        iconUrl = Extension.proxyExtensionIconUrl(row[ExtensionTable.pkgName]),
        name = row[ExtensionTable.name],
        pkgName = row[ExtensionTable.pkgName],
        apkUrl = row[ExtensionTable.apkUrl],
        jarUrl = row[ExtensionTable.jarUrl],
        extensionLib = row[ExtensionTable.extensionLib],
        versionName = row[ExtensionTable.versionName],
        versionCode = row[ExtensionTable.versionCode].toInt(),
        versionCodeLong = row[ExtensionTable.versionCode],
        lang = row[ExtensionTable.lang],
        isNsfw = row[ExtensionTable.contentWarning] >= ContentWarning.MIXED.ordinal,
        contentWarning = ContentWarning.valueOf(row[ExtensionTable.contentWarning]),
        isInstalled = row[ExtensionTable.isInstalled],
        hasUpdate = row[ExtensionTable.hasUpdate],
        isObsolete = row[ExtensionTable.isObsolete],
    )

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<SourceNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader<String, SourceNodeList>("SourcesForExtensionDataLoader", pkgName)

    fun extensionStore(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ExtensionStoreType?> =
        dataFetchingEnvironment.getValueFromDataLoader<String, ExtensionStoreType?>("ExtensionStoreDataLoader", storeIndexUrl.orEmpty())
}

data class ExtensionNodeList(
    override val nodes: List<ExtensionType>,
    override val edges: List<ExtensionEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class ExtensionEdge(
        override val cursor: Cursor,
        override val node: ExtensionType,
    ) : Edge()

    companion object {
        fun List<ExtensionType>.toNodeList(): ExtensionNodeList =
            ExtensionNodeList(
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

        private fun List<ExtensionType>.getEdges(): List<ExtensionEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                ExtensionEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                ExtensionEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
