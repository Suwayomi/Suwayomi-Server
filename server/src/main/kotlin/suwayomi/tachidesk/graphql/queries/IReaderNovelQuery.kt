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
import suwayomi.tachidesk.graphql.types.IReaderNovelNodeList
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.manga.model.table.IReaderNovelTable
import java.util.concurrent.CompletableFuture

class IReaderNovelQuery {
    @RequireAuth
    fun ireaderNovel(
        dataFetchingEnvironment: DataFetchingEnvironment,
        id: Int,
    ): CompletableFuture<IReaderNovelType?> =
        dataFetchingEnvironment.getValueFromDataLoader("IReaderNovelDataLoader", id)

    enum class IReaderNovelOrderBy(
        override val column: Column<*>,
    ) : OrderBy<IReaderNovelType> {
        ID(IReaderNovelTable.id),
        TITLE(IReaderNovelTable.title),
        IN_LIBRARY_AT(IReaderNovelTable.inLibraryAt),
        LAST_FETCHED_AT(IReaderNovelTable.lastFetchedAt),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> IReaderNovelTable.id greater cursor.value.toInt()
                TITLE -> greaterNotUnique(IReaderNovelTable.title, IReaderNovelTable.id, cursor, String::toString)
                IN_LIBRARY_AT -> greaterNotUnique(IReaderNovelTable.inLibraryAt, IReaderNovelTable.id, cursor, String::toLong)
                LAST_FETCHED_AT -> greaterNotUnique(IReaderNovelTable.lastFetchedAt, IReaderNovelTable.id, cursor, String::toLong)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> IReaderNovelTable.id less cursor.value.toInt()
                TITLE -> lessNotUnique(IReaderNovelTable.title, IReaderNovelTable.id, cursor, String::toString)
                IN_LIBRARY_AT -> lessNotUnique(IReaderNovelTable.inLibraryAt, IReaderNovelTable.id, cursor, String::toLong)
                LAST_FETCHED_AT -> lessNotUnique(IReaderNovelTable.lastFetchedAt, IReaderNovelTable.id, cursor, String::toLong)
            }

        override fun asCursor(type: IReaderNovelType): Cursor {
            val value =
                when (this) {
                    ID -> type.id.toString()
                    TITLE -> type.id.toString() + "-" + type.title
                    IN_LIBRARY_AT -> type.id.toString() + "-" + type.inLibraryAt.toString()
                    LAST_FETCHED_AT -> type.id.toString() + "-" + type.lastFetchedAt.toString()
                }
            return Cursor(value)
        }
    }

    data class IReaderNovelOrder(
        override val by: IReaderNovelOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<IReaderNovelOrderBy>

    data class IReaderNovelCondition(
        val id: Int? = null,
        val sourceId: Long? = null,
        val url: String? = null,
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val initialized: Boolean? = null,
        val artist: String? = null,
        val author: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
        val inLibrary: Boolean? = null,
        val inLibraryAt: Long? = null,
        val lastFetchedAt: Long? = null,
        val chaptersLastFetchedAt: Long? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, IReaderNovelTable.id)
            opAnd.eq(sourceId, IReaderNovelTable.sourceReference)
            opAnd.eq(url, IReaderNovelTable.url)
            opAnd.eq(title, IReaderNovelTable.title)
            opAnd.eq(thumbnailUrl, IReaderNovelTable.thumbnailUrl)
            opAnd.eq(initialized, IReaderNovelTable.initialized)
            opAnd.eq(artist, IReaderNovelTable.artist)
            opAnd.eq(author, IReaderNovelTable.author)
            opAnd.eq(description, IReaderNovelTable.description)
            opAnd.andWhereAll(genre) { IReaderNovelTable.genre like "%$it%" }
            opAnd.eq(status, IReaderNovelTable.status)
            opAnd.eq(inLibrary, IReaderNovelTable.inLibrary)
            opAnd.eq(inLibraryAt, IReaderNovelTable.inLibraryAt)
            opAnd.eq(lastFetchedAt, IReaderNovelTable.lastFetchedAt)
            opAnd.eq(chaptersLastFetchedAt, IReaderNovelTable.chaptersLastFetchedAt)

            return opAnd.op
        }
    }

    data class IReaderNovelFilter(
        val id: IntFilter? = null,
        val sourceId: LongFilter? = null,
        val url: StringFilter? = null,
        val title: StringFilter? = null,
        val thumbnailUrl: StringFilter? = null,
        val initialized: BooleanFilter? = null,
        val artist: StringFilter? = null,
        val author: StringFilter? = null,
        val description: StringFilter? = null,
        val genre: StringFilter? = null,
        val status: LongFilter? = null,
        val inLibrary: BooleanFilter? = null,
        val inLibraryAt: LongFilter? = null,
        val lastFetchedAt: LongFilter? = null,
        val chaptersLastFetchedAt: LongFilter? = null,
        override val and: List<IReaderNovelFilter>? = null,
        override val or: List<IReaderNovelFilter>? = null,
        override val not: IReaderNovelFilter? = null,
    ) : Filter<IReaderNovelFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareEntity(IReaderNovelTable.id, id),
                andFilterWithCompare(IReaderNovelTable.sourceReference, sourceId),
                andFilterWithCompareString(IReaderNovelTable.url, url),
                andFilterWithCompareString(IReaderNovelTable.title, title),
                andFilterWithCompareString(IReaderNovelTable.thumbnailUrl, thumbnailUrl),
                andFilterWithCompare(IReaderNovelTable.initialized, initialized),
                andFilterWithCompareString(IReaderNovelTable.artist, artist),
                andFilterWithCompareString(IReaderNovelTable.author, author),
                andFilterWithCompareString(IReaderNovelTable.description, description),
                andFilterWithCompareString(IReaderNovelTable.genre, genre),
                andFilterWithCompare(IReaderNovelTable.status, status),
                andFilterWithCompare(IReaderNovelTable.inLibrary, inLibrary),
                andFilterWithCompare(IReaderNovelTable.inLibraryAt, inLibraryAt),
                andFilterWithCompare(IReaderNovelTable.lastFetchedAt, lastFetchedAt),
                andFilterWithCompare(IReaderNovelTable.chaptersLastFetchedAt, chaptersLastFetchedAt),
            )
    }


    @RequireAuth
    fun ireaderNovels(
        condition: IReaderNovelCondition? = null,
        filter: IReaderNovelFilter? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderBy: IReaderNovelOrderBy? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderByType: SortOrder? = null,
        order: List<IReaderNovelOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): IReaderNovelNodeList {
        val queryResults =
            transaction {
                val res = IReaderNovelTable.selectAll()

                res.applyOps(condition, filter)

                if (order != null || orderBy != null || (last != null || before != null)) {
                    val baseSort = listOf(IReaderNovelOrder(IReaderNovelOrderBy.ID, SortOrder.ASC))
                    val deprecatedSort = listOfNotNull(orderBy?.let { IReaderNovelOrder(orderBy, orderByType) })
                    val actualSort = (order.orEmpty() + deprecatedSort + baseSort)
                    actualSort.forEach { (orderBy, orderByType) ->
                        val orderByColumn = orderBy.column
                        val orderType = orderByType.maybeSwap(last ?: before)

                        res.orderBy(orderByColumn to orderType)
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(IReaderNovelTable.id)?.value
                val lastResult = res.lastOrNull()?.get(IReaderNovelTable.id)?.value

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: IReaderNovelOrderBy.ID,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList())
            }

        val getAsCursor: (IReaderNovelType) -> Cursor = (order?.firstOrNull()?.by ?: IReaderNovelOrderBy.ID)::asCursor

        val resultsAsType = queryResults.results.map { IReaderNovelType(it) }

        return IReaderNovelNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        IReaderNovelNodeList.IReaderNovelEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        IReaderNovelNodeList.IReaderNovelEdge(
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
