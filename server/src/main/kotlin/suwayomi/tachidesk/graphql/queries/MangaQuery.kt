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
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.IntFilter
import suwayomi.tachidesk.graphql.queries.filter.LongFilter
import suwayomi.tachidesk.graphql.queries.filter.OpAnd
import suwayomi.tachidesk.graphql.queries.filter.StringFilter
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompare
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareEntity
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareString
import suwayomi.tachidesk.graphql.queries.filter.getOp
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 *
 * TODO Mutations
 * - Favorite
 * - Unfavorite
 * - Add to category
 * - Remove from category
 * - Check for updates
 * - Download x(all = -1) chapters
 * - Delete read/all downloaded chapters
 * - Add/update meta
 * - Delete meta
 */
class MangaQuery {
    fun manga(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<MangaType?> {
        return dataFetchingEnvironment.getValueFromDataLoader("MangaDataLoader", id)
    }

    enum class MangaOrderBy {
        ID,
        TITLE,
        IN_LIBRARY_AT,
        LAST_FETCHED_AT
    }

    private fun getAsCursor(orderBy: MangaOrderBy?, type: MangaType): Cursor {
        val value = when (orderBy) {
            MangaOrderBy.ID, null -> type.id.toString()
            MangaOrderBy.TITLE -> type.title
            MangaOrderBy.IN_LIBRARY_AT -> type.inLibraryAt.toString()
            MangaOrderBy.LAST_FETCHED_AT -> type.lastFetchedAt.toString()
        }
        return Cursor(value)
    }

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
        var lastFetchedAt: Long? = null,
        var chaptersLastFetchedAt: Long? = null
    ) {
        fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            fun <T> eq(value: T?, column: Column<T>) = opAnd.andWhere(value) { column eq it }
            fun <T : Comparable<T>> eq(value: T?, column: Column<EntityID<T>>) = opAnd.andWhere(value) { column eq it }
            eq(id, MangaTable.id)
            eq(sourceId, MangaTable.sourceReference)
            eq(url, MangaTable.url)
            eq(title, MangaTable.title)
            eq(thumbnailUrl, MangaTable.thumbnail_url)
            eq(initialized, MangaTable.initialized)
            eq(artist, MangaTable.artist)
            eq(author, MangaTable.author)
            eq(description, MangaTable.description)
            eq(genre?.joinToString(), MangaTable.genre)
            eq(status?.value, MangaTable.status)
            eq(inLibrary, MangaTable.inLibrary)
            eq(inLibraryAt, MangaTable.inLibraryAt)
            eq(realUrl, MangaTable.realUrl)
            eq(lastFetchedAt, MangaTable.lastFetchedAt)
            eq(chaptersLastFetchedAt, MangaTable.chaptersLastFetchedAt)

            return opAnd.op
        }
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
        // val genre: List<String>? = null, // todo
        // val status: MangaStatus? = null, // todo
        val inLibrary: BooleanFilter? = null,
        val inLibraryAt: LongFilter? = null,
        val realUrl: StringFilter? = null,
        var lastFetchedAt: LongFilter? = null,
        var chaptersLastFetchedAt: LongFilter? = null,
        val category: IntFilter? = null,
        override val and: List<MangaFilter>? = null,
        override val or: List<MangaFilter>? = null,
        override val not: MangaFilter? = null
    ) : Filter<MangaFilter> {
        override fun getOpList(): List<Op<Boolean>> {
            return listOfNotNull(
                andFilterWithCompareEntity(MangaTable.id, id),
                andFilterWithCompare(MangaTable.sourceReference, sourceId),
                andFilterWithCompareString(MangaTable.url, url),
                andFilterWithCompareString(MangaTable.title, title),
                andFilterWithCompareString(MangaTable.thumbnail_url, thumbnailUrl),
                andFilterWithCompare(MangaTable.initialized, initialized),
                andFilterWithCompareString(MangaTable.artist, artist),
                andFilterWithCompareString(MangaTable.author, author),
                andFilterWithCompareString(MangaTable.description, description),
                andFilterWithCompare(MangaTable.inLibrary, inLibrary),
                andFilterWithCompare(MangaTable.inLibraryAt, inLibraryAt),
                andFilterWithCompareString(MangaTable.realUrl, realUrl),
                andFilterWithCompare(MangaTable.inLibraryAt, lastFetchedAt),
                andFilterWithCompare(MangaTable.inLibraryAt, chaptersLastFetchedAt)
            )
        }

        fun getCategoryOp() = andFilterWithCompareEntity(CategoryMangaTable.category, category)
    }

    fun mangas(
        condition: MangaCondition? = null,
        filter: MangaFilter? = null,
        orderBy: MangaOrderBy? = null,
        orderByType: SortOrder? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null
    ): MangaNodeList {
        val queryResults = transaction {
            var res = MangaTable.selectAll()

            val categoryOp = filter?.getCategoryOp()
            if (categoryOp != null) {
                res = MangaTable.innerJoin(CategoryMangaTable)
                    .select { categoryOp }
            }
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
                    MangaOrderBy.ID, null -> MangaTable.id
                    MangaOrderBy.TITLE -> MangaTable.title
                    MangaOrderBy.IN_LIBRARY_AT -> MangaTable.inLibraryAt
                    MangaOrderBy.LAST_FETCHED_AT -> MangaTable.lastFetchedAt
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
            val firstResult = res.first()[MangaTable.id].value
            val lastResult = res.last()[MangaTable.id].value

            if (after != null) {
                when (orderBy) {
                    MangaOrderBy.ID, null -> res.andWhere {
                        MangaTable.id greater after.value.toInt()
                    }
                    MangaOrderBy.TITLE -> res.andWhere {
                        MangaTable.title greater after.value
                    }
                    MangaOrderBy.IN_LIBRARY_AT -> res.andWhere {
                        MangaTable.inLibraryAt greater after.value.toLong()
                    }
                    MangaOrderBy.LAST_FETCHED_AT -> res.andWhere {
                        MangaTable.lastFetchedAt greater after.value.toLong()
                    }
                }
            } else if (before != null) {
                when (orderBy) {
                    MangaOrderBy.ID, null -> res.andWhere {
                        MangaTable.id less before.value.toInt()
                    }
                    MangaOrderBy.TITLE -> res.andWhere {
                        MangaTable.title less before.value
                    }
                    MangaOrderBy.IN_LIBRARY_AT -> res.andWhere {
                        MangaTable.inLibraryAt less before.value.toLong()
                    }
                    MangaOrderBy.LAST_FETCHED_AT -> res.andWhere {
                        MangaTable.lastFetchedAt less before.value.toLong()
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

        val resultsAsType = queryResults.results.map { MangaType(it) }

        return MangaNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    resultsAsType.first().let {
                        MangaNodeList.MangaEdge(
                            getAsCursor(orderBy, it),
                            it
                        )
                    },
                    resultsAsType.last().let {
                        MangaNodeList.MangaEdge(
                            getAsCursor(orderBy, it),
                            it
                        )
                    }
                )
            },
            pageInfo = PageInfo(
                hasNextPage = queryResults.lastKey != resultsAsType.last().id,
                hasPreviousPage = queryResults.firstKey != resultsAsType.first().id,
                startCursor = resultsAsType.firstOrNull()?.let { getAsCursor(orderBy, it) },
                endCursor = resultsAsType.lastOrNull()?.let { getAsCursor(orderBy, it) }
            ),
            totalCount = queryResults.total.toInt()
        )
    }
}
