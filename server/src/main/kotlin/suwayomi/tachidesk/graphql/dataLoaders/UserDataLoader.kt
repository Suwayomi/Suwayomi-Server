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
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.v1.core.Slf4jSqlDebugLogger
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.UserType
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.hasPermission
import suwayomi.tachidesk.server.user.requirePermissions
import suwayomi.tachidesk.server.user.requireUser

class UserDataLoader : KotlinDataLoader<Int, UserType> {
    override val dataLoaderName = "UserDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, UserType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                val canManageUsers = graphQLContext.getAttribute(Attribute.TachideskUser).hasPermission(UserPermission.MANAGE_USERS)
                if (!canManageUsers && ids.any { it != userId }) {
                    throw ForbiddenException()
                }
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val users =
                        UserAccountTable
                            .selectAll()
                            .where { UserAccountTable.id inList ids }
                            .map { UserType(it) }
                            .associateBy { it.id }
                    ids.map { users[it] }
                }
            }
        }
}

class PermissionsForUserDataLoader : KotlinDataLoader<Int, List<UserPermission>> {
    override val dataLoaderName = "PermissionsForUserDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<UserPermission>> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                val canManageUsers = graphQLContext.getAttribute(Attribute.TachideskUser).hasPermission(UserPermission.MANAGE_USERS)
                if (!canManageUsers && ids.any { it != userId }) {
                    throw ForbiddenException()
                }
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val permissionsByUserId =
                        UserPermissionsTable
                            .selectAll()
                            .where { UserPermissionsTable.user inList ids }
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
                    ids.map { permissionsByUserId[it].orEmpty() }
                }
            }
        }
}

class RolesForUserDataLoader : KotlinDataLoader<Int, List<UserRole>> {
    override val dataLoaderName = "RolesForUserDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<UserRole>> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                val canManageUsers = graphQLContext.getAttribute(Attribute.TachideskUser).hasPermission(UserPermission.MANAGE_USERS)
                if (!canManageUsers && ids.any { it != userId }) {
                    throw ForbiddenException()
                }
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val rolesByUserId =
                        UserRolesTable
                            .selectAll()
                            .where { UserRolesTable.user inList ids }
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
                    ids.map { rolesByUserId[it] }
                }
            }
        }
}
