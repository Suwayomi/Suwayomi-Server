/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.HasGetOp
import suwayomi.tachidesk.graphql.queries.filter.LongFilter
import suwayomi.tachidesk.graphql.queries.filter.OpAnd
import suwayomi.tachidesk.graphql.queries.filter.StringFilter
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompare
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareEntity
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
import suwayomi.tachidesk.graphql.types.IReaderSourceNodeList
import suwayomi.tachidesk.graphql.types.IReaderSourceType
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import java.util.concurrent.CompletableFuture

class IReaderSourceQuery {
    @RequireAuth
    fun ireaderSource(
        dataFetchingEnvironment: DataFetchingEnvironment,
        id: Long,
    ): CompletableFuture<IReaderSourceType?> =
        dataFetchingEnvironment.getValueFromDataLoader("IReaderSourceDataLoader", id)

    enum class IReaderSourceOrderBy(
        override val column: Column<*>,
    ) : OrderBy<IReaderSourceType> {
        ID(IReaderSourceTable.id),
        NAME(IReaderSourceTable.name),
        LANG(IReaderSourceTable.lang),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> IReaderSourceTable.id greater cursor.value.toLong()
                NAME -> greaterNotUnique(IReaderSourceTable.name, IReaderSourceTable.id, cursor, String::toString)
                LANG -> greaterNotUnique(IReaderSourceTable.lang, IReaderSourceTable.id, cursor, String::toString)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> IReaderSourceTable.id less cursor.value.toLong()
                NAME -> lessNotUnique(IReaderSourceTable.name, IReaderSourceTable.id, cursor, String::toString)
                LANG -> lessNotUnique(IReaderSourceTable.lang, IReaderSourceTable.id, cursor, String::toString)
            }

        override fun asCursor(type: IReaderSourceType): Cursor {
            val value =
                when (this) {
                    ID -> type.id
                    NAME -> type.id + "-" + type.name
                    LANG -> type.id + "-" + type.lang
                }
            return Cursor(value)
        }
    }

    data class IReaderSourceOrder(
        override val by: IReaderSourceOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<IReaderSourceOrderBy>

    data class IReaderSourceCondition(
        val id: Long? = null,
        val name: String? = null,
        val lang: String? = null,
        val isNsfw: Boolean? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, IReaderSourceTable.id)
            opAnd.eq(name, IReaderSourceTable.name)
            opAnd.eq(lang, IReaderSourceTable.lang)
            opAnd.eq(isNsfw, IReaderSourceTable.isNsfw)

            return opAnd.op
        }
    }

    data class IReaderSourceFilter(
        val id: LongFilter? = null,
        val name: StringFilter? = null,
        val lang: StringFilter? = null,
        val isNsfw: BooleanFilter? = null,
        override val and: List<IReaderSourceFilter>? = null,
        override val or: List<IReaderSourceFilter>? = null,
        override val not: IReaderSourceFilter? = null,
    ) : Filter<IReaderSourceFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareEntity(IReaderSourceTable.id, id),
                andFilterWithCompareString(IReaderSourceTable.name, name),
                andFilterWithCompareString(IReaderSourceTable.lang, lang),
                andFilterWithCompare(IReaderSourceTable.isNsfw, isNsfw),
            )
    }


    @RequireAuth
    fun ireaderSources(
        condition: IReaderSourceCondition? = null,
        filter: IReaderSourceFilter? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderBy: IReaderSourceOrderBy? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderByType: SortOrder? = null,
        order: List<IReaderSourceOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): IReaderSourceNodeList {
        val (queryResults, resultsAsType) =
            transaction {
                val res =
                    IReaderSourceTable
                        .innerJoin(IReaderExtensionTable)
                        .selectAll()

                res.applyOps(condition, filter)

                if (order != null || orderBy != null || (last != null || before != null)) {
                    val baseSort = listOf(IReaderSourceOrder(IReaderSourceOrderBy.ID, SortOrder.ASC))
                    val deprecatedSort = listOfNotNull(orderBy?.let { IReaderSourceOrder(orderBy, orderByType) })
                    val actualSort = (order.orEmpty() + deprecatedSort + baseSort)
                    actualSort.forEach { (orderBy, orderByType) ->
                        val orderByColumn = orderBy.column
                        val orderType = orderByType.maybeSwap(last ?: before)

                        res.orderBy(orderByColumn to orderType)
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(IReaderSourceTable.id)?.value
                val lastResult = res.lastOrNull()?.get(IReaderSourceTable.id)?.value

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: IReaderSourceOrderBy.ID,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList()).let {
                    it to it.results.mapNotNull { IReaderSourceType.fromResultRow(it) }
                }
            }

        val getAsCursor: (IReaderSourceType) -> Cursor =
            (order?.firstOrNull()?.by ?: IReaderSourceOrderBy.ID)::asCursor

        return IReaderSourceNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        IReaderSourceNodeList.IReaderSourceEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        IReaderSourceNodeList.IReaderSourceEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                )
            },
            pageInfo =
                PageInfo(
                    hasNextPage = queryResults.lastKey != resultsAsType.lastOrNull()?.id?.toLongOrNull(),
                    hasPreviousPage = queryResults.firstKey != resultsAsType.firstOrNull()?.id?.toLongOrNull(),
                    startCursor = resultsAsType.firstOrNull()?.let { getAsCursor(it) },
                    endCursor = resultsAsType.lastOrNull()?.let { getAsCursor(it) },
                ),
            totalCount = queryResults.total.toInt(),
        )
    }
}
