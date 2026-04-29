/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import io.javalin.http.UploadedFile
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.MangaUserOverrideType
import suwayomi.tachidesk.manga.impl.MangaUserOverride

class MangaUserOverrideMutation {
    data class MangaUserOverridePatch(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val notes: String? = null,
    )

    data class SetMangaUserOverrideInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val patch: MangaUserOverridePatch,
    )

    data class SetMangaUserOverridePayload(
        val clientMutationId: String?,
        val override: MangaUserOverrideType,
    )

    @RequireAuth
    fun setMangaUserOverride(input: SetMangaUserOverrideInput): DataFetcherResult<SetMangaUserOverridePayload?> =
        asDataFetcherResult {
            val (clientMutationId, mangaId, patch) = input
            val saved =
                MangaUserOverride.set(
                    mangaId,
                    MangaUserOverride.Patch(
                        title = patch.title,
                        author = patch.author,
                        artist = patch.artist,
                        description = patch.description,
                        genre = patch.genre,
                        notes = patch.notes,
                    ),
                )
            SetMangaUserOverridePayload(clientMutationId, MangaUserOverrideType(saved))
        }

    data class ClearMangaUserOverrideInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
    )

    data class ClearMangaUserOverridePayload(
        val clientMutationId: String?,
        val cleared: Boolean,
    )

    @RequireAuth
    fun clearMangaUserOverride(input: ClearMangaUserOverrideInput): DataFetcherResult<ClearMangaUserOverridePayload?> =
        asDataFetcherResult {
            val (clientMutationId, mangaId) = input
            ClearMangaUserOverridePayload(clientMutationId, MangaUserOverride.clear(mangaId))
        }

    data class SetMangaCustomCoverInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val cover: UploadedFile,
    )

    data class SetMangaCustomCoverPayload(
        val clientMutationId: String?,
        val override: MangaUserOverrideType,
    )

    @RequireAuth
    fun setMangaCustomCover(input: SetMangaCustomCoverInput): DataFetcherResult<SetMangaCustomCoverPayload?> =
        asDataFetcherResult {
            val (clientMutationId, mangaId, cover) = input
            val saved = MangaUserOverride.setCustomCover(mangaId, cover.content())
            SetMangaCustomCoverPayload(clientMutationId, MangaUserOverrideType(saved))
        }

    data class ClearMangaCustomCoverInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
    )

    data class ClearMangaCustomCoverPayload(
        val clientMutationId: String?,
        val cleared: Boolean,
    )

    @RequireAuth
    fun clearMangaCustomCover(input: ClearMangaCustomCoverInput): DataFetcherResult<ClearMangaCustomCoverPayload?> =
        asDataFetcherResult {
            val (clientMutationId, mangaId) = input
            ClearMangaCustomCoverPayload(clientMutationId, MangaUserOverride.clearCustomCover(mangaId))
        }
}
