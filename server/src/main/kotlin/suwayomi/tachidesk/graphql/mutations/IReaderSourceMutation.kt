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
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderSourceMutation {
    data class SetIReaderSourcePreferenceInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val sourceId: Long,
        @GraphQLDescription("Preference key")
        val key: String,
        @GraphQLDescription("Preference value")
        val value: String,
    )

    data class SetIReaderSourcePreferencePayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    @RequireAuth
    @GraphQLDescription("Set a preference for an IReader source")
    fun setIReaderSourcePreference(
        input: SetIReaderSourcePreferenceInput,
    ): CompletableFuture<DataFetcherResult<SetIReaderSourcePreferencePayload?>> {
        val (clientMutationId, sourceId, key, value) = input

        return future {
            asDataFetcherResult {
                // Placeholder for when preference support is fully implemented
                // IReader sources use PreferenceStore which is currently stubbed

                SetIReaderSourcePreferencePayload(
                    clientMutationId = clientMutationId,
                    success = true,
                )
            }
        }
    }
}
