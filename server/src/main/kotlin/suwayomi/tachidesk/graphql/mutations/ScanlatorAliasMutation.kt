/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ScanlatorAliasType
import suwayomi.tachidesk.manga.impl.ScanlatorAlias

class ScanlatorAliasMutation {
    data class CreateScanlatorAliasInput(
        val clientMutationId: String? = null,
        val scanlator: String,
        val displayName: String,
    )

    data class CreateScanlatorAliasPayload(
        val clientMutationId: String?,
        val scanlatorAlias: ScanlatorAliasType,
    )

    @RequireAuth
    fun createScanlatorAlias(input: CreateScanlatorAliasInput): DataFetcherResult<CreateScanlatorAliasPayload?> =
        asDataFetcherResult {
            val (clientMutationId, scanlator, displayName) = input
            val created = ScanlatorAlias.create(scanlator, displayName)
            CreateScanlatorAliasPayload(clientMutationId, ScanlatorAliasType(created))
        }

    data class UpdateScanlatorAliasPatch(
        val displayName: String,
    )

    data class UpdateScanlatorAliasInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateScanlatorAliasPatch,
    )

    data class UpdateScanlatorAliasPayload(
        val clientMutationId: String?,
        val scanlatorAlias: ScanlatorAliasType,
    )

    @RequireAuth
    fun updateScanlatorAlias(input: UpdateScanlatorAliasInput): DataFetcherResult<UpdateScanlatorAliasPayload?> =
        asDataFetcherResult {
            val (clientMutationId, id, patch) = input
            val updated = ScanlatorAlias.update(id, patch.displayName)
            UpdateScanlatorAliasPayload(clientMutationId, ScanlatorAliasType(updated))
        }

    data class DeleteScanlatorAliasInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DeleteScanlatorAliasPayload(
        val clientMutationId: String?,
        val deleted: Boolean,
    )

    @RequireAuth
    fun deleteScanlatorAlias(input: DeleteScanlatorAliasInput): DataFetcherResult<DeleteScanlatorAliasPayload?> =
        asDataFetcherResult {
            val (clientMutationId, id) = input
            val deleted = ScanlatorAlias.delete(id)
            DeleteScanlatorAliasPayload(clientMutationId, deleted)
        }
}
