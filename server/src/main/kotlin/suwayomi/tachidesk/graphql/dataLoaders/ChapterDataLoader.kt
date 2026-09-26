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
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
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

/**
 * For each manga in [mangaIds], its first chapter by [order] among those
 * matching [where], ties going to the lowest id.
 *
 * Ranked in SQL (ROW_NUMBER over each manga), so a single row per manga
 * leaves the database. Loading every matching chapter of every manga to keep
 * the first one scaled with the size of the whole chapter table, and a
 * library list resolves several of these per manga.
 */
internal fun firstChapterPerManga(
    mangaIds: List<Int>,
    order: List<Pair<Expression<*>, SortOrder>>,
    where: Op<Boolean>? = null,
): Map<Int, ChapterType> {
    val rank =
        rowNumber()
            .over()
            .partitionBy(ChapterTable.manga)
            .orderBy(*(order + (ChapterTable.id to SortOrder.ASC)).toTypedArray())
            .alias("chapter_rank")
    val ranked =
        ChapterTable
            .select(ChapterTable.id, rank)
            .where {
                val inMangas = ChapterTable.manga inList mangaIds
                if (where == null) inMangas else inMangas and where
            }.alias("ranked_chapter")
    return ChapterTable
        .join(
            ranked,
            JoinType.INNER,
            additionalConstraint = {
                (ChapterTable.id eq ranked[ChapterTable.id]) and (ranked[rank] eq 1L)
            },
        ).select(ChapterTable.columns)
        .associate { it[ChapterTable.manga].value to ChapterType(it) }
}

class LastReadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType> {
    override val dataLoaderName = "LastReadChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val lastReadChapterByMangaId =
                        firstChapterPerManga(ids, listOf(ChapterTable.lastReadAt to SortOrder.DESC))
                    ids.map { lastReadChapterByMangaId[it] }
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
                    val latestReadChapterByMangaId =
                        firstChapterPerManga(
                            ids,
                            listOf(ChapterTable.sourceOrder to SortOrder.DESC),
                            ChapterTable.isRead eq true,
                        )
                    ids.map { latestReadChapterByMangaId[it] }
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
                    val latestFetchedChapterByMangaId =
                        firstChapterPerManga(
                            ids,
                            listOf(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                        )
                    ids.map { latestFetchedChapterByMangaId[it] }
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
                    val latestUploadedChapterByMangaId =
                        firstChapterPerManga(
                            ids,
                            listOf(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC),
                        )
                    ids.map { latestUploadedChapterByMangaId[it] }
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
                    val firstUnreadChapterByMangaId =
                        firstChapterPerManga(
                            ids,
                            listOf(ChapterTable.sourceOrder to SortOrder.ASC),
                            ChapterTable.isRead eq false,
                        )
                    ids.map { firstUnreadChapterByMangaId[it] }
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
                    val highestNumberedChapterByMangaId =
                        firstChapterPerManga(
                            ids,
                            listOf(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST),
                            ChapterTable.chapter_number greater 0f,
                        )
                    ids.map { highestNumberedChapterByMangaId[it] }
                }
            }
        }
}
