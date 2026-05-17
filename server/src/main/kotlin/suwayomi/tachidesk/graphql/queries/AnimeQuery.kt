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
import suwayomi.tachidesk.anime.impl.AnimeVideo
import suwayomi.tachidesk.anime.model.table.AnimeStatus
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.ComparableScalarFilter
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
import suwayomi.tachidesk.graphql.types.AnimeNodeList
import suwayomi.tachidesk.graphql.types.AnimeType
import suwayomi.tachidesk.graphql.types.HosterType
import suwayomi.tachidesk.graphql.types.VideoType
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class AnimeQuery {
    @RequireAuth
    fun anime(
        dataFetchingEnvironment: DataFetchingEnvironment,
        id: Int,
    ): CompletableFuture<AnimeType> = dataFetchingEnvironment.getValueFromDataLoader("AnimeDataLoader", id)

    enum class AnimeOrderBy(
        override val column: Column<*>,
    ) : OrderBy<AnimeType> {
        ID(AnimeTable.id),
        TITLE(AnimeTable.title),
        SOURCE_ID(AnimeTable.sourceReference),
        IN_LIBRARY_AT(AnimeTable.inLibraryAt),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> AnimeTable.id greater cursor.value.toInt()
                TITLE -> greaterNotUnique(AnimeTable.title, AnimeTable.id, cursor, String::toString)
                SOURCE_ID -> greaterNotUnique(AnimeTable.sourceReference, AnimeTable.id, cursor, String::toLong)
                IN_LIBRARY_AT -> greaterNotUnique(AnimeTable.inLibraryAt, AnimeTable.id, cursor, String::toLong)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> AnimeTable.id less cursor.value.toInt()
                TITLE -> lessNotUnique(AnimeTable.title, AnimeTable.id, cursor, String::toString)
                SOURCE_ID -> lessNotUnique(AnimeTable.sourceReference, AnimeTable.id, cursor, String::toLong)
                IN_LIBRARY_AT -> lessNotUnique(AnimeTable.inLibraryAt, AnimeTable.id, cursor, String::toLong)
            }

        override fun asCursor(type: AnimeType): Cursor {
            val value =
                when (this) {
                    ID -> type.id.toString()
                    TITLE -> type.id.toString() + "-" + type.title
                    SOURCE_ID -> type.id.toString() + "-" + type.sourceId
                    IN_LIBRARY_AT -> type.id.toString() + "-" + type.inLibraryAt
                }
            return Cursor(value)
        }
    }

    data class AnimeOrder(
        override val by: AnimeOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<AnimeOrderBy>

    data class AnimeCondition(
        val id: Int? = null,
        val sourceId: Long? = null,
        val url: String? = null,
        val title: String? = null,
        val status: AnimeStatus? = null,
        val initialized: Boolean? = null,
        val inLibrary: Boolean? = null,
        val inLibraryAt: Long? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, AnimeTable.id)
            opAnd.eq(sourceId, AnimeTable.sourceReference)
            opAnd.eq(url, AnimeTable.url)
            opAnd.eq(title, AnimeTable.title)
            opAnd.eq(status?.value, AnimeTable.status)
            opAnd.eq(initialized, AnimeTable.initialized)
            opAnd.eq(inLibrary, AnimeTable.inLibrary)
            opAnd.eq(inLibraryAt, AnimeTable.inLibraryAt)
            return opAnd.op
        }
    }

    data class AnimeStatusFilter(
        override val isNull: Boolean? = null,
        override val equalTo: AnimeStatus? = null,
        override val notEqualTo: AnimeStatus? = null,
        override val notEqualToAll: List<AnimeStatus>? = null,
        override val notEqualToAny: List<AnimeStatus>? = null,
        override val distinctFrom: AnimeStatus? = null,
        override val distinctFromAll: List<AnimeStatus>? = null,
        override val distinctFromAny: List<AnimeStatus>? = null,
        override val notDistinctFrom: AnimeStatus? = null,
        override val `in`: List<AnimeStatus>? = null,
        override val notIn: List<AnimeStatus>? = null,
        override val lessThan: AnimeStatus? = null,
        override val lessThanOrEqualTo: AnimeStatus? = null,
        override val greaterThan: AnimeStatus? = null,
        override val greaterThanOrEqualTo: AnimeStatus? = null,
    ) : ComparableScalarFilter<AnimeStatus> {
        fun asIntFilter() =
            IntFilter(
                equalTo = equalTo?.value,
                notEqualTo = notEqualTo?.value,
                notEqualToAll = notEqualToAll?.map { it.value },
                notEqualToAny = notEqualToAny?.map { it.value },
                distinctFrom = distinctFrom?.value,
                distinctFromAll = distinctFromAll?.map { it.value },
                distinctFromAny = distinctFromAny?.map { it.value },
                notDistinctFrom = notDistinctFrom?.value,
                `in` = `in`?.map { it.value },
                notIn = notIn?.map { it.value },
                lessThan = lessThan?.value,
                lessThanOrEqualTo = lessThanOrEqualTo?.value,
                greaterThan = greaterThan?.value,
                greaterThanOrEqualTo = greaterThanOrEqualTo?.value,
            )
    }

    data class AnimeFilter(
        val id: IntFilter? = null,
        val sourceId: LongFilter? = null,
        val url: StringFilter? = null,
        val title: StringFilter? = null,
        val status: AnimeStatusFilter? = null,
        val initialized: BooleanFilter? = null,
        val inLibrary: BooleanFilter? = null,
        val inLibraryAt: LongFilter? = null,
        override val and: List<AnimeFilter>? = null,
        override val or: List<AnimeFilter>? = null,
        override val not: AnimeFilter? = null,
    ) : Filter<AnimeFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareEntity(AnimeTable.id, id),
                andFilterWithCompare(AnimeTable.sourceReference, sourceId),
                andFilterWithCompareString(AnimeTable.url, url),
                andFilterWithCompareString(AnimeTable.title, title),
                andFilterWithCompare(AnimeTable.status, status?.asIntFilter()),
                andFilterWithCompare(AnimeTable.initialized, initialized),
                andFilterWithCompare(AnimeTable.inLibrary, inLibrary),
                andFilterWithCompare(AnimeTable.inLibraryAt, inLibraryAt),
            )
    }

    @RequireAuth
    fun animes(
        condition: AnimeCondition? = null,
        filter: AnimeFilter? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderBy: AnimeOrderBy? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderByType: SortOrder? = null,
        order: List<AnimeOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): AnimeNodeList {
        val queryResults =
            transaction {
                val res = AnimeTable.selectAll()

                res.applyOps(condition, filter)

                if (order != null || orderBy != null || (last != null || before != null)) {
                    val baseSort = listOf(AnimeOrder(AnimeOrderBy.ID, SortOrder.ASC))
                    val deprecatedSort = listOfNotNull(orderBy?.let { AnimeOrder(orderBy, orderByType) })
                    val actualSort = (order.orEmpty() + deprecatedSort + baseSort)
                    actualSort.forEach { (orderBy, orderByType) ->
                        val orderByColumn = orderBy.column
                        val orderType = orderByType.maybeSwap(last ?: before)

                        res.orderBy(orderByColumn to orderType)
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(AnimeTable.id)?.value
                val lastResult = res.lastOrNull()?.get(AnimeTable.id)?.value

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: AnimeOrderBy.ID,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList())
            }

        val getAsCursor: (AnimeType) -> Cursor = (order?.firstOrNull()?.by ?: AnimeOrderBy.ID)::asCursor

        val resultsAsType = queryResults.results.map { AnimeType(it) }

        return AnimeNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        AnimeNodeList.AnimeEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        AnimeNodeList.AnimeEdge(
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

    @RequireAuth
    fun episodeVideos(
        animeId: Int,
        episodeIndex: Int,
    ): CompletableFuture<List<VideoType>> =
        future {
            AnimeVideo
                .getEpisodeVideos(animeId, episodeIndex)
                .map { VideoType(it) }
        }

    @RequireAuth
    fun episodeHosters(
        animeId: Int,
        episodeIndex: Int,
    ): CompletableFuture<List<HosterType>> =
        future {
            AnimeVideo
                .getHosters(animeId, episodeIndex)
                .map { HosterType(it) }
        }

    @RequireAuth
    fun hosterVideos(
        animeId: Int,
        episodeIndex: Int,
        hosterIndex: Int,
    ): CompletableFuture<List<VideoType>> =
        future {
            AnimeVideo
                .getHosterVideos(animeId, episodeIndex, hosterIndex)
                .map { VideoType(it) }
        }
}
