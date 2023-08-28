/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import mu.KotlinLogging
import org.dataloader.CacheKey
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SortOrder.ASC
import org.jetbrains.exposed.sql.SortOrder.ASC_NULLS_FIRST
import org.jetbrains.exposed.sql.SortOrder.ASC_NULLS_LAST
import org.jetbrains.exposed.sql.SortOrder.DESC
import org.jetbrains.exposed.sql.SortOrder.DESC_NULLS_FIRST
import org.jetbrains.exposed.sql.SortOrder.DESC_NULLS_LAST
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.orWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.queries.ChapterQuery.BaseChapterCondition
import suwayomi.tachidesk.graphql.queries.ChapterQuery.ChapterFilter
import suwayomi.tachidesk.graphql.queries.ChapterQuery.ChapterOrderBy
import suwayomi.tachidesk.graphql.queries.filter.applyOps
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.server.primitives.maybeSwap
import suwayomi.tachidesk.graphql.types.ChapterNodeList
import suwayomi.tachidesk.graphql.types.ChapterNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.future

class ChapterDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "ChapterDataLoader"
    override fun getDataLoader(): DataLoader<Int, ChapterType?> = DataLoaderFactory.newDataLoader<Int, ChapterType> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val chapters = ChapterTable.select { ChapterTable.id inList ids }
                    .map { ChapterType(it) }
                    .associateBy { it.id }
                ids.map { chapters[it] }
            }
        }
    }
}

class ChaptersForMangaDataLoadesr : KotlinDataLoader<Int, ChapterNodeList> {
    override val dataLoaderName = "ChaptersForMangaDataLoadesr"
    override fun getDataLoader(): DataLoader<Int, ChapterNodeList> = DataLoaderFactory.newDataLoader<Int, ChapterNodeList> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val chaptersByMangaId = ChapterTable.select { ChapterTable.manga inList ids }
                    .map { ChapterType(it) }
                    .groupBy { it.mangaId }
                ids.map { (chaptersByMangaId[it] ?: emptyList()).toNodeList() }
            }
        }
    }
}

data class ChaptersContext<Condition : BaseChapterCondition>(
    val condition: Condition? = null,
    val filter: ChapterFilter? = null,
    val orderBy: ChapterOrderBy? = null,
    val orderByType: SortOrder? = null,
    val before: Cursor? = null,
    val after: Cursor? = null,
    val first: Int? = null,
    val last: Int? = null,
    val offset: Int? = null
)

/**
 * This data loader requires a context to be passed, if it is missing a NullPointerException will be thrown
 */
class ChaptersForMangaDataLoader : KotlinDataLoader<Int, ChapterNodeList> {
    override val dataLoaderName = "ChaptersForMangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, ChapterNodeList> = DataLoaderFactory.newDataLoader<Int, ChapterNodeList> (
        { ids, env ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)

                    KotlinLogging.logger { }.info { "@Daniel chapters loader <-> ids $ids" }
                    KotlinLogging.logger { }.info { "@Daniel chapters loader <-> context ${env.getContext<Any>()}" }
                    KotlinLogging.logger { }.info { "@Daniel chapters loader <-> keyContexts ${env.keyContexts}" }
                    KotlinLogging.logger { }.info { "@Daniel chapters loader <-> keyContextsList ${env.keyContextsList}" }

                    // ids [1, 1, 2, 3, 4, 4, 1]
                    // idSets => [[(1, ctx), (2, ctx), (3, ctx), (4, ctx)], [(1, ctx), (4, ctx)], [(1, ctx)]]
                    //
                    // (id)                                                         1       1       2       3       4       4       1
                    // id to list to list index [(list index, index in list)] => [(0, 0), (1, 0), (0, 1), (0, 2), (0, 3), (1, 2), (2, 0)]

                    // create sets of ids, which can be queried together - since the same id can have different contexts, these can not be queried together
                    val listOfIdCtxSets = mutableListOf<MutableList<Pair<Int, ChaptersContext<*>>>>()
                    val idToListToListIndexMap = mutableListOf<Pair<Int, Int>>()
                    ids.forEachIndexed { idIndex, id ->
                        var inserted = false
                        val idCtxPair = Pair(id, env.keyContextsList[idIndex] as ChaptersContext<*>)

                        for (idSet in listOfIdCtxSets) {
                            val idSetIndex = listOfIdCtxSets.indexOf(idSet)

                            val isDuplicate = idSet.any { (idInIdSet) -> idInIdSet == id }
                            if (!isDuplicate) {
                                idToListToListIndexMap.add(Pair(idSetIndex, idSet.size))

                                idSet.add(idCtxPair)
                                inserted = true
                                break
                            }
                        }

                        if (!inserted) {
                            idToListToListIndexMap.add(Pair(listOfIdCtxSets.size, 0))
                            listOfIdCtxSets.add(mutableListOf(idCtxPair))
                        }
                    }

                    KotlinLogging.logger { }.info { "@Daniel listOfIdSets $listOfIdCtxSets" }
                    KotlinLogging.logger { }.info { "@Daniel idToListToListIndexMap $idToListToListIndexMap" }

                    val result = listOfIdCtxSets.map { idCtxSet ->
                        val idSetIds = idCtxSet.map { it.first }

                        val query = ChapterTable.select { ChapterTable.manga inList idSetIds }
                        // filter chapters for each manga
                        idCtxSet.forEach { (id, ctx) ->
                            val (condition, filter) = ctx
                            query.orWhere { ChapterTable.manga eq id }.applyOps(condition, filter)
                        }

                        val mangaToChapterRowsMap = query
                            .groupBy { it[ChapterTable.manga].value }

                        idSetIds.map { mangaId ->
                            val chapterRows = mangaToChapterRowsMap[mangaId] ?: emptyList()
                            val (_, _, orderBy, orderByType, before, after, first, last, offset) = idCtxSet.find { it.first == mangaId }!!.second

                            val sortedChapterRows = if (orderBy != null || (last != null || before != null)) {
                                val orderByColumn = orderBy?.column ?: ChapterTable.id
                                val orderType = orderByType.maybeSwap(last ?: before)

                                if (orderBy == ChapterOrderBy.ID || orderBy == null) {
                                    chapterRows.sortedBy { it[orderByColumn] as Comparable<Any> }
                                } else {
                                    when (orderType) {
                                        ASC -> chapterRows.sortedWith(compareBy({ it[orderByColumn] }, { it[ChapterTable.id] }))
                                        DESC -> chapterRows.sortedWith(compareBy<ResultRow> { it[orderByColumn] }.reversed().thenBy { it[ChapterTable.id] })
                                        ASC_NULLS_FIRST -> chapterRows.sortedWith(compareBy<ResultRow, Comparable<Any>>(nullsFirst()) { it[orderByColumn] as Comparable<Any> }.thenBy { it[ChapterTable.id] })
                                        DESC_NULLS_FIRST -> chapterRows.sortedWith(compareBy<ResultRow, Comparable<Any>>(nullsFirst()) { it[orderByColumn] as Comparable<Any> }.reversed().thenBy { it[ChapterTable.id] })
                                        ASC_NULLS_LAST -> chapterRows.sortedWith(compareBy<ResultRow, Comparable<Any>>(nullsLast()) { it[orderByColumn] as Comparable<Any> }.thenBy { it[ChapterTable.id] })
                                        DESC_NULLS_LAST -> chapterRows.sortedWith(compareBy<ResultRow, Comparable<Any>>(nullsLast()) { it[orderByColumn] as Comparable<Any> }.reversed().thenBy { it[ChapterTable.id] })
                                    }
                                }
                            } else {
                                chapterRows
                            }

                            val total = sortedChapterRows.size
                            val firstResult = sortedChapterRows.firstOrNull()?.get(ChapterTable.id)?.value
                            val lastResult = sortedChapterRows.lastOrNull()?.get(ChapterTable.id)?.value

                            var paginatedChapterRows = if (after != null) {
                                val afterIndex = sortedChapterRows.indexOfFirst { chapter -> chapter[ChapterTable.id].value == after.value.toInt() }

                                if (sortedChapterRows.size - 1 < afterIndex) {
                                    emptyList()
                                } else {
                                    sortedChapterRows.subList(afterIndex, -1)
                                }
                            } else if (before != null) {
                                val beforeIndex = sortedChapterRows.indexOfFirst { chapter -> chapter[ChapterTable.id].value == before.value.toInt() }
                                sortedChapterRows.subList(0, (sortedChapterRows.size - 1).coerceAtMost(beforeIndex))
                            } else {
                                sortedChapterRows
                            }

                            paginatedChapterRows = if (first != null) {
                                if (paginatedChapterRows.isEmpty()) {
                                    emptyList()
                                } else {
                                    paginatedChapterRows.subList(
                                        (paginatedChapterRows.size - 1).coerceAtMost(offset ?: 0),
                                        (paginatedChapterRows.size - 1).coerceAtMost(first)
                                    )
                                }
                            } else if (last != null) {
                                paginatedChapterRows.takeLast(last)
                            } else {
                                paginatedChapterRows
                            }

                            val queryResults = QueryResults(total.toLong(), firstResult, lastResult, paginatedChapterRows)

                            val getAsCursor: (ChapterType) -> Cursor = (orderBy ?: ChapterOrderBy.ID)::asCursor

                            val resultsAsType = queryResults.results.map { ChapterType(it) }

                            ChapterNodeList(
                                resultsAsType,
                                if (resultsAsType.isEmpty()) {
                                    emptyList()
                                } else {
                                    listOfNotNull(
                                        resultsAsType.firstOrNull()?.let {
                                            ChapterNodeList.ChapterEdge(
                                                getAsCursor(it),
                                                it
                                            )
                                        },
                                        resultsAsType.lastOrNull()?.let {
                                            ChapterNodeList.ChapterEdge(
                                                getAsCursor(it),
                                                it
                                            )
                                        }
                                    )
                                },
                                pageInfo = PageInfo(
                                    hasNextPage = queryResults.lastKey != resultsAsType.lastOrNull()?.id,
                                    hasPreviousPage = queryResults.firstKey != resultsAsType.firstOrNull()?.id,
                                    startCursor = resultsAsType.firstOrNull()?.let { getAsCursor(it) },
                                    endCursor = resultsAsType.lastOrNull()?.let { getAsCursor(it) }
                                ),
                                totalCount = queryResults.total.toInt()
                            )
                        }
                    }

                    idToListToListIndexMap.map { (idSetIndex, idSetIdIndex) ->
                        result[idSetIndex][idSetIdIndex]
                    }
                }
            }
        },
        DataLoaderOptions.newOptions().setCacheKeyFunction(
            object : CacheKey<Int> {
                override fun getKey(input: Int): String {
                    return input.toString()
                }

                override fun getKeyWithContext(input: Int, context: Any): String {
                    return "${input}_$context"
                }
            }
        )
    )
}
