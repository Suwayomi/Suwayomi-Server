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
import suwayomi.tachidesk.manga.impl.IReaderSource
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

@GraphQLDescription("IReader source preference")
data class IReaderSourcePreference(
    val key: String,
    val title: String,
    val summary: String?,
    val defaultValue: String?,
    val currentValue: String?,
    val valueType: String,
)

class IReaderSourcePreferencesQuery {
    @RequireAuth
    @GraphQLDescription("Get preferences for an IReader source")
    fun ireaderSourcePreferences(
        @GraphQLDescription("Source ID")
        sourceId: Long,
    ): CompletableFuture<DataFetcherResult<List<IReaderSourcePreference>?>> =
        future {
            asDataFetcherResult {
                val source =
                    IReaderSource.getSource(sourceId)
                        ?: throw IllegalArgumentException("Source not found: $sourceId")

                // IReader sources may have preferences through their preference store
                // This is a placeholder for when preference support is fully implemented
                emptyList<IReaderSourcePreference>()
            }
        }
}
