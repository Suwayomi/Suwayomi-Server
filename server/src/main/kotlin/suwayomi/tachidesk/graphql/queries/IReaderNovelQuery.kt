/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.execution.DataFetcherResult
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderChapterType
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.graphql.types.IReaderNovelsPageType
import suwayomi.tachidesk.graphql.types.IReaderPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderNovelQuery {
    @RequireAuth
    @GraphQLDescription("Get popular novels from an IReader source")
    fun ireaderPopularNovels(
        @GraphQLDescription("Source ID")
        sourceId: Long,
        @GraphQLDescription("Page number (1-indexed)")
        page: Int = 1,
    ): CompletableFuture<DataFetcherResult<IReaderNovelsPageType?>> =
        future {
            asDataFetcherResult {
                require(page > 0) { "Page must be greater than 0" }
                IReaderNovelsPageType(IReaderNovel.getPopularNovels(sourceId, page))
            }
        }

    @RequireAuth
    @GraphQLDescription("Get latest novels from an IReader source")
    fun ireaderLatestNovels(
        @GraphQLDescription("Source ID")
        sourceId: Long,
        @GraphQLDescription("Page number (1-indexed)")
        page: Int = 1,
    ): CompletableFuture<DataFetcherResult<IReaderNovelsPageType?>> =
        future {
            asDataFetcherResult {
                require(page > 0) { "Page must be greater than 0" }
                IReaderNovelsPageType(IReaderNovel.getLatestNovels(sourceId, page))
            }
        }

    @RequireAuth
    @GraphQLDescription("Search for novels in an IReader source")
    fun ireaderSearchNovels(
        @GraphQLDescription("Source ID")
        sourceId: Long,
        @GraphQLDescription("Search query")
        query: String,
        @GraphQLDescription("Page number (1-indexed)")
        page: Int = 1,
    ): CompletableFuture<DataFetcherResult<IReaderNovelsPageType?>> =
        future {
            asDataFetcherResult {
                require(page > 0) { "Page must be greater than 0" }
                require(query.isNotBlank()) { "Query cannot be empty" }
                IReaderNovelsPageType(IReaderNovel.searchNovels(sourceId, query, page))
            }
        }

    @RequireAuth
    @GraphQLDescription("Get detailed information about a novel")
    fun ireaderNovelDetails(
        @GraphQLDescription("Source ID")
        sourceId: Long,
        @GraphQLDescription("Novel URL from the source")
        novelUrl: String,
    ): CompletableFuture<DataFetcherResult<IReaderNovelType?>> =
        future {
            asDataFetcherResult {
                require(novelUrl.isNotBlank()) { "Novel URL cannot be empty" }
                IReaderNovelType(IReaderNovel.getNovelDetails(sourceId, novelUrl))
            }
        }

    @RequireAuth
    @GraphQLDescription("Get the list of chapters for a novel")
    fun ireaderNovelChapters(
        @GraphQLDescription("Source ID")
        sourceId: Long,
        @GraphQLDescription("Novel URL from the source")
        novelUrl: String,
    ): CompletableFuture<DataFetcherResult<List<IReaderChapterType>?>> =
        future {
            asDataFetcherResult {
                require(novelUrl.isNotBlank()) { "Novel URL cannot be empty" }
                IReaderNovel.getChapterList(sourceId, novelUrl).map { IReaderChapterType(it) }
            }
        }

    @RequireAuth
    @GraphQLDescription("Get the content/pages of a chapter")
    fun ireaderChapterContent(
        @GraphQLDescription("Source ID")
        sourceId: Long,
        @GraphQLDescription("Chapter URL from the source")
        chapterUrl: String,
    ): CompletableFuture<DataFetcherResult<List<IReaderPageType>?>> =
        future {
            asDataFetcherResult {
                require(chapterUrl.isNotBlank()) { "Chapter URL cannot be empty" }
                IReaderNovel.getChapterContent(sourceId, chapterUrl).map { IReaderPageType.fromPage(it) }
            }
        }
}
