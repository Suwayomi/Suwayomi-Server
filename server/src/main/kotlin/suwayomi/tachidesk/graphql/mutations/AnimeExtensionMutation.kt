package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.impl.extension.AnimeExtensionsList
import suwayomi.tachidesk.anime.impl.extension.AnimeExtension
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.AnimeExtensionType
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class AnimeExtensionMutation {
    data class UpdateAnimeExtensionPatch(
        val install: Boolean? = null,
        val update: Boolean? = null,
        val uninstall: Boolean? = null,
    )

    data class UpdateAnimeExtensionPayload(
        val clientMutationId: String?,
        val extension: AnimeExtensionType?,
    )

    data class UpdateAnimeExtensionInput(
        val clientMutationId: String? = null,
        val id: String,
        val patch: UpdateAnimeExtensionPatch,
    )

    data class UpdateAnimeExtensionsPayload(
        val clientMutationId: String?,
        val extensions: List<AnimeExtensionType>,
    )

    data class UpdateAnimeExtensionsInput(
        val clientMutationId: String? = null,
        val ids: List<String>,
        val patch: UpdateAnimeExtensionPatch,
    )

    private suspend fun updateExtensions(
        ids: List<String>,
        patch: UpdateAnimeExtensionPatch,
    ) {
        val extensions =
            transaction {
                AnimeExtensionTable
                    .selectAll()
                    .where { AnimeExtensionTable.pkgName inList ids }
                    .map { AnimeExtensionType(it) }
            }

        if (patch.update == true) {
            extensions.filter { it.hasUpdate }.forEach { AnimeExtension.updateExtension(it.pkgName) }
        }

        if (patch.install == true) {
            extensions.filterNot { it.isInstalled }.forEach { AnimeExtension.installExtension(it.pkgName) }
        }

        if (patch.uninstall == true) {
            extensions.filter { it.isInstalled }.forEach { AnimeExtension.uninstallExtension(it.pkgName) }
        }
    }

    @RequireAuth
    fun updateAnimeExtension(
        input: UpdateAnimeExtensionInput,
    ): CompletableFuture<DataFetcherResult<UpdateAnimeExtensionPayload?>> {
        val (clientMutationId, id, patch) = input

        return future {
            asDataFetcherResult {
                updateExtensions(listOf(id), patch)

                val extension =
                    transaction {
                        AnimeExtensionTable
                            .selectAll()
                            .where { AnimeExtensionTable.pkgName eq id }
                            .firstOrNull()
                            ?.let { AnimeExtensionType(it) }
                    }

                UpdateAnimeExtensionPayload(
                    clientMutationId = clientMutationId,
                    extension = extension,
                )
            }
        }
    }

    @RequireAuth
    fun updateAnimeExtensions(
        input: UpdateAnimeExtensionsInput,
    ): CompletableFuture<DataFetcherResult<UpdateAnimeExtensionsPayload?>> {
        val (clientMutationId, ids, patch) = input

        return future {
            asDataFetcherResult {
                updateExtensions(ids, patch)

                val extensions =
                    transaction {
                        AnimeExtensionTable
                            .selectAll()
                            .where { AnimeExtensionTable.pkgName inList ids }
                            .map { AnimeExtensionType(it) }
                    }

                UpdateAnimeExtensionsPayload(
                    clientMutationId = clientMutationId,
                    extensions = extensions,
                )
            }
        }
    }
    data class FetchAnimeExtensionsInput(
        val clientMutationId: String? = null,
    )

    data class FetchAnimeExtensionsPayload(
        val clientMutationId: String?,
        val extensions: List<AnimeExtensionType>,
    )

    @RequireAuth
    fun fetchAnimeExtensions(
        input: FetchAnimeExtensionsInput,
    ): CompletableFuture<DataFetcherResult<FetchAnimeExtensionsPayload?>> {
        val (clientMutationId) = input

        return future {
            asDataFetcherResult {
                AnimeExtensionsList.fetchExtensions()

                val extensions =
                    transaction {
                        AnimeExtensionTable
                            .selectAll()
                            .map { AnimeExtensionType(it) }
                    }

                FetchAnimeExtensionsPayload(
                    clientMutationId = clientMutationId,
                    extensions = extensions,
                )
            }
        }
    }
}
