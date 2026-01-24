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
import suwayomi.tachidesk.anime.model.table.EpisodeTable
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.DoubleFilter
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.HasGetOp
import suwayomi.tachidesk.graphql.queries.filter.IntFilter
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
import suwayomi.tachidesk.graphql.types.EpisodeNodeList
import suwayomi.tachidesk.graphql.types.EpisodeType
import java.util.concurrent.CompletableFuture

class EpisodeQuery {
    @RequireAuth
    fun episode(
        dataFetchingEnvironment: DataFetchingEnvironment,
        id: Int,
    ): CompletableFuture<EpisodeType> = dataFetchingEnvironment.getValueFromDataLoader("EpisodeDataLoader", id)

    enum class EpisodeOrderBy(
        override val column: Column<*>,
    ) : OrderBy<EpisodeType> {
        ID(EpisodeTable.id),
        SOURCE_ORDER(EpisodeTable.sourceOrder),
        NAME(EpisodeTable.name),
        UPLOAD_DATE(EpisodeTable.date_upload),
        EPISODE_NUMBER(EpisodeTable.episode_number),
        FETCHED_AT(EpisodeTable.fetchedAt),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> EpisodeTable.id greater cursor.value.toInt()
                SOURCE_ORDER -> greaterNotUnique(EpisodeTable.sourceOrder, EpisodeTable.id, cursor, String::toInt)
                NAME -> greaterNotUnique(EpisodeTable.name, EpisodeTable.id, cursor, String::toString)
                UPLOAD_DATE -> greaterNotUnique(EpisodeTable.date_upload, EpisodeTable.id, cursor, String::toLong)
                EPISODE_NUMBER -> greaterNotUnique(EpisodeTable.episode_number, EpisodeTable.id, cursor, String::toFloat)
                FETCHED_AT -> greaterNotUnique(EpisodeTable.fetchedAt, EpisodeTable.id, cursor, String::toLong)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> EpisodeTable.id less cursor.value.toInt()
                SOURCE_ORDER -> lessNotUnique(EpisodeTable.sourceOrder, EpisodeTable.id, cursor, String::toInt)
                NAME -> lessNotUnique(EpisodeTable.name, EpisodeTable.id, cursor, String::toString)
                UPLOAD_DATE -> lessNotUnique(EpisodeTable.date_upload, EpisodeTable.id, cursor, String::toLong)
                EPISODE_NUMBER -> lessNotUnique(EpisodeTable.episode_number, EpisodeTable.id, cursor, String::toFloat)
                FETCHED_AT -> lessNotUnique(EpisodeTable.fetchedAt, EpisodeTable.id, cursor, String::toLong)
            }

        override fun asCursor(type: EpisodeType): Cursor {
            val value =
                when (this) {
                    ID -> type.id.toString()
                    SOURCE_ORDER -> type.id.toString() + "-" + type.sourceOrder
                    NAME -> type.id.toString() + "-" + type.name
                    UPLOAD_DATE -> type.id.toString() + "-" + type.uploadDate
                    EPISODE_NUMBER -> type.id.toString() + "-" + type.episodeNumber
                    FETCHED_AT -> type.id.toString() + "-" + type.fetchedAt
                }
            return Cursor(value)
        }
    }

    data class EpisodeOrder(
        override val by: EpisodeOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<EpisodeOrderBy>

    data class EpisodeCondition(
        val id: Int? = null,
        val url: String? = null,
        val name: String? = null,
        val uploadDate: Long? = null,
        val episodeNumber: Float? = null,
        val scanlator: String? = null,
        val animeId: Int? = null,
        val sourceOrder: Int? = null,
        val realUrl: String? = null,
        val fetchedAt: Long? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, EpisodeTable.id)
            opAnd.eq(url, EpisodeTable.url)
            opAnd.eq(name, EpisodeTable.name)
            opAnd.eq(uploadDate, EpisodeTable.date_upload)
            opAnd.eq(episodeNumber, EpisodeTable.episode_number)
            opAnd.eq(scanlator, EpisodeTable.scanlator)
            opAnd.eq(animeId, EpisodeTable.anime)
            opAnd.eq(sourceOrder, EpisodeTable.sourceOrder)
            opAnd.eq(realUrl, EpisodeTable.realUrl)
            opAnd.eq(fetchedAt, EpisodeTable.fetchedAt)
            return opAnd.op
        }
    }

    data class EpisodeFilter(
        val id: IntFilter? = null,
        val url: StringFilter? = null,
        val name: StringFilter? = null,
        val uploadDate: LongFilter? = null,
        val episodeNumber: DoubleFilter? = null,
        val scanlator: StringFilter? = null,
        val animeId: IntFilter? = null,
        val sourceOrder: IntFilter? = null,
        val realUrl: StringFilter? = null,
        val fetchedAt: LongFilter? = null,
        val fillermark: BooleanFilter? = null,
        override val and: List<EpisodeFilter>? = null,
        override val or: List<EpisodeFilter>? = null,
        override val not: EpisodeFilter? = null,
    ) : Filter<EpisodeFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareEntity(EpisodeTable.id, id),
                andFilterWithCompareString(EpisodeTable.url, url),
                andFilterWithCompareString(EpisodeTable.name, name),
                andFilterWithCompare(EpisodeTable.date_upload, uploadDate),
                andFilterWithCompare(EpisodeTable.episode_number, episodeNumber?.toFloatFilter()),
                andFilterWithCompareString(EpisodeTable.scanlator, scanlator),
                andFilterWithCompareEntity(EpisodeTable.anime, animeId),
                andFilterWithCompare(EpisodeTable.sourceOrder, sourceOrder),
                andFilterWithCompareString(EpisodeTable.realUrl, realUrl),
                andFilterWithCompare(EpisodeTable.fetchedAt, fetchedAt),
                andFilterWithCompare(EpisodeTable.fillermark, fillermark),
            )
    }

    @RequireAuth
    fun episodes(
        condition: EpisodeCondition? = null,
        filter: EpisodeFilter? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderBy: EpisodeOrderBy? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderByType: SortOrder? = null,
        order: List<EpisodeOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): EpisodeNodeList {
        val queryResults =
            transaction {
                val res = EpisodeTable.selectAll()

                res.applyOps(condition, filter)

                if (order != null || orderBy != null || (last != null || before != null)) {
                    val baseSort = listOf(EpisodeOrder(EpisodeOrderBy.ID, SortOrder.ASC))
                    val deprecatedSort = listOfNotNull(orderBy?.let { EpisodeOrder(orderBy, orderByType) })
                    val actualSort = (order.orEmpty() + deprecatedSort + baseSort)
                    actualSort.forEach { (orderBy, orderByType) ->
                        val orderByColumn = orderBy.column
                        val orderType = orderByType.maybeSwap(last ?: before)

                        res.orderBy(orderByColumn to orderType)
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(EpisodeTable.id)?.value
                val lastResult = res.lastOrNull()?.get(EpisodeTable.id)?.value

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: EpisodeOrderBy.ID,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList())
            }

        val getAsCursor: (EpisodeType) -> Cursor = (order?.firstOrNull()?.by ?: EpisodeOrderBy.ID)::asCursor

        val resultsAsType = queryResults.results.map { EpisodeType(it) }

        return EpisodeNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        EpisodeNodeList.EpisodeEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        EpisodeNodeList.EpisodeEdge(
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
