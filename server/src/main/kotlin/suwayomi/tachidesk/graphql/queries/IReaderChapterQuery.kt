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
import suwayomi.tachidesk.graphql.types.IReaderPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderChapterQuery {
    @RequireAuth
    @GraphQLDescription("Get the list of chapters for a novel")
    fun ireaderChapters(
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
