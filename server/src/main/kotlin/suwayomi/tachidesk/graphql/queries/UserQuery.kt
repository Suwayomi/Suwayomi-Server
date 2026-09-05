/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.HasGetOp
import suwayomi.tachidesk.graphql.queries.filter.IntFilter
import suwayomi.tachidesk.graphql.queries.filter.OpAnd
import suwayomi.tachidesk.graphql.queries.filter.PermissionsFilter
import suwayomi.tachidesk.graphql.queries.filter.StringFilter
import suwayomi.tachidesk.graphql.queries.filter.andFilterEnum
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareEntity
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareString
import suwayomi.tachidesk.graphql.queries.filter.applyOps
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Order
import suwayomi.tachidesk.graphql.server.primitives.OrderBy
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.server.primitives.applyBeforeAfter
import suwayomi.tachidesk.graphql.server.primitives.applySortAndGetPaginationInfo
import suwayomi.tachidesk.graphql.server.primitives.greaterNotUnique
import suwayomi.tachidesk.graphql.server.primitives.lessNotUnique
import suwayomi.tachidesk.graphql.types.UserCodeType
import suwayomi.tachidesk.graphql.types.UserNodeList
import suwayomi.tachidesk.graphql.types.UserType
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.server.user.UserPermission
import java.util.concurrent.CompletableFuture

class UserQuery {
    @RequireAuth
    fun user(
        @GraphQLIgnore
        userId: Int,
        dataFetchingEnvironment: DataFetchingEnvironment,
        id: Int? = null,
    ): CompletableFuture<UserType> = dataFetchingEnvironment.getValueFromDataLoader("UserDataLoader", id ?: userId)

    @GraphQLDescription("Outstanding (unconsumed, unexpired) user codes.")
    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_USERS)
    fun userCodes(forUserId: Int? = null): List<UserCodeType> = UserCodeService.listOutstandingCodes(forUserId).map { UserCodeType(it) }

    enum class UserOrderBy(
        override val column: Column<*>,
    ) : OrderBy<UserType> {
        ID(UserAccountTable.id),
        USERNAME(UserAccountTable.username),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> UserAccountTable.id greater cursor.value.toInt()
                USERNAME -> greaterNotUnique(UserAccountTable.username, UserAccountTable.id, cursor, String::toString)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> UserAccountTable.id less cursor.value.toInt()
                USERNAME -> lessNotUnique(UserAccountTable.username, UserAccountTable.id, cursor, String::toString)
            }

        override fun asCursor(type: UserType): Cursor {
            val value =
                when (this) {
                    ID -> type.id.toString()
                    USERNAME -> type.id.toString() + "-" + type.username
                }
            return Cursor(value)
        }
    }

    data class UserOrder(
        override val by: UserOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<UserOrderBy>

    data class UserCondition(
        val id: Int? = null,
        val username: String? = null,
        val role: String? = null,
        val permission: UserPermission? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, UserAccountTable.id)
            opAnd.eq(username, UserAccountTable.username)
            // EXISTS-style subqueries (not joins) to avoid row fan-out when
            // combining role and permission conditions
            opAnd.andWhere(role) {
                UserAccountTable.id inSubQuery (
                    UserRolesTable
                        .select(UserRolesTable.user)
                        .where { (UserRolesTable.user eq UserAccountTable.id) and (UserRolesTable.role eq it) }
                )
            }
            opAnd.andWhere(permission) {
                UserAccountTable.id inSubQuery (
                    UserPermissionsTable
                        .select(UserPermissionsTable.user)
                        .where { (UserPermissionsTable.user eq UserAccountTable.id) and (UserPermissionsTable.permission eq it.name) }
                )
            }

            return opAnd.op
        }
    }

    data class UserFilter(
        val id: IntFilter? = null,
        val username: StringFilter? = null,
        val role: StringFilter? = null,
        val permission: PermissionsFilter? = null,
        override val and: List<UserFilter>? = null,
        override val or: List<UserFilter>? = null,
        override val not: UserFilter? = null,
    ) : Filter<UserFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareEntity(UserAccountTable.id, id),
                andFilterWithCompareString(UserAccountTable.username, username),
                roleSubqueryOp(role),
                permissionSubqueryOp(permission),
            )

        private fun roleSubqueryOp(filter: StringFilter?): Op<Boolean>? {
            filter ?: return null

            if (filter.isNull != null) {
                return if (filter.isNull == true) {
                    notExists(UserRolesTable.select(UserRolesTable.user).where { UserRolesTable.user eq UserAccountTable.id })
                } else {
                    UserAccountTable.id inSubQuery (
                        UserRolesTable
                            .select(UserRolesTable.user)
                            .where { (UserRolesTable.user eq UserAccountTable.id) }
                    )
                }
            }

            val inner = andFilterWithCompareString(UserRolesTable.role, filter) ?: return null

            return UserAccountTable.id inSubQuery (
                UserRolesTable
                    .select(UserRolesTable.user)
                    .where { (UserRolesTable.user eq UserAccountTable.id) and inner }
            )
        }

        private fun permissionSubqueryOp(filter: PermissionsFilter?): Op<Boolean>? {
            filter ?: return null

            // no permission rows at all
            if (filter.isNull == true) {
                return notExists(
                    UserPermissionsTable.select(UserPermissionsTable.user).where {
                        UserPermissionsTable.user eq
                            UserAccountTable.id
                    },
                )
            }

            val inner = andFilterEnum(UserPermissionsTable.permission, filter) ?: return null

            return UserAccountTable.id inSubQuery (
                UserPermissionsTable
                    .select(UserPermissionsTable.user)
                    .where { (UserPermissionsTable.user eq UserAccountTable.id) and inner }
            )
        }
    }

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_USERS)
    fun users(
        condition: UserCondition? = null,
        filter: UserFilter? = null,
        order: List<UserOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): UserNodeList {
        val queryResults =
            transaction {
                val res = UserAccountTable.selectAll()

                res.applyOps(condition, filter)

                val baseSort = listOf(UserOrder(UserOrderBy.ID, SortOrder.ASC))
                val actualSort = (order.orEmpty() + baseSort)

                val (total, firstResult, lastResult) = res.applySortAndGetPaginationInfo(actualSort, before, last, UserAccountTable.id)

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: UserOrderBy.ID,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList())
            }

        val getAsCursor: (UserType) -> Cursor = (order?.firstOrNull()?.by ?: UserOrderBy.ID)::asCursor

        val resultsAsType = queryResults.results.map { UserType(it) }

        return UserNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        UserNodeList.UserEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        UserNodeList.UserEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                )
            },
            pageInfo =
                PageInfo(
                    hasNextPage = queryResults.lastKey != resultsAsType.lastOrNull()?.id,
                    hasPreviousPage = queryResults.firstKey != resultsAsType.firstOrNull()?.id,
                    startCursor = resultsAsType.firstOrNull()?.let { getAsCursor(it) },
                    endCursor = resultsAsType.lastOrNull()?.let { getAsCursor(it) },
                ),
            totalCount = queryResults.total.toInt(),
        )
    }
}
