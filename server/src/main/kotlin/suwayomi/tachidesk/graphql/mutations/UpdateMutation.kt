package suwayomi.tachidesk.graphql.mutations

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class UpdateMutation {
    private val updater by DI.global.instance<IUpdater>()

    data class UpdateLibraryMangaInput(
        val clientMutationId: String? = null,
    )

    data class UpdateLibraryMangaPayload(
        val clientMutationId: String?,
        val updateStatus: UpdateStatus,
    )

    fun updateLibraryManga(input: UpdateLibraryMangaInput): CompletableFuture<UpdateLibraryMangaPayload> {
        updater.addCategoriesToUpdateQueue(
            Category.getCategoryList(),
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

    fun updateCategoryManga(input: UpdateCategoryMangaInput): CompletableFuture<UpdateCategoryMangaPayload> {
        val categories =
            transaction {
                CategoryTable.select { CategoryTable.id inList input.categories }.map {
                    CategoryTable.toDataClass(it)
                }
            }
        updater.addCategoriesToUpdateQueue(categories, clear = true, forceAll = true)

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

    fun updateStop(input: UpdateStopInput): UpdateStopPayload {
        updater.reset()
        return UpdateStopPayload(input.clientMutationId)
    }
}
