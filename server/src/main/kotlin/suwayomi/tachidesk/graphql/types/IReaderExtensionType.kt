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
import suwayomi.tachidesk.manga.model.dataclass.IReaderExtensionDataClass
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import java.util.concurrent.CompletableFuture

@GraphQLDescription("Represents an IReader extension")
data class IReaderExtensionType(
    val repo: String?,
    val apkName: String,
    val iconUrl: String,
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val isNsfw: Boolean,
    val installed: Boolean,
    val hasUpdate: Boolean,
    val obsolete: Boolean,
) : Node {
    constructor(dataClass: IReaderExtensionDataClass) : this(
        repo = dataClass.repo,
        apkName = dataClass.apkName,
        iconUrl = dataClass.iconUrl,
        name = dataClass.name,
        pkgName = dataClass.pkgName,
        versionName = dataClass.versionName,
        versionCode = dataClass.versionCode,
        lang = dataClass.lang,
        isNsfw = dataClass.isNsfw,
        installed = dataClass.installed,
        hasUpdate = dataClass.hasUpdate,
        obsolete = dataClass.obsolete,
    )

    fun sources(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<IReaderSourceNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader("IReaderSourcesForExtensionDataLoader", pkgName)

    companion object {
        fun fromResultRow(row: ResultRow): IReaderExtensionType =
            IReaderExtensionType(
                repo = row[IReaderExtensionTable.repo],
                apkName = row[IReaderExtensionTable.apkName],
                iconUrl = IReaderExtension.getExtensionIconUrl(row[IReaderExtensionTable.apkName]),
                name = row[IReaderExtensionTable.name],
                pkgName = row[IReaderExtensionTable.pkgName],
                versionName = row[IReaderExtensionTable.versionName],
                versionCode = row[IReaderExtensionTable.versionCode],
                lang = row[IReaderExtensionTable.lang],
                isNsfw = row[IReaderExtensionTable.isNsfw],
                installed = row[IReaderExtensionTable.isInstalled],
                hasUpdate = row[IReaderExtensionTable.hasUpdate],
                obsolete = row[IReaderExtensionTable.isObsolete],
            )
    }
}

data class IReaderExtensionNodeList(
    override val nodes: List<IReaderExtensionType>,
    override val edges: List<IReaderExtensionEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class IReaderExtensionEdge(
        override val cursor: Cursor,
        override val node: IReaderExtensionType,
    ) : Edge()

    companion object {
        fun List<IReaderExtensionType>.toNodeList(): IReaderExtensionNodeList =
            IReaderExtensionNodeList(
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

        private fun List<IReaderExtensionType>.getEdges(): List<IReaderExtensionEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                IReaderExtensionEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                IReaderExtensionEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
