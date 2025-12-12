/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderChapterType
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.graphql.types.IReaderPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.IReaderNovelTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderSourceMutation {
    enum class FetchIReaderNovelType {
        SEARCH,
        POPULAR,
        LATEST,
    }

    data class FetchIReaderNovelsInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val source: Long,
        @GraphQLDescription("Type of fetch operation")
        val type: FetchIReaderNovelType,
        @GraphQLDescription("Page number (1-indexed)")
        val page: Int,
        @GraphQLDescription("Search query (required for SEARCH type)")
        val query: String? = null,
    )

    data class FetchIReaderNovelsPayload(
        val clientMutationId: String?,
        val novels: List<IReaderNovelType>,
        val hasNextPage: Boolean,
    )

    @RequireAuth
    @GraphQLDescription("Fetch novels from an IReader source")
    fun fetchIReaderNovels(input: FetchIReaderNovelsInput): CompletableFuture<DataFetcherResult<FetchIReaderNovelsPayload?>> {
        val (clientMutationId, sourceId, type, page, query) = input

        return future {
            asDataFetcherResult {
                require(page > 0) { "Page must be greater than 0" }

                val novelsPage =
                    when (type) {
                        FetchIReaderNovelType.SEARCH -> {
                            require(!query.isNullOrBlank()) { "Query is required for SEARCH type" }
                            IReaderNovel.searchNovels(sourceId, query, page)
                        }
                        FetchIReaderNovelType.POPULAR -> {
                            IReaderNovel.getPopularNovels(sourceId, page)
                        }
                        FetchIReaderNovelType.LATEST -> {
                            IReaderNovel.getLatestNovels(sourceId, page)
                        }
                    }

                FetchIReaderNovelsPayload(
                    clientMutationId = clientMutationId,
                    novels = novelsPage.mangas.map { IReaderNovelType(it, sourceId) },
                    hasNextPage = novelsPage.hasNextPage,
                )
            }
        }
    }

    data class FetchIReaderChaptersInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID (required if using novelUrl)")
        val source: Long? = null,
        @GraphQLDescription("Novel URL/key from the source (provide this OR novelId)")
        val novelUrl: String? = null,
        @GraphQLDescription("Database novel ID (provide this OR novelUrl + source)")
        val novelId: Int? = null,
    )

    data class FetchIReaderChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<IReaderChapterType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch chapters for a novel from an IReader source")
    fun fetchIReaderChapters(input: FetchIReaderChaptersInput): CompletableFuture<DataFetcherResult<FetchIReaderChaptersPayload?>> {
        val (clientMutationId, source, novelUrl, novelId) = input

        return future {
            asDataFetcherResult {
                val (resolvedSourceId, resolvedNovelUrl) =
                    when {
                        novelId != null -> {
                            // Look up novel from database
                            val novel =
                                transaction {
                                    IReaderNovelTable
                                        .selectAll()
                                        .where { IReaderNovelTable.id eq novelId }
                                        .firstOrNull()
                                } ?: throw IllegalArgumentException("Novel with ID $novelId not found")
                            novel[IReaderNovelTable.sourceReference] to novel[IReaderNovelTable.url]
                        }
                        novelUrl != null && source != null -> {
                            require(novelUrl.isNotBlank()) { "Novel URL cannot be empty" }
                            source to novelUrl
                        }
                        else -> throw IllegalArgumentException("Either novelId OR (novelUrl + source) must be provided")
                    }

                val chapters = IReaderNovel.getChapterList(resolvedSourceId, resolvedNovelUrl)

                FetchIReaderChaptersPayload(
                    clientMutationId = clientMutationId,
                    chapters = chapters.map { IReaderChapterType(it) },
                )
            }
        }
    }

    data class FetchIReaderChapterContentInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID (required if using chapterUrl)")
        val source: Long? = null,
        @GraphQLDescription("Chapter URL/key from the source (provide this OR chapterId)")
        val chapterUrl: String? = null,
        @GraphQLDescription("Database chapter ID (provide this OR chapterUrl + source)")
        val chapterId: Int? = null,
    )

    data class FetchIReaderChapterContentPayload(
        val clientMutationId: String?,
        val pages: List<IReaderPageType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch content/pages for a chapter from an IReader source")
    fun fetchIReaderChapterContent(input: FetchIReaderChapterContentInput): CompletableFuture<DataFetcherResult<FetchIReaderChapterContentPayload?>> {
        val (clientMutationId, source, chapterUrl, chapterId) = input

        return future {
            asDataFetcherResult {
                val (resolvedSourceId, resolvedChapterUrl) =
                    when {
                        chapterId != null -> {
                            // Look up chapter from database and get source from associated manga
                            val chapterWithManga =
                                transaction {
                                    (ChapterTable innerJoin MangaTable)
                                        .selectAll()
                                        .where { ChapterTable.id eq chapterId }
                                        .firstOrNull()
                                } ?: throw IllegalArgumentException("Chapter with ID $chapterId not found")
                            chapterWithManga[MangaTable.sourceReference] to chapterWithManga[ChapterTable.url]
                        }
                        chapterUrl != null && source != null -> {
                            require(chapterUrl.isNotBlank()) { "Chapter URL cannot be empty" }
                            source to chapterUrl
                        }
                        else -> throw IllegalArgumentException("Either chapterId OR (chapterUrl + source) must be provided")
                    }

                val pages = IReaderNovel.getChapterContent(resolvedSourceId, resolvedChapterUrl)

                FetchIReaderChapterContentPayload(
                    clientMutationId = clientMutationId,
                    pages = pages.map { IReaderPageType.fromPage(it) },
                )
            }
        }
    }
}
