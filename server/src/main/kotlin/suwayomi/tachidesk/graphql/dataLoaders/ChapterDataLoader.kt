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
import org.jetbrains.exposed.v1.core.Slf4jSqlDebugLogger
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
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

class DownloadedChapterCountForMangaDataLoader : KotlinDataLoader<Int, Int> {
    override val dataLoaderName = "DownloadedChapterCountForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Int> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val downloadedChapterCountByMangaId =
                        ChapterTable
                            .select(ChapterTable.manga, ChapterTable.isDownloaded.count())
                            .where {
                                (ChapterTable.manga inList ids) and
                                    (ChapterTable.isDownloaded eq true)
                            }.groupBy(ChapterTable.manga)
                            .associate { it[ChapterTable.manga].value to it[ChapterTable.isDownloaded.count()] }
                    ids.map { downloadedChapterCountByMangaId[it]?.toInt() ?: 0 }
                }
            }
        }
}

class UnreadChapterCountForMangaDataLoader : KotlinDataLoader<Int, Int> {
    override val dataLoaderName = "UnreadChapterCountForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Int> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val unreadChapterCountByMangaId =
                        ChapterTable
                            .select(ChapterTable.manga, ChapterTable.isRead.count())
                            .where {
                                (ChapterTable.manga inList ids) and
                                    (ChapterTable.isRead eq false)
                            }.groupBy(ChapterTable.manga)
                            .associate { it[ChapterTable.manga].value to it[ChapterTable.isRead.count()] }
                    ids.map { unreadChapterCountByMangaId[it]?.toInt() ?: 0 }
                }
            }
        }
}

class BookmarkedChapterCountForMangaDataLoader : KotlinDataLoader<Int, Int> {
    override val dataLoaderName = "BookmarkedChapterCountForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Int> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val bookmarkedChapterCountByMangaId =
                        ChapterTable
                            .select(ChapterTable.manga, ChapterTable.isBookmarked.count())
                            .where {
                                (ChapterTable.manga inList ids) and
                                    (ChapterTable.isBookmarked eq true)
                            }.groupBy(ChapterTable.manga)
                            .associate { it[ChapterTable.manga].value to it[ChapterTable.isBookmarked.count()] }
                    ids.map { bookmarkedChapterCountByMangaId[it]?.toInt() ?: 0 }
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
                            filter = ChapterFilter.IsRead,
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
                            filter = ChapterFilter.IsUnread,
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
                            filter = ChapterFilter.HasPositiveChapterNumber,
                            orderBy = listOf(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST),
                        )
                    ids.map { chaptersByMangaId[it] }
                }
            }
        }
}

/**
 * SQL sort direction strings for each [SortOrder] value.
 * Use in raw SQL ORDER BY clauses to avoid repeated `when` mapping.
 */
private val SortOrder.sql: String
    get() =
        when (this) {
            SortOrder.ASC -> "ASC"
            SortOrder.DESC -> "DESC"
            SortOrder.ASC_NULLS_FIRST -> "ASC NULLS FIRST"
            SortOrder.ASC_NULLS_LAST -> "ASC NULLS LAST"
            SortOrder.DESC_NULLS_FIRST -> "DESC NULLS FIRST"
            SortOrder.DESC_NULLS_LAST -> "DESC NULLS LAST"
        }

/**
 * Type-safe SQL filter conditions for [firstChapterPerManga].
 * Prevents SQL injection by restricting filters to known-safe literals.
 */
private sealed interface ChapterFilter {
    val sql: String

    data object IsRead : ChapterFilter {
        override val sql = "READ = TRUE"
    }

    data object IsUnread : ChapterFilter {
        override val sql = "READ = FALSE"
    }

    data object HasPositiveChapterNumber : ChapterFilter {
        override val sql = "chapter_number > 0"
    }
}

/**
 * Fetches at most one chapter per manga using a window function (ROW_NUMBER),
 * avoiding the previous pattern of loading all chapters and grouping in memory.
 * With appropriate indexes, this executes as an index scan returning only N rows
 * (one per manga) instead of all chapters for the requested manga.
 */
private fun firstChapterPerManga(
    mangaIds: List<Int>,
    orderBy: List<Pair<org.jetbrains.exposed.v1.core.Column<*>, SortOrder>>,
    filter: ChapterFilter? = null,
): Map<Int, ChapterType> {
    if (mangaIds.isEmpty()) return emptyMap()

    val orderClause = orderBy.joinToString(", ") { (col, order) -> "${col.name} ${order.sql}" }
    val placeholders = mangaIds.joinToString(",") { "?" }
    val filterClause = if (filter != null) " AND ${filter.sql}" else ""

    val sql =
        """
        SELECT id FROM (
            SELECT id, manga, ROW_NUMBER() OVER (PARTITION BY manga ORDER BY $orderClause) AS rn
            FROM CHAPTER
            WHERE manga IN ($placeholders)$filterClause
        ) ranked WHERE rn = 1
        """.trimIndent()

    val jdbcConn = (org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current() as org.jetbrains.exposed.v1.jdbc.JdbcTransaction).connection.connection as java.sql.Connection
    val targetIds = mutableListOf<Int>()
    jdbcConn.prepareStatement(sql).use { stmt ->
        mangaIds.forEachIndexed { index, id -> stmt.setInt(index + 1, id) }
        stmt.executeQuery().use { rs ->
            while (rs.next()) {
                targetIds.add(rs.getInt(1))
            }
        }
    }

    if (targetIds.isEmpty()) return emptyMap()

    return ChapterTable
        .selectAll()
        .where { ChapterTable.id inList targetIds }
        .associate { it[ChapterTable.manga].value to ChapterType(it) }
}
