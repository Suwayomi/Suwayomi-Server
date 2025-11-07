package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class UpdateMutation {
    private val updater: IUpdater by injectLazy()

    data class UpdateLibraryInput(
        val clientMutationId: String? = null,
        val categories: List<Int>?,
    )

    data class UpdateLibraryPayload(
        val clientMutationId: String? = null,
        val updateStatus: LibraryUpdateStatus,
    )

    @RequireAuth
    fun updateLibrary(input: UpdateLibraryInput): CompletableFuture<DataFetcherResult<UpdateLibraryPayload?>> {
        updater.addCategoriesToUpdateQueue(
            Category.getCategoryList().filter { input.categories?.contains(it.id) ?: true },
            clear = true,
            forceAll = !input.categories.isNullOrEmpty(),
        )

        return future {
            asDataFetcherResult {
                UpdateLibraryPayload(
                    input.clientMutationId,
                    updateStatus =
                        withTimeout(30.seconds) {
                            LibraryUpdateStatus(
                                updater.updates.first(),
                            )
                        },
                )
            }
        }
    }

    data class UpdateLibraryMangaInput(
        val clientMutationId: String? = null,
    )

    data class UpdateLibraryMangaPayload(
        val clientMutationId: String?,
        val updateStatus: UpdateStatus,
    )

    @RequireAuth
    fun updateLibraryManga(input: UpdateLibraryMangaInput): CompletableFuture<DataFetcherResult<UpdateLibraryMangaPayload?>> {
        updateLibrary(
            UpdateLibraryInput(
                clientMutationId = input.clientMutationId,
                categories = null,
            ),
        )

        return future {
            asDataFetcherResult {
                UpdateLibraryMangaPayload(
                    input.clientMutationId,
                    updateStatus =
                        withTimeout(30.seconds) {
                            UpdateStatus(updater.status.first())
                        },
                )
            }
        }
    }

    data class UpdateCategoryMangaInput(
        val clientMutationId: String? = null,
        val categories: List<Int>,
    )

    data class UpdateCategoryMangaPayload(
        val clientMutationId: String?,
        val updateStatus: UpdateStatus,
    )

    @RequireAuth
    fun updateCategoryManga(input: UpdateCategoryMangaInput): CompletableFuture<DataFetcherResult<UpdateCategoryMangaPayload?>> {
        updateLibrary(
            UpdateLibraryInput(
                clientMutationId = input.clientMutationId,
                categories = input.categories,
            ),
        )

        return future {
            asDataFetcherResult {
                UpdateCategoryMangaPayload(
                    input.clientMutationId,
                    updateStatus =
                        withTimeout(30.seconds) {
                            UpdateStatus(updater.status.first())
                        },
                )
            }
        }
    }

    data class UpdateStopInput(
        val clientMutationId: String? = null,
    )

    data class UpdateStopPayload(
        val clientMutationId: String?,
    )

    @RequireAuth
    fun updateStop(input: UpdateStopInput): UpdateStopPayload {
        updater.reset()
        return UpdateStopPayload(input.clientMutationId)
    }
}
