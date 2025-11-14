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
import suwayomi.tachidesk.graphql.types.IReaderNovelsPageType
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
        val sourceId: Long,
        @GraphQLDescription("Type of fetch operation")
        val type: FetchIReaderNovelType,
        @GraphQLDescription("Page number (1-indexed)")
        val page: Int = 1,
        @GraphQLDescription("Search query (required for SEARCH type)")
        val query: String? = null,
    )

    data class FetchIReaderNovelsPayload(
        val clientMutationId: String?,
        val novels: IReaderNovelsPageType,
    )

    @RequireAuth
    @GraphQLDescription("Fetch novels from an IReader source")
    fun fetchIReaderNovels(
        input: FetchIReaderNovelsInput,
    ): CompletableFuture<DataFetcherResult<FetchIReaderNovelsPayload?>> {
        val (clientMutationId, sourceId, type, page, query) = input

        return future {
            asDataFetcherResult {
                require(page > 0) { "Page must be greater than 0" }

                val novelsPage =
                    when (type) {
                        FetchIReaderNovelType.SEARCH -> {
                            require(query != null && query.isNotBlank()) { "Query cannot be empty for SEARCH type" }
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
                    novels = IReaderNovelsPageType(novelsPage),
                )
            }
        }
    }
}
