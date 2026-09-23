@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.update.UpdaterRegistry
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class UpdateMutation {
    private val updaters: UpdaterRegistry by injectLazy()

    data class UpdateLibraryInput(
        val clientMutationId: String? = null,
        val categories: List<Int>?,
        val contentType: SourceContentType? = SourceContentType.MANGA,
    )

    data class UpdateLibraryPayload(
        val clientMutationId: String? = null,
        val updateStatus: LibraryUpdateStatus,
    )

    @RequireAuth
    fun updateLibrary(input: UpdateLibraryInput): CompletableFuture<UpdateLibraryPayload?> {
        val updater = updaters.forContentType(input.contentType)
        updater.addCategoriesToUpdateQueue(
            Category.getCategoryList(input.contentType ?: SourceContentType.MANGA).filter { input.categories?.contains(it.id) ?: true },
            clear = true,
            forceAll = !input.categories.isNullOrEmpty(),
            contentType = input.contentType,
        )

        return future {
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

    data class UpdateLibraryMangaInput(
        val clientMutationId: String? = null,
    )

    data class UpdateLibraryMangaPayload(
        val clientMutationId: String?,
        val updateStatus: UpdateStatus,
    )

    @RequireAuth
    fun updateLibraryManga(input: UpdateLibraryMangaInput): CompletableFuture<UpdateLibraryMangaPayload?> {
        updateLibrary(
            UpdateLibraryInput(
                clientMutationId = input.clientMutationId,
                categories = null,
            ),
        )

        return future {
            UpdateLibraryMangaPayload(
                input.clientMutationId,
                updateStatus =
                    withTimeout(30.seconds) {
                        UpdateStatus(updaters.manga.status.first())
                    },
            )
        }
    }

    data class UpdateCategoryMangaInput(
        val clientMutationId: String? = null,
        val categories: List<Int>,
        val contentType: SourceContentType? = SourceContentType.MANGA,
    )

    data class UpdateCategoryMangaPayload(
        val clientMutationId: String?,
        val updateStatus: UpdateStatus,
    )

    @RequireAuth
    fun updateCategoryManga(input: UpdateCategoryMangaInput): CompletableFuture<UpdateCategoryMangaPayload?> {
        updateLibrary(
            UpdateLibraryInput(
                clientMutationId = input.clientMutationId,
                categories = input.categories,
                contentType = input.contentType,
            ),
        )

        return future {
            UpdateCategoryMangaPayload(
                input.clientMutationId,
                updateStatus =
                    withTimeout(30.seconds) {
                        UpdateStatus(updaters.forContentType(input.contentType).status.first())
                    },
            )
        }
    }

    data class UpdateStopInput(
        val clientMutationId: String? = null,
        val contentType: SourceContentType? = SourceContentType.MANGA,
    )

    data class UpdateStopPayload(
        val clientMutationId: String?,
    )

    @RequireAuth
    fun updateStop(input: UpdateStopInput): UpdateStopPayload {
        updaters.forContentType(input.contentType).reset()
        return UpdateStopPayload(input.clientMutationId)
    }
}
