package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import com.expediagroup.graphql.server.extensions.getValuesFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Mutations
 * - Add to category
 * - Remove from category
 * - Check for updates
 * - Download x(all = -1) chapters
 * - Delete read/all downloaded chapters
 * - Add/update meta
 * - Delete meta
 */
class MangaMutation {
    data class UpdateMangaPatch(
        val inLibrary: Boolean? = null
    )

    data class UpdateMangaPayload(
        val clientMutationId: String?,
        val manga: MangaType
    )
    data class UpdateMangaInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateMangaPatch
    )

    data class UpdateMangasPayload(
        val clientMutationId: String?,
        val mangas: List<MangaType>
    )
    data class UpdateMangasInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
        val patch: UpdateMangaPatch
    )

    private fun updateMangas(ids: List<Int>, patch: UpdateMangaPatch) {
        transaction {
            if (patch.inLibrary != null) {
                MangaTable.update({ MangaTable.id inList ids }) { update ->
                    patch.inLibrary.also {
                        update[inLibrary] = it
                    }
                }
            }
        }
    }

    fun updateManga(dataFetchingEnvironment: DataFetchingEnvironment, input: UpdateMangaInput): CompletableFuture<UpdateMangaPayload> {
        val (clientMutationId, id, patch) = input

        updateMangas(listOf(id), patch)

        return dataFetchingEnvironment.getValueFromDataLoader<Int, MangaType>("MangaDataLoader", id).thenApply { manga ->
            UpdateMangaPayload(
                clientMutationId = clientMutationId,
                manga = manga
            )
        }
    }

    fun updateMangas(dataFetchingEnvironment: DataFetchingEnvironment, input: UpdateMangasInput): CompletableFuture<UpdateMangasPayload> {
        val (clientMutationId, ids, patch) = input

        updateMangas(ids, patch)

        return dataFetchingEnvironment.getValuesFromDataLoader<Int, MangaType>("MangaDataLoader", ids).thenApply { mangas ->
            UpdateMangasPayload(
                clientMutationId = clientMutationId,
                mangas = mangas
            )
        }
    }
}
