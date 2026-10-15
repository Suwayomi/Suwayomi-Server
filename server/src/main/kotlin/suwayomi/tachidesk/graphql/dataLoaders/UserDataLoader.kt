/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.dataloader.Try
import org.jetbrains.exposed.v1.jdbc.selectAll
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.server.primitives.inIds
import suwayomi.tachidesk.graphql.types.UserType
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.hasPermission
import suwayomi.tachidesk.server.user.requireUser

/**
 * Visibility of user rows: a caller without MANAGE_USERS may only resolve its own id.
 * The check is applied per key so that a batch containing foreign ids fails those keys
 * only instead of failing the whole batch.
 */
private fun Collection<Int>.visibleKeys(
    userId: Int,
    canManageUsers: Boolean,
): Set<Int> = if (canManageUsers) toSet() else filterTo(mutableSetOf()) { it == userId }

class UserDataLoader : KotlinDataLoader<Int, UserType> {
    override val dataLoaderName = "UserDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, UserType> =
        newBatchedDataLoaderWithTry { ids ->
            future {
                val user = graphQLContext.getAttribute(Attribute.TachideskUser)
                val userId = user.requireUser()
                val canManageUsers = user.hasPermission(UserPermission.MANAGE_USERS)
                val visibleIds = ids.visibleKeys(userId, canManageUsers)
                loggedTransaction {
                    val users =
                        UserAccountTable
                            .selectAll()
                            .where { UserAccountTable.id inIds visibleIds }
                            .map { UserType(it) }
                            .associateBy { it.id }
                    ids
                        .map {
                            // foreign ids fail per key instead of failing the whole batch
                            if (it in visibleIds) Try.succeeded(users[it]) else Try.failed(ForbiddenException())
                        }.asTriedDataLoaderResults()
                }
            }
        }
}

class PermissionsForUserDataLoader : KotlinDataLoader<Int, List<UserPermission>> {
    override val dataLoaderName = "PermissionsForUserDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<UserPermission>> =
        newBatchedDataLoader { ids ->
            future {
                val user = graphQLContext.getAttribute(Attribute.TachideskUser)
                val userId = user.requireUser()
                val canManageUsers = user.hasPermission(UserPermission.MANAGE_USERS)
                val visibleIds = ids.visibleKeys(userId, canManageUsers)
                loggedTransaction {
                    val permissionsByUserId =
                        UserPermissionsTable
                            .selectAll()
                            .where { UserPermissionsTable.user inIds visibleIds }
                            .map {
                                val permission =
                                    it[UserPermissionsTable.permission]
                                        .let { permission ->
                                            UserPermission.entries.find { it.name == permission }
                                        }
                                it[UserPermissionsTable.user].value to permission
                            }.groupBy(
                                keySelector = { it.first },
                                valueTransform = {
                                    it.second
                                },
                            ).map {
                                it.key to it.value.filterNotNull()
                            }.toMap()
                    ids.map { if (it in visibleIds) permissionsByUserId[it].orEmpty() else emptyList() }
                }
            }
        }
}

class RolesForUserDataLoader : KotlinDataLoader<Int, List<UserRole>> {
    override val dataLoaderName = "RolesForUserDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<UserRole>> =
        newBatchedDataLoader { ids ->
            future {
                val user = graphQLContext.getAttribute(Attribute.TachideskUser)
                val userId = user.requireUser()
                val canManageUsers = user.hasPermission(UserPermission.MANAGE_USERS)
                val visibleIds = ids.visibleKeys(userId, canManageUsers)
                loggedTransaction {
                    val rolesByUserId =
                        UserRolesTable
                            .selectAll()
                            .where { UserRolesTable.user inIds visibleIds }
                            .map {
                                val role =
                                    it[UserRolesTable.role]
                                        .let { role ->
                                            UserRole.entries.find { it.name == role }
                                        }
                                it[UserRolesTable.user].value to role
                            }.groupBy(
                                keySelector = { it.first },
                                valueTransform = {
                                    it.second
                                },
                            ).map {
                                it.key to it.value.filterNotNull()
                            }.toMap()
                    ids.map { if (it in visibleIds) rolesByUserId[it].orEmpty() else emptyList() }
                }
            }
        }
}
