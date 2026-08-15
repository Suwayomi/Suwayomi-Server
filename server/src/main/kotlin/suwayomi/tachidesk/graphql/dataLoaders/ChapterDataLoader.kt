/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Slf4jSqlDebugLogger
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterNodeList
import suwayomi.tachidesk.graphql.types.ChapterNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.future

class ChapterDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "ChapterDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chapters =
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.id inList ids }
                            .map { ChapterType(it) }
                            .associateBy { it.id }
                    ids.map { chapters[it] }
                }
            }
        }
}

class ChaptersForMangaDataLoader : KotlinDataLoader<Int, ChapterNodeList> {
    override val dataLoaderName = "ChaptersForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.manga inList ids }
                            .map { ChapterType(it) }
                            .groupBy { it.mangaId }
                    ids.map { (chaptersByMangaId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

data class MangaChapterStats(
    val unreadCount: Int,
    val downloadCount: Int,
    val bookmarkCount: Int,
)

class ChapterFlagCountForMangaDataLoader : KotlinDataLoader<Int, MangaChapterStats> {
    override val dataLoaderName = "ChapterFlagCountForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, MangaChapterStats> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)

                    val unreadCount =
                        Case()
                            .When(ChapterTable.isRead eq false, intLiteral(1))
                            .Else(intLiteral(0))
                            .sum()

                    val downloadCount =
                        Case()
                            .When(ChapterTable.isDownloaded eq true, intLiteral(1))
                            .Else(intLiteral(0))
                            .sum()

                    val bookmarkCount =
                        Case()
                            .When(ChapterTable.isBookmarked eq true, intLiteral(1))
                            .Else(intLiteral(0))
                            .sum()

                    val statsByMangaId =
                        ChapterTable
                            .select(
                                ChapterTable.manga,
                                unreadCount,
                                downloadCount,
                                bookmarkCount,
                            ).where {
                                ChapterTable.manga inList ids
                            }.groupBy(ChapterTable.manga)
                            .associate {
                                val mangaId = it[ChapterTable.manga].value

                                mangaId to
                                    MangaChapterStats(
                                        unreadCount = it[unreadCount] ?: 0,
                                        downloadCount = it[downloadCount] ?: 0,
                                        bookmarkCount = it[bookmarkCount] ?: 0,
                                    )
                            }

                    ids.map {
                        statsByMangaId[it] ?: MangaChapterStats(
                            unreadCount = 0,
                            downloadCount = 0,
                            bookmarkCount = 0,
                        )
                    }
                }
            }
        }
}

class HasDuplicateChaptersForMangaDataLoader : KotlinDataLoader<Int, Boolean> {
    override val dataLoaderName = "HasDuplicateChaptersForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Boolean> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val duplicatedChapterCountByMangaId =
                        ChapterTable
                            .select(ChapterTable.manga, ChapterTable.chapter_number, ChapterTable.chapter_number.count())
                            .where {
                                (
                                    ChapterTable.manga inList
                                        ids
                                ) and
                                    (ChapterTable.chapter_number greaterEq 0f)
                            }.groupBy(ChapterTable.manga, ChapterTable.chapter_number)
                            .having { ChapterTable.chapter_number.count() greater 1 }
                            .associate { it[ChapterTable.manga].value to it[ChapterTable.chapter_number.count()] }

                    ids.map { duplicatedChapterCountByMangaId.contains(it) }
                }
            }
        }
}

class LastReadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LastReadChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chaptersByMangaId =
                        firstChapterPerManga(
                            mangaIds = ids,
                            orderBy = listOf(ChapterTable.lastReadAt to SortOrder.DESC),
                        )
                    ids.map { chaptersByMangaId[it] }
                }
            }
        }
}

class LatestReadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LatestReadChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chaptersByMangaId =
                        firstChapterPerManga(
                            mangaIds = ids,
                            filter = ChapterTable.isRead eq true,
                            orderBy = listOf(ChapterTable.sourceOrder to SortOrder.DESC),
                        )
                    ids.map { chaptersByMangaId[it] }
                }
            }
        }
}

class LatestFetchedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LatestFetchedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chaptersByMangaId =
                        firstChapterPerManga(
                            mangaIds = ids,
                            orderBy = listOf(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                        )
                    ids.map { chaptersByMangaId[it] }
                }
            }
        }
}

class LatestUploadedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LatestUploadedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chaptersByMangaId =
                        firstChapterPerManga(
                            mangaIds = ids,
                            orderBy = listOf(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                        )
                    ids.map { chaptersByMangaId[it] }
                }
            }
        }
}

class FirstUnreadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "FirstUnreadChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chaptersByMangaId =
                        firstChapterPerManga(
                            mangaIds = ids,
                            filter = ChapterTable.isRead eq false,
                            orderBy = listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                        )
                    ids.map { chaptersByMangaId[it] }
                }
            }
        }
}

class HighestNumberedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "HighestNumberedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val chaptersByMangaId =
                        firstChapterPerManga(
                            mangaIds = ids,
                            filter = ChapterTable.chapter_number greater 0f,
                            orderBy = listOf(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST),
                        )
                    ids.map { chaptersByMangaId[it] }
                }
            }
        }
}

/**
 * Fetches at most one chapter per manga using Exposed's ROW_NUMBER() window function,
 * avoiding the previous pattern of loading all chapters and grouping in memory.
 * With appropriate indexes, this executes as an index scan returning only N rows
 * (one per manga) instead of all chapters for the requested manga.
 */
private fun firstChapterPerManga(
    mangaIds: List<Int>,
    orderBy: List<Pair<org.jetbrains.exposed.v1.core.Column<*>, SortOrder>>,
    filter: Op<Boolean>? = null,
): Map<Int, ChapterType> {
    if (mangaIds.isEmpty()) return emptyMap()

    val rn =
        rowNumber()
            .over()
            .partitionBy(ChapterTable.manga)
            .orderBy(*orderBy.toTypedArray())
            .alias("rn")

    val baseCondition = ChapterTable.manga inList mangaIds
    val fullCondition = if (filter != null) baseCondition and filter else baseCondition

    val ranked =
        ChapterTable
            .select(ChapterTable.columns + rn)
            .where { fullCondition }
            .alias("ranked")

    val targetIds =
        ranked
            .select(ranked[ChapterTable.id])
            .where { ranked[rn] eq longLiteral(1) }
            .map { it[ranked[ChapterTable.id]].value }

    if (targetIds.isEmpty()) return emptyMap()

    return ChapterTable
        .selectAll()
        .where { ChapterTable.id inList targetIds }
        .associate { it[ChapterTable.manga].value to ChapterType(it) }
}
