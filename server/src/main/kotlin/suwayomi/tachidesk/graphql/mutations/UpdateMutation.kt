@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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
    fun updateLibrary(
        @GraphQLIgnore
        userId: Int,
        input: UpdateLibraryInput,
    ): CompletableFuture<UpdateLibraryPayload?> {
        updater.addCategoriesToUpdateQueue(
            userId,
            Category.getCategoryList(userId).filter { input.categories?.contains(it.id) ?: true },
            clear = true,
            forceAll = !input.categories.isNullOrEmpty(),
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
    fun updateLibraryManga(
        @GraphQLIgnore
        userId: Int,
        input: UpdateLibraryMangaInput,
    ): CompletableFuture<UpdateLibraryMangaPayload?> {
        // same enqueue as updateLibrary(categories = null), inlined — calling updateLibrary
        // here would build a CompletableFuture that's discarded unused
        updater.addCategoriesToUpdateQueue(
            userId,
            Category.getCategoryList(userId),
            clear = true,
            forceAll = false,
        )

        return future {
            UpdateLibraryMangaPayload(
                input.clientMutationId,
                updateStatus =
                    withTimeout(30.seconds) {
                        UpdateStatus(updater.status.first())
                    },
            )
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
    fun updateCategoryManga(
        @GraphQLIgnore
        userId: Int,
        input: UpdateCategoryMangaInput,
    ): CompletableFuture<UpdateCategoryMangaPayload?> {
        // same enqueue as updateLibrary(categories = input.categories), inlined — calling
        // updateLibrary here would build a CompletableFuture that's discarded unused
        updater.addCategoriesToUpdateQueue(
            userId,
            Category.getCategoryList(userId).filter { input.categories.contains(it.id) },
            clear = true,
            forceAll = true,
        )

        return future {
            UpdateCategoryMangaPayload(
                input.clientMutationId,
                updateStatus =
                    withTimeout(30.seconds) {
                        UpdateStatus(updater.status.first())
                    },
            )
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
        // the update queue is shared process-wide state: reset() cancels every user's
        // in-flight updates, not just the caller's — same shared semantics as the REST
        // update/stop endpoint
        updater.reset()
        return UpdateStopPayload(input.clientMutationId)
    }
}
