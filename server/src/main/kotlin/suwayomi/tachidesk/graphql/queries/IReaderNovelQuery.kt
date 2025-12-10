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
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.graphql.types.IReaderNovelsPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderNovelQuery {
    @RequireAuth
    @GraphQLDescription("Get detailed information about a novel")
    fun ireaderNovel(
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
    @GraphQLDescription("Get popular novels from a source")
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
    @GraphQLDescription("Get latest novels from a source")
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
    @GraphQLDescription("Search novels from a source")
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
                require(query.isNotBlank()) { "Search query cannot be empty" }
                require(page > 0) { "Page must be greater than 0" }
                IReaderNovelsPageType(IReaderNovel.searchNovels(sourceId, query, page))
            }
        }
}
