/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.MangaDuplicates
import suwayomi.tachidesk.manga.impl.MangaUrlResolver
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class MangaUrlMutation {
    enum class AddMangaFromUrlStatus {
        FOUND,
        EXTENSION_INSTALLED,
        NO_SOURCE_FOR_URL,
        INVALID_URL,
    }

    data class AddMangaFromUrlInput(
        val clientMutationId: String? = null,
        val url: String,
        val autoInstallExtension: Boolean? = null,
        val addToLibrary: Boolean? = null,
        val categoryIds: List<Int>? = null,
    )

    data class AddMangaFromUrlPayload(
        val clientMutationId: String?,
        val status: AddMangaFromUrlStatus,
        val manga: MangaType?,
        val installedExtensionPkgName: String?,
        val message: String?,
        val duplicates: List<MangaType>,
    )

    @RequireAuth
    fun addMangaFromUrl(input: AddMangaFromUrlInput): CompletableFuture<DataFetcherResult<AddMangaFromUrlPayload?>> {
        val (clientMutationId, url, autoInstallExtension, addToLibrary, categoryIds) = input

        return future {
            asDataFetcherResult {
                val effectiveAutoInstall = autoInstallExtension ?: true
                val effectiveAddToLibrary = addToLibrary ?: true
                val effectiveCategoryIds = categoryIds.orEmpty()

                val result =
                    MangaUrlResolver.resolveUrl(
                        url = url,
                        autoInstallExtension = effectiveAutoInstall,
                        addToLibrary = effectiveAddToLibrary,
                    )

                val mangaId = result.manga?.id
                if (mangaId != null && effectiveAddToLibrary && effectiveCategoryIds.isNotEmpty()) {
                    CategoryManga.addMangasToCategories(listOf(mangaId), effectiveCategoryIds)
                }

                val mangaType =
                    mangaId?.let { id ->
                        transaction {
                            MangaTable
                                .selectAll()
                                .where { MangaTable.id eq id }
                                .firstOrNull()
                                ?.let { MangaType(it) }
                        }
                    }

                val duplicates =
                    mangaId?.let { id ->
                        MangaDuplicates.findDuplicates(id).map { MangaType(it) }
                    }.orEmpty()

                AddMangaFromUrlPayload(
                    clientMutationId = clientMutationId,
                    status = AddMangaFromUrlStatus.valueOf(result.status.name),
                    manga = mangaType,
                    installedExtensionPkgName = result.installedExtensionPkgName,
                    message = result.message,
                    duplicates = duplicates,
                )
            }
        }
    }
}
