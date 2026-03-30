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
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterNodeList
import suwayomi.tachidesk.graphql.types.ChapterNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.server.JavalinSetup.future

class ChapterDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "ChapterDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType?> =
        DataLoaderFactory.newDataLoader<Int, ChapterType> { ids ->
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
        DataLoaderFactory.newDataLoader<Int, ChapterNodeList> { ids ->
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
        DataLoaderFactory.newDataLoader<Int, Int> { ids ->
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
        DataLoaderFactory.newDataLoader<Int, Int> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val excludedScanlatorsByMangaId = loadExcludedScanlators(ids)
                    val unreadChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where {
                                (ChapterTable.manga inList ids) and
                                    (ChapterTable.isRead eq false)
                            }
                            .groupBy { it[ChapterTable.manga].value }
                            .mapValues { (mangaId, rows) ->
                                val excluded = excludedScanlatorsByMangaId[mangaId].orEmpty()
                                rows.count { row ->
                                    val scanlator = row[ChapterTable.scanlator]
                                    scanlator == null || scanlator !in excluded
                                }
                            }
                    ids.map { mangaId -> unreadChaptersByMangaId[mangaId] ?: 0 }
                }
            }
        }
}
class BookmarkedChapterCountForMangaDataLoader : KotlinDataLoader<Int, Int> {
    override val dataLoaderName = "BookmarkedChapterCountForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Int> =
        DataLoaderFactory.newDataLoader<Int, Int> { ids ->
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

class LastReadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "LastReadChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType?> =
        DataLoaderFactory.newDataLoader<Int, ChapterType?> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val lastReadChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) }
                            .orderBy(ChapterTable.lastReadAt to SortOrder.DESC)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id -> lastReadChaptersByMangaId[id]?.let { chapters -> ChapterType(chapters.first()) } }
                }
            }
        }
}

class LatestReadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "LatestReadChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType?> =
        DataLoaderFactory.newDataLoader<Int, ChapterType?> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val latestReadChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) and (ChapterTable.isRead eq true) }
                            .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id -> latestReadChaptersByMangaId[id]?.let { chapters -> ChapterType(chapters.first()) } }
                }
            }
        }
}

class LatestFetchedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "LatestFetchedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType?> =
        DataLoaderFactory.newDataLoader<Int, ChapterType?> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val latestFetchedChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) }
                            .orderBy(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id -> latestFetchedChaptersByMangaId[id]?.let { chapters -> ChapterType(chapters.first()) } }
                }
            }
        }
}

class LatestUploadedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "LatestUploadedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType?> =
        DataLoaderFactory.newDataLoader<Int, ChapterType?> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val latestUploadedChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) }
                            .orderBy(ChapterTable.date_upload to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id -> latestUploadedChaptersByMangaId[id]?.let { chapters -> ChapterType(chapters.first()) } }
                }
            }
        }
}

class FirstUnreadChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "FirstUnreadChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType?> =
        DataLoaderFactory.newDataLoader<Int, ChapterType?> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val excludedScanlatorsByMangaId = loadExcludedScanlators(ids)
                    val unreadChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where {
                                (ChapterTable.manga inList ids) and
                                    (ChapterTable.isRead eq false)
                            }
                            .orderBy(ChapterTable.sourceOrder to SortOrder.ASC)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { mangaId ->
                        val excluded = excludedScanlatorsByMangaId[mangaId].orEmpty()
                        unreadChaptersByMangaId[mangaId]
                            ?.firstOrNull { row ->
                                val scanlator = row[ChapterTable.scanlator]
                                scanlator == null || scanlator !in excluded
                            }?.let { ChapterType(it) }
                    }
                }
            }
        }
}

class HighestNumberedChapterForMangaDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "HighestNumberedChapterForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, ChapterType?> =
        DataLoaderFactory.newDataLoader<Int, ChapterType?> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val highestNumberedChaptersByMangaId =
                        ChapterTable
                            .selectAll()
                            .where { (ChapterTable.manga inList ids) and (ChapterTable.chapter_number greater 0f) }
                            .orderBy(ChapterTable.chapter_number to SortOrder.DESC_NULLS_LAST)
                            .groupBy { it[ChapterTable.manga].value }
                    ids.map { id ->
                        highestNumberedChaptersByMangaId[id]
                            ?.firstOrNull()
                            ?.let { chapter -> ChapterType(chapter) }
                    }
                }
            }
        }
}

class ExcludedScanlatorsForMangaDataLoader : KotlinDataLoader<Int, List<String>> {
    override val dataLoaderName = "ExcludedScanlatorsForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<String>> =
        DataLoaderFactory.newDataLoader<Int, List<String>> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val rowsByMangaId =
                        MangaMetaTable
                            .selectAll()
                            .where {
                                (MangaMetaTable.ref inList ids) and
                                    (MangaMetaTable.key eq EXCLUDED_SCANLATORS_META_KEY)
                            }
                            .groupBy { it[MangaMetaTable.ref].value }
                    ids.map { mangaId ->
                        rowsByMangaId[mangaId]
                            ?.flatMap { parseExcludedScanlators(it[MangaMetaTable.value]) }
                            ?.distinct()
                            ?: emptyList()
                    }
                }
            }
        }
}

internal const val EXCLUDED_SCANLATORS_META_KEY = "webUI_excludedScanlators"

internal fun loadExcludedScanlators(mangaIds: List<Int>): Map<Int, Set<String>> {
    if (mangaIds.isEmpty()) return emptyMap()
    return MangaMetaTable
        .selectAll()
        .where {
            (MangaMetaTable.ref inList mangaIds) and
                (MangaMetaTable.key eq EXCLUDED_SCANLATORS_META_KEY)
        }
        .groupBy { it[MangaMetaTable.ref].value }
        .mapValues { (_, rows) ->
            rows.flatMap { parseExcludedScanlators(it[MangaMetaTable.value]) }.toSet()
        }
}

private fun parseExcludedScanlators(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val trimmed = raw.trim()
    if (trimmed.length < 2 || trimmed.first() != '[' || trimmed.last() != ']') return emptyList()
    return trimmed
        .substring(1, trimmed.length - 1)
        .split(',')
        .mapNotNull { token ->
            val value = token.trim().trim('"')
            value.ifEmpty { null }
        }
}