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
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
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
import suwayomi.tachidesk.graphql.types.AnimeSourceNodeList
import suwayomi.tachidesk.graphql.types.AnimeSourceType
import java.util.concurrent.CompletableFuture

class AnimeSourceQuery {
    @RequireAuth
    fun animeSource(
        dataFetchingEnvironment: DataFetchingEnvironment,
        id: Long,
    ): CompletableFuture<AnimeSourceType?> = dataFetchingEnvironment.getValueFromDataLoader("AnimeSourceDataLoader", id)

    enum class AnimeSourceOrderBy(
        override val column: Column<*>,
    ) : OrderBy<AnimeSourceType> {
        ID(AnimeSourceTable.id),
        NAME(AnimeSourceTable.name),
        LANG(AnimeSourceTable.lang),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> AnimeSourceTable.id greater cursor.value.toLong()
                NAME -> greaterNotUnique(AnimeSourceTable.name, AnimeSourceTable.id, cursor, String::toString)
                LANG -> greaterNotUnique(AnimeSourceTable.lang, AnimeSourceTable.id, cursor, String::toString)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> AnimeSourceTable.id less cursor.value.toLong()
                NAME -> lessNotUnique(AnimeSourceTable.name, AnimeSourceTable.id, cursor, String::toString)
                LANG -> lessNotUnique(AnimeSourceTable.lang, AnimeSourceTable.id, cursor, String::toString)
            }

        override fun asCursor(type: AnimeSourceType): Cursor {
            val value =
                when (this) {
                    ID -> type.id.toString()
                    NAME -> type.id.toString() + "-" + type.name
                    LANG -> type.id.toString() + "-" + type.lang
                }
            return Cursor(value)
        }
    }

    data class AnimeSourceOrder(
        override val by: AnimeSourceOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<AnimeSourceOrderBy>

    data class AnimeSourceCondition(
        val id: Long? = null,
        val name: String? = null,
        val lang: String? = null,
        val isNsfw: Boolean? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, AnimeSourceTable.id)
            opAnd.eq(name, AnimeSourceTable.name)
            opAnd.eq(lang, AnimeSourceTable.lang)
            opAnd.eq(isNsfw, AnimeSourceTable.isNsfw)
            return opAnd.op
        }
    }

    data class AnimeSourceFilter(
        val id: LongFilter? = null,
        val name: StringFilter? = null,
        val lang: StringFilter? = null,
        val isNsfw: BooleanFilter? = null,
        override val and: List<AnimeSourceFilter>? = null,
        override val or: List<AnimeSourceFilter>? = null,
        override val not: AnimeSourceFilter? = null,
    ) : Filter<AnimeSourceFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareEntity(AnimeSourceTable.id, id),
                andFilterWithCompareString(AnimeSourceTable.name, name),
                andFilterWithCompareString(AnimeSourceTable.lang, lang),
                andFilterWithCompare(AnimeSourceTable.isNsfw, isNsfw),
            )
    }

    @RequireAuth
    fun animeSources(
        condition: AnimeSourceCondition? = null,
        filter: AnimeSourceFilter? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderBy: AnimeSourceOrderBy? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderByType: SortOrder? = null,
        order: List<AnimeSourceOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): AnimeSourceNodeList {
        val (queryResults, resultsAsType) =
            transaction {
                val res = AnimeSourceTable.selectAll()

                res.applyOps(condition, filter)

                if (order != null || orderBy != null || (last != null || before != null)) {
                    val baseSort = listOf(AnimeSourceOrder(AnimeSourceOrderBy.ID, SortOrder.ASC))
                    val deprecatedSort = listOfNotNull(orderBy?.let { AnimeSourceOrder(orderBy, orderByType) })
                    val actualSort = (order.orEmpty() + deprecatedSort + baseSort)
                    actualSort.forEach { (orderBy, orderByType) ->
                        val orderByColumn = orderBy.column
                        val orderType = orderByType.maybeSwap(last ?: before)

                        res.orderBy(orderByColumn to orderType)
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(AnimeSourceTable.id)?.value
                val lastResult = res.lastOrNull()?.get(AnimeSourceTable.id)?.value

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: AnimeSourceOrderBy.ID,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList()).let {
                    it to it.results.mapNotNull { AnimeSourceType(it) }
                }
            }

        val getAsCursor: (AnimeSourceType) -> Cursor = (order?.firstOrNull()?.by ?: AnimeSourceOrderBy.ID)::asCursor

        return AnimeSourceNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        AnimeSourceNodeList.AnimeSourceEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        AnimeSourceNodeList.AnimeSourceEdge(
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
