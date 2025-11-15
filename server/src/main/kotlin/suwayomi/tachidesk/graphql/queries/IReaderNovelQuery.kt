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
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderNovelQuery {
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
}
