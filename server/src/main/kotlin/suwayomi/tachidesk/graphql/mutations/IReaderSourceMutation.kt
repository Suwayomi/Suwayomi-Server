/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.execution.DataFetcherResult
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderChapterType
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.graphql.types.IReaderPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel
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
        @GraphQLDescription("Source ID")
        val source: Long,
        @GraphQLDescription("Novel URL/key from the source")
        val novelUrl: String,
    )

    data class FetchIReaderChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<IReaderChapterType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch chapters for a novel from an IReader source")
    fun fetchIReaderChapters(input: FetchIReaderChaptersInput): CompletableFuture<DataFetcherResult<FetchIReaderChaptersPayload?>> {
        val (clientMutationId, sourceId, novelUrl) = input

        return future {
            asDataFetcherResult {
                require(novelUrl.isNotBlank()) { "Novel URL cannot be empty" }

                val chapters = IReaderNovel.getChapterList(sourceId, novelUrl)

                FetchIReaderChaptersPayload(
                    clientMutationId = clientMutationId,
                    chapters = chapters.map { IReaderChapterType(it) },
                )
            }
        }
    }

    data class FetchIReaderChapterContentInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val source: Long,
        @GraphQLDescription("Chapter URL/key from the source")
        val chapterUrl: String,
    )

    data class FetchIReaderChapterContentPayload(
        val clientMutationId: String?,
        val pages: List<IReaderPageType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch content/pages for a chapter from an IReader source")
    fun fetchIReaderChapterContent(input: FetchIReaderChapterContentInput): CompletableFuture<DataFetcherResult<FetchIReaderChapterContentPayload?>> {
        val (clientMutationId, sourceId, chapterUrl) = input

        return future {
            asDataFetcherResult {
                require(chapterUrl.isNotBlank()) { "Chapter URL cannot be empty" }

                val pages = IReaderNovel.getChapterContent(sourceId, chapterUrl)

                FetchIReaderChapterContentPayload(
                    clientMutationId = clientMutationId,
                    pages = pages.map { IReaderPageType.fromPage(it) },
                )
            }
        }
    }
}
