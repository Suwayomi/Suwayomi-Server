package suwayomi.tachidesk.graphql.queries

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.HasGetOp
import suwayomi.tachidesk.graphql.queries.filter.OpAnd
import suwayomi.tachidesk.graphql.queries.filter.StringFilter
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareString
import suwayomi.tachidesk.graphql.queries.filter.applyOps
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Order
import suwayomi.tachidesk.graphql.server.primitives.OrderBy
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.server.primitives.applyBeforeAfter
import suwayomi.tachidesk.graphql.server.primitives.greaterNotUnique
import suwayomi.tachidesk.graphql.server.primitives.lessNotUnique
import suwayomi.tachidesk.graphql.server.primitives.maybeSwap
import suwayomi.tachidesk.graphql.types.ExtensionStoreNodeList
import suwayomi.tachidesk.graphql.types.ExtensionStoreType
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import java.util.concurrent.CompletableFuture

class ExtensionStoreQuery {
    @RequireAuth
    fun extensionStore(
        dataFetchingEnvironment: DataFetchingEnvironment,
        indexUrl: String,
    ): CompletableFuture<ExtensionStoreType> = dataFetchingEnvironment.getValueFromDataLoader("ExtensionStoreDataLoader", indexUrl)

    enum class ExtensionStoreOrderBy(
        override val column: Column<*>,
    ) : OrderBy<ExtensionStoreType> {
        NAME(ExtensionStoreTable.name),
        INDEX_URL(ExtensionStoreTable.indexUrl),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                NAME -> greaterNotUnique(ExtensionStoreTable.name, ExtensionStoreTable.id, cursor, String::toString)
                INDEX_URL -> greaterNotUnique(ExtensionStoreTable.indexUrl, ExtensionStoreTable.id, cursor, String::toString)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                NAME -> lessNotUnique(ExtensionStoreTable.name, ExtensionStoreTable.id, cursor, String::toString)
                INDEX_URL -> lessNotUnique(ExtensionStoreTable.indexUrl, ExtensionStoreTable.id, cursor, String::toString)
            }

        override fun asCursor(type: ExtensionStoreType): Cursor {
            val value =
                when (this) {
                    INDEX_URL -> type.indexUrl
                    NAME -> type.indexUrl + "-" + type.name
                }
            return Cursor(value)
        }
    }

    data class ExtensionStoreOrder(
        override val by: ExtensionStoreOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<ExtensionStoreOrderBy>

    data class ExtensionStoreCondition(
        val id: Int? = null,
        val indexUrl: String? = null,
        val name: String? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, ExtensionStoreTable.id)
            opAnd.eq(indexUrl, ExtensionStoreTable.indexUrl)
            opAnd.eq(name, ExtensionStoreTable.name)

            return opAnd.op
        }
    }

    data class ExtensionStoreFilter(
        val indexUrl: StringFilter? = null,
        val name: StringFilter? = null,
        override val and: List<ExtensionStoreFilter>? = null,
        override val or: List<ExtensionStoreFilter>? = null,
        override val not: ExtensionStoreFilter? = null,
    ) : Filter<ExtensionStoreFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareString(ExtensionStoreTable.indexUrl, indexUrl),
                andFilterWithCompareString(ExtensionStoreTable.name, name),
            )
    }

    @RequireAuth
    fun extensionStores(
        condition: ExtensionStoreCondition? = null,
        filter: ExtensionStoreFilter? = null,
        order: List<ExtensionStoreOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): ExtensionStoreNodeList {
        val queryResults =
            transaction {
                val res = ExtensionStoreTable.selectAll()

                res.applyOps(condition, filter)

                if (order != null || (last != null || before != null)) {
                    val baseSort = listOf(ExtensionStoreOrder(ExtensionStoreOrderBy.INDEX_URL, SortOrder.ASC))
                    val actualSort = (order.orEmpty() + baseSort)
                    actualSort.forEach { (orderBy, orderByType) ->
                        val orderByColumn = orderBy.column
                        val orderType = orderByType.maybeSwap(last ?: before)

                        res.orderBy(orderByColumn to orderType)
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(ExtensionStoreTable.indexUrl)
                val lastResult = res.lastOrNull()?.get(ExtensionStoreTable.indexUrl)

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: ExtensionStoreOrderBy.INDEX_URL,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList())
            }

        val getAsCursor: (ExtensionStoreType) -> Cursor = (order?.firstOrNull()?.by ?: ExtensionStoreOrderBy.INDEX_URL)::asCursor

        val resultsAsType = queryResults.results.map { ExtensionStoreType(it) }

        return ExtensionStoreNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        ExtensionStoreNodeList.ExtensionStoreEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        ExtensionStoreNodeList.ExtensionStoreEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                )
            },
            pageInfo =
                PageInfo(
                    hasNextPage = queryResults.lastKey != resultsAsType.lastOrNull()?.indexUrl,
                    hasPreviousPage = queryResults.firstKey != resultsAsType.firstOrNull()?.indexUrl,
                    startCursor = resultsAsType.firstOrNull()?.let { getAsCursor(it) },
                    endCursor = resultsAsType.lastOrNull()?.let { getAsCursor(it) },
                ),
            totalCount = queryResults.total.toInt(),
        )
    }
}
