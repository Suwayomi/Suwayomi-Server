/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
import suwayomi.tachidesk.graphql.queries.filter.coalesceDefault
import suwayomi.tachidesk.graphql.queries.filter.escapeLikePattern
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Order
import suwayomi.tachidesk.graphql.server.primitives.OrderBy
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.PaginationNeeds
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.server.primitives.applyBeforeAfter
import suwayomi.tachidesk.graphql.server.primitives.applySortAndGetPaginationInfo
import suwayomi.tachidesk.graphql.server.primitives.greaterNotUnique
import suwayomi.tachidesk.graphql.server.primitives.lessNotUnique
import suwayomi.tachidesk.graphql.server.primitives.validatePagination
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import java.util.concurrent.CompletableFuture

class MangaQuery {
    @RequireAuth
    fun manga(
        dataFetchingEnvironment: DataFetchingEnvironment,
        id: Int,
    ): CompletableFuture<MangaType> = dataFetchingEnvironment.getValueFromDataLoader("MangaDataLoader", id)

    enum class MangaOrderBy(
        override val column: ExpressionWithColumnType<*>,
    ) : OrderBy<MangaType> {
        ID(MangaTable.id),
        TITLE(MangaTable.title),

        // LEFT JOIN'd per-user column: NULL (manga has no user row) must sort and compare
        // as 0, matching the `getOrNull(...) ?: 0` mapping in MangaType
        IN_LIBRARY_AT(coalesceDefault(MangaUserTable.inLibraryAt, 0L)),
        LAST_FETCHED_AT(MangaTable.lastFetchedAt),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> {
                    MangaTable.id greater cursor.value.toInt()
                }

                TITLE -> {
                    greaterNotUnique(MangaTable.title, MangaTable.id, cursor, String::toString)
                }

                IN_LIBRARY_AT -> {
                    greaterNotUnique(
                        coalesceDefault(MangaUserTable.inLibraryAt, 0L),
                        MangaTable.id,
                        cursor,
                        String::toLong,
                    )
                }

                LAST_FETCHED_AT -> {
                    greaterNotUnique(MangaTable.lastFetchedAt, MangaTable.id, cursor, String::toLong)
                }
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                ID -> {
                    MangaTable.id less cursor.value.toInt()
                }

                TITLE -> {
                    lessNotUnique(MangaTable.title, MangaTable.id, cursor, String::toString)
                }

                IN_LIBRARY_AT -> {
                    lessNotUnique(
                        coalesceDefault(MangaUserTable.inLibraryAt, 0L),
                        MangaTable.id,
                        cursor,
                        String::toLong,
                    )
                }

                LAST_FETCHED_AT -> {
                    lessNotUnique(MangaTable.lastFetchedAt, MangaTable.id, cursor, String::toLong)
                }
            }

        override fun asCursor(type: MangaType): Cursor {
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

    data class MangaOrder(
        override val by: MangaOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<MangaOrderBy>

    data class MangaCondition(
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
        val status: MangaStatus? = null,
        val inLibrary: Boolean? = null,
        val inLibraryAt: Long? = null,
        val realUrl: String? = null,
        val lastFetchedAt: Long? = null,
        val chaptersLastFetchedAt: Long? = null,
        val categoryIds: List<Int>? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(id, MangaTable.id)
            opAnd.eq(sourceId, MangaTable.sourceReference)
            opAnd.eq(url, MangaTable.url)
            opAnd.eq(title, MangaTable.title)
            opAnd.eq(thumbnailUrl, MangaTable.thumbnail_url)
            opAnd.eq(initialized, MangaTable.initialized)
            opAnd.eq(artist, MangaTable.artist)
            opAnd.eq(author, MangaTable.author)
            opAnd.eq(description, MangaTable.description)
            opAnd.andWhereAll(genre) { genreListContains(it) }
            opAnd.eq(status?.value, MangaTable.status)
            opAnd.eq(inLibrary, coalesceDefault(MangaUserTable.inLibrary, false))
            opAnd.eq(inLibraryAt, coalesceDefault(MangaUserTable.inLibraryAt, 0))
            opAnd.eq(realUrl, MangaTable.realUrl)
            opAnd.eq(lastFetchedAt, MangaTable.lastFetchedAt)
            opAnd.eq(chaptersLastFetchedAt, MangaTable.chaptersLastFetchedAt)
            opAnd.andWhere(categoryIds) {
                // an explicitly empty id list matches nothing instead of silently degrading to no filter
                if (it.isEmpty()) Op.FALSE else CategoryMangaTable.category inList it
            }

            return opAnd.op
        }

        /**
         * `genre` is stored as a single comma-separated string (both `","` and `", "` separators
         * occur in the wild), so a plain substring `LIKE` would also match partial genre names.
         * Compare it as a list element under both separators, with the value escaped as a literal.
         */
        private fun genreListContains(genre: String): Op<Boolean> {
            val escaped = escapeLikePattern(genre)
            return (MangaTable.genre eq genre) or
                (MangaTable.genre like LikePattern("$escaped,%", '\\')) or // "g, ..." (covers "g, " too)
                (MangaTable.genre like LikePattern("%, $escaped", '\\')) or // "..., g"
                (MangaTable.genre like LikePattern("%,$escaped", '\\')) or // "...,g"
                (MangaTable.genre like LikePattern("%, $escaped,%", '\\')) or // "..., g, ..."
                (MangaTable.genre like LikePattern("%,$escaped,%", '\\')) // "...,g,..."
        }
    }

    data class MangaStatusFilter(
        override val isNull: Boolean? = null,
        override val equalTo: MangaStatus? = null,
        override val notEqualTo: MangaStatus? = null,
        override val notEqualToAll: List<MangaStatus>? = null,
        override val notEqualToAny: List<MangaStatus>? = null,
        override val distinctFrom: MangaStatus? = null,
        override val distinctFromAll: List<MangaStatus>? = null,
        override val distinctFromAny: List<MangaStatus>? = null,
        override val notDistinctFrom: MangaStatus? = null,
        override val `in`: List<MangaStatus>? = null,
        override val notIn: List<MangaStatus>? = null,
        override val lessThan: MangaStatus? = null,
        override val lessThanOrEqualTo: MangaStatus? = null,
        override val greaterThan: MangaStatus? = null,
        override val greaterThanOrEqualTo: MangaStatus? = null,
    ) : ComparableScalarFilter<MangaStatus> {
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

    data class MangaFilter(
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
        val status: MangaStatusFilter? = null,
        val inLibrary: BooleanFilter? = null,
        val inLibraryAt: LongFilter? = null,
        val realUrl: StringFilter? = null,
        val lastFetchedAt: LongFilter? = null,
        val chaptersLastFetchedAt: LongFilter? = null,
        val categoryId: IntFilter? = null,
        override val and: List<MangaFilter>? = null,
        override val or: List<MangaFilter>? = null,
        override val not: MangaFilter? = null,
    ) : Filter<MangaFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareEntity(MangaTable.id, id),
                andFilterWithCompare(MangaTable.sourceReference, sourceId),
                andFilterWithCompareString(MangaTable.url, url),
                andFilterWithCompareString(MangaTable.title, title),
                andFilterWithCompareString(MangaTable.thumbnail_url, thumbnailUrl),
                andFilterWithCompare(MangaTable.initialized, initialized),
                andFilterWithCompareString(MangaTable.artist, artist),
                andFilterWithCompareString(MangaTable.author, author),
                andFilterWithCompareString(MangaTable.description, description),
                andFilterWithCompareString(MangaTable.genre, genre),
                andFilterWithCompare(MangaTable.status, status?.asIntFilter()),
                andFilterWithCompare(coalesceDefault(MangaUserTable.inLibrary, false), inLibrary),
                andFilterWithCompare(coalesceDefault(MangaUserTable.inLibraryAt, 0), inLibraryAt),
                andFilterWithCompareString(MangaTable.realUrl, realUrl),
                andFilterWithCompare(MangaTable.lastFetchedAt, lastFetchedAt),
                andFilterWithCompare(MangaTable.chaptersLastFetchedAt, chaptersLastFetchedAt),
            ) + categoryIdOps()

        /**
         * Category predicates are evaluated per manga, not per joined `CategoryMangaTable`
         * row: a manga with categories {1, 5} would otherwise satisfy `notIn: [5]` on its
         * (manga, 1) join row. Positive membership goes through `EXISTS`, negations through
         * `NOT EXISTS` over a correlated subquery, so `and`/`or`/`not` combinators keep
         * per-manga semantics as well.
         */
        private fun categoryIdOps(): List<Op<Boolean>> {
            val filter = categoryId ?: return emptyList()

            fun categoryRowsWhere(op: Op<Boolean>? = null) =
                CategoryMangaTable
                    .select(CategoryMangaTable.manga)
                    .where {
                        if (op == null) {
                            CategoryMangaTable.manga eq MangaTable.id
                        } else {
                            (CategoryMangaTable.manga eq MangaTable.id) and op
                        }
                    }

            val ops = mutableListOf<Op<Boolean>>()

            filter.isNull?.let { isNull ->
                ops += if (isNull) notExists(categoryRowsWhere()) else exists(categoryRowsWhere())
            }

            // membership predicates: the manga must have *a* category row satisfying them
            val positiveFilter =
                IntFilter(
                    equalTo = filter.equalTo,
                    notDistinctFrom = filter.notDistinctFrom,
                    `in` = filter.`in`,
                    lessThan = filter.lessThan,
                    lessThanOrEqualTo = filter.lessThanOrEqualTo,
                    greaterThan = filter.greaterThan,
                    greaterThanOrEqualTo = filter.greaterThanOrEqualTo,
                )
            andFilterWithCompareEntity(CategoryMangaTable.category, positiveFilter)?.let {
                ops += exists(categoryRowsWhere(it))
            }

            // single negations: the manga must have *no* category row matching them;
            // AND-ed exclusions are equivalent to one NOT EXISTS over their OR
            val noMatchOps = mutableListOf<Op<Boolean>>()
            filter.notEqualTo?.let { noMatchOps += CategoryMangaTable.category eq it }
            filter.notIn?.takeIf { it.isNotEmpty() }?.let { noMatchOps += CategoryMangaTable.category inList it }
            filter.notEqualToAny?.takeIf { it.isNotEmpty() }?.let { noMatchOps += CategoryMangaTable.category inList it }
            filter.distinctFrom?.let { noMatchOps += CategoryMangaTable.category eq it }
            filter.distinctFromAll?.takeIf { it.isNotEmpty() }?.let { noMatchOps += CategoryMangaTable.category inList it }
            noMatchOps.reduceOrNull { acc, op -> acc or op }?.let { ops += notExists(categoryRowsWhere(it)) }

            // OR-ed negations: at least one value must have no matching category row
            filter.notEqualToAll?.let { values ->
                val notExistsOps: List<Op<Boolean>> =
                    values.map { value -> notExists(categoryRowsWhere(CategoryMangaTable.category eq value)) }
                notExistsOps.reduceOrNull { acc, op -> acc or op }?.let { ops += it }
            }
            filter.distinctFromAny?.let { values ->
                val notExistsOps: List<Op<Boolean>> =
                    values.map { value -> notExists(categoryRowsWhere(CategoryMangaTable.category eq value)) }
                notExistsOps.reduceOrNull { acc, op -> acc or op }?.let { ops += it }
            }

            return ops
        }

        fun isFilteringForCategories(): Boolean =
            this.categoryId != null ||
                this.or?.any { it.isFilteringForCategories() } == true ||
                this.and?.any { it.isFilteringForCategories() } == true ||
                this.not?.isFilteringForCategories() == true
    }

    @RequireAuth
    fun mangas(
        @GraphQLIgnore
        userId: Int,
        dataFetchingEnvironment: DataFetchingEnvironment,
        condition: MangaCondition? = null,
        filter: MangaFilter? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderBy: MangaOrderBy? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderByType: SortOrder? = null,
        order: List<MangaOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): MangaNodeList {
        validatePagination(first, last, offset)

        val queryResults =
            transaction {
                val mangaIdsQuery =
                    MangaTable
                        .getWithUserData(userId)
                        .leftJoin(
                            CategoryMangaTable,
                            onColumn = { MangaTable.id },
                            otherColumn = { CategoryMangaTable.manga },
                            additionalConstraint = { CategoryMangaTable.user eq userId },
                        ).select(MangaTable.id)
                        .withDistinct()
                        .applyOps(condition, filter)

                val res =
                    if (condition?.categoryIds != null || filter?.isFilteringForCategories() == true) {
                        MangaTable.getWithUserData(userId).selectAll().where { MangaTable.id inSubQuery mangaIdsQuery }
                    } else {
                        MangaTable.getWithUserData(userId).selectAll().applyOps(condition, filter)
                    }

                val baseSort = listOf(MangaOrder(MangaOrderBy.ID, SortOrder.ASC))
                val deprecatedSort = listOfNotNull(orderBy?.let { MangaOrder(orderBy, orderByType) })
                val actualSort = (order.orEmpty() + deprecatedSort + baseSort)

                val (total, firstResult, lastResult) =
                    res.applySortAndGetPaginationInfo(
                        actualSort,
                        last,
                        MangaTable.id,
                        PaginationNeeds.of(dataFetchingEnvironment),
                    )

                // the cursor predicate and cursor producer use the *effective* first sort key,
                // which may also come from the deprecated orderBy/orderByType arguments
                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: orderBy ?: MangaOrderBy.ID,
                    orderByType = order?.firstOrNull()?.byType ?: orderByType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                // `last` fetches the tail in reversed order; flip it back to forward order
                val results = res.toList()
                QueryResults(total, firstResult, lastResult, if (last != null) results.asReversed() else results)
            }

        val getAsCursor: (MangaType) -> Cursor = (order?.firstOrNull()?.by ?: orderBy ?: MangaOrderBy.ID)::asCursor

        val resultsAsType = queryResults.results.map { MangaType(it) }

        return MangaNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        MangaNodeList.MangaEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        MangaNodeList.MangaEdge(
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
