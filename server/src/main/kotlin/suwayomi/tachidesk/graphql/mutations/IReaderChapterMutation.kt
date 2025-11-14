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
import suwayomi.tachidesk.graphql.types.IReaderPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderChapterMutation {
    data class FetchIReaderChaptersInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val sourceId: Long,
        @GraphQLDescription("Novel URL from the source")
        val novelUrl: String,
    )

    data class FetchIReaderChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<IReaderChapterType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch the list of chapters for a novel")
    fun fetchIReaderChapters(
        input: FetchIReaderChaptersInput,
    ): CompletableFuture<DataFetcherResult<FetchIReaderChaptersPayload?>> {
        val (clientMutationId, sourceId, novelUrl) = input

        return future {
            asDataFetcherResult {
                require(novelUrl.isNotBlank()) { "Novel URL cannot be empty" }

                val chapters = IReaderNovel.getChapterList(sourceId, novelUrl).map { IReaderChapterType(it) }

                FetchIReaderChaptersPayload(
                    clientMutationId = clientMutationId,
                    chapters = chapters,
                )
            }
        }
    }

    data class FetchIReaderChapterContentInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val sourceId: Long,
        @GraphQLDescription("Chapter URL from the source")
        val chapterUrl: String,
    )

    data class FetchIReaderChapterContentPayload(
        val clientMutationId: String?,
        val pages: List<IReaderPageType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch the content/pages of a chapter")
    fun fetchIReaderChapterContent(
        input: FetchIReaderChapterContentInput,
    ): CompletableFuture<DataFetcherResult<FetchIReaderChapterContentPayload?>> {
        val (clientMutationId, sourceId, chapterUrl) = input

        return future {
            asDataFetcherResult {
                require(chapterUrl.isNotBlank()) { "Chapter URL cannot be empty" }

                val pages = IReaderNovel.getChapterContent(sourceId, chapterUrl).map { IReaderPageType.fromPage(it) }

                FetchIReaderChapterContentPayload(
                    clientMutationId = clientMutationId,
                    pages = pages,
                )
            }
        }
    }
}
