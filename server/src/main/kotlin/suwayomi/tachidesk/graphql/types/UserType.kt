/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import java.util.concurrent.CompletableFuture

class UserType(
    val id: Int,
    val username: String,
) : Node {
    constructor(resultRow: ResultRow) : this(
        resultRow[UserAccountTable.id].value,
        resultRow[UserAccountTable.username],
    )

    fun permissions(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<UserPermission>> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, List<UserPermission>>("PermissionsForUserDataLoader", id)

    fun roles(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<UserRole>> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, List<UserRole>>("RolesForUserDataLoader", id)
}

data class UserNodeList(
    override val nodes: List<UserType>,
    override val edges: List<UserEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class UserEdge(
        override val cursor: Cursor,
        override val node: UserType,
    ) : Edge()

    companion object {
        fun List<UserType>.toNodeList(): UserNodeList =
            UserNodeList(
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

        private fun List<UserType>.getEdges(): List<UserEdge> {
            if (isEmpty()) return emptyList()
            return listOf(
                UserEdge(
                    cursor = Cursor("0"),
                    node = first(),
                ),
                UserEdge(
                    cursor = Cursor(lastIndex.toString()),
                    node = last(),
                ),
            )
        }
    }
}
