/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.IntFilter
import suwayomi.tachidesk.graphql.queries.filter.OpAnd
import suwayomi.tachidesk.graphql.queries.filter.StringFilter
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompare
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareEntity
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareString
import suwayomi.tachidesk.graphql.queries.filter.getOp
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.types.CategoryNodeList
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Paged queries
 *
 * TODO Mutations
 * - Name
 * - Order
 * - Default
 * - Create
 * - Delete
 * - Add/update meta
 * - Delete meta
 */
class CategoryQuery {
    fun category(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<CategoryType?> {
        return dataFetchingEnvironment.getValueFromDataLoader("CategoryDataLoader", id)
    }

    enum class CategoryOrderBy {
        ID,
        NAME,
        ORDER
    }

    private fun getAsCursor(orderBy: CategoryOrderBy?, type: CategoryType): Cursor {
        val value = when (orderBy) {
            CategoryOrderBy.ID, null -> type.id.toString()
            CategoryOrderBy.NAME -> type.name
            CategoryOrderBy.ORDER -> type.order.toString()
        }
        return Cursor(value)
    }

    data class CategoryCondition(
        val id: Int? = null,
        val order: Int? = null,
        val name: String? = null,
        val default: Boolean? = null
    ) {
        fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            fun <T> eq(value: T?, column: Column<T>) = opAnd.andWhere(value) { column eq it }
            fun <T : Comparable<T>> eq(value: T?, column: Column<EntityID<T>>) = opAnd.andWhere(value) { column eq it }
            eq(id, CategoryTable.id)
            eq(order, CategoryTable.order)
            eq(name, CategoryTable.name)
            eq(default, CategoryTable.isDefault)

            return opAnd.op
        }
    }

    data class CategoryFilter(
        val id: IntFilter? = null,
        val order: IntFilter? = null,
        val name: StringFilter? = null,
        val default: BooleanFilter? = null,
        override val and: List<CategoryFilter>? = null,
        override val or: List<CategoryFilter>? = null,
        override val not: CategoryFilter? = null
    ) : Filter<CategoryFilter> {
        override fun getOpList(): List<Op<Boolean>> {
            return listOfNotNull(
                andFilterWithCompareEntity(CategoryTable.id, id),
                andFilterWithCompare(CategoryTable.order, order),
                andFilterWithCompareString(CategoryTable.name, name),
                andFilterWithCompare(CategoryTable.isDefault, default),
            )
        }
    }

    fun categories(
        condition: CategoryCondition? = null,
        filter: CategoryFilter? = null,
        orderBy: CategoryOrderBy? = null,
        orderByType: SortOrder? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null
    ): CategoryNodeList {
        val queryResults = transaction {
            val res = CategoryTable.selectAll()

            val conditionOp = condition?.getOp()
            if (conditionOp != null) {
                res.andWhere { conditionOp }
            }
            val filterOp = filter?.getOp()
            if (filterOp != null) {
                res.andWhere { filterOp }
            }
            if (orderBy != null || (last != null || before != null)) {
                val orderByColumn = when (orderBy) {
                    CategoryOrderBy.ID, null -> CategoryTable.id
                    CategoryOrderBy.NAME -> CategoryTable.name
                    CategoryOrderBy.ORDER -> CategoryTable.order
                }
                val orderType = if (last != null || before != null) {
                    when (orderByType) {
                        SortOrder.ASC -> SortOrder.DESC
                        SortOrder.DESC -> SortOrder.ASC
                        SortOrder.ASC_NULLS_FIRST -> SortOrder.DESC_NULLS_LAST
                        SortOrder.DESC_NULLS_FIRST -> SortOrder.ASC_NULLS_LAST
                        SortOrder.ASC_NULLS_LAST -> SortOrder.DESC_NULLS_FIRST
                        SortOrder.DESC_NULLS_LAST -> SortOrder.ASC_NULLS_FIRST
                        null -> SortOrder.DESC
                    }
                } else {
                    orderByType ?: SortOrder.ASC
                }
                res.orderBy(orderByColumn, order = orderType)
            }

            val total = res.count()
            val firstResult = res.first()[CategoryTable.id].value
            val lastResult = res.last()[CategoryTable.id].value

            if (after != null) {
                when (orderBy) {
                    CategoryOrderBy.ID, null -> res.andWhere {
                        CategoryTable.id greater after.value.toInt()
                    }
                    CategoryOrderBy.NAME -> res.andWhere {
                        CategoryTable.name greater after.value
                    }
                    CategoryOrderBy.ORDER -> res.andWhere {
                        CategoryTable.order greater after.value.toInt()
                    }
                }
            } else if (before != null) {
                when (orderBy) {
                    CategoryOrderBy.ID, null -> res.andWhere {
                        CategoryTable.id less before.value.toInt()
                    }
                    CategoryOrderBy.NAME -> res.andWhere {
                        CategoryTable.name less before.value
                    }
                    CategoryOrderBy.ORDER -> res.andWhere {
                        CategoryTable.order less before.value.toInt()
                    }
                }
            }

            if (first != null) {
                res.limit(first, offset?.toLong() ?: 0)
            } else if (last != null) {
                res.limit(last)
            }

            QueryResults(total, firstResult, lastResult, res.toList())
        }

        val resultsAsType = queryResults.results.map { CategoryType(it) }

        return CategoryNodeList(
            resultsAsType,
            CategoryNodeList.CategoryEdges(
                cursor = getAsCursor(orderBy, resultsAsType.last()),
                node = resultsAsType.last()
            ),
            pageInfo = PageInfo(
                hasNextPage = queryResults.lastKey != resultsAsType.last().id,
                hasPreviousPage = queryResults.firstKey != resultsAsType.first().id,
                startCursor = getAsCursor(orderBy, resultsAsType.first()),
                endCursor = getAsCursor(orderBy, resultsAsType.last())
            ),
            totalCount = queryResults.total.toInt()
        )
    }
}
