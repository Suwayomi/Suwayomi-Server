package suwayomi.tachidesk.graphql.mutations

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.MangaMetaType
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.Library
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

/**
 * TODO Mutations
 * - Download x(all = -1) chapters
 * - Delete read/all downloaded chapters
 */
class MangaMutation {
    data class UpdateMangaPatch(
        val inLibrary: Boolean? = null,
    )

    data class UpdateMangaPayload(
        val clientMutationId: String?,
        val manga: MangaType,
    )

    data class UpdateMangaInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateMangaPatch,
    )

    data class UpdateMangasPayload(
        val clientMutationId: String?,
        val mangas: List<MangaType>,
    )

    data class UpdateMangasInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
        val patch: UpdateMangaPatch,
    )

    private suspend fun updateMangas(
        ids: List<Int>,
        patch: UpdateMangaPatch,
    ) {
        transaction {
            if (patch.inLibrary != null) {
                MangaTable.update({ MangaTable.id inList ids }) { update ->
                    patch.inLibrary.also {
                        update[inLibrary] = it
                    }
                }
            }
        }.apply {
            if (patch.inLibrary != null) {
                ids.forEach {
                    Library.handleMangaThumbnail(it, patch.inLibrary)
                }
            }
        }
    }

    fun updateManga(input: UpdateMangaInput): CompletableFuture<UpdateMangaPayload> {
        val (clientMutationId, id, patch) = input

        return future {
            updateMangas(listOf(id), patch)
        }.thenApply {
            val manga =
                transaction {
                    MangaType(MangaTable.select { MangaTable.id eq id }.first())
                }

            UpdateMangaPayload(
                clientMutationId = clientMutationId,
                manga = manga,
            )
        }
    }

    fun updateMangas(input: UpdateMangasInput): CompletableFuture<UpdateMangasPayload> {
        val (clientMutationId, ids, patch) = input

        return future {
            updateMangas(ids, patch)
        }.thenApply {
            val mangas =
                transaction {
                    MangaTable.select { MangaTable.id inList ids }.map { MangaType(it) }
                }

            UpdateMangasPayload(
                clientMutationId = clientMutationId,
                mangas = mangas,
            )
        }
    }

    data class FetchMangaInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class FetchMangaPayload(
        val clientMutationId: String?,
        val manga: MangaType,
    )

    fun fetchManga(input: FetchMangaInput): CompletableFuture<FetchMangaPayload> {
        val (clientMutationId, id) = input

        return future {
            Manga.fetchManga(id)
        }.thenApply {
            val manga =
                transaction {
                    MangaTable.select { MangaTable.id eq id }.first()
                }
            FetchMangaPayload(
                clientMutationId = clientMutationId,
                manga = MangaType(manga),
            )
        }
    }

    data class SetMangaMetaInput(
        val clientMutationId: String? = null,
        val meta: MangaMetaType,
    )

    data class SetMangaMetaPayload(
        val clientMutationId: String?,
        val meta: MangaMetaType,
    )

    fun setMangaMeta(input: SetMangaMetaInput): SetMangaMetaPayload {
        val (clientMutationId, meta) = input

        Manga.modifyMangaMeta(meta.mangaId, meta.key, meta.value)

        return SetMangaMetaPayload(clientMutationId, meta)
    }

    data class DeleteMangaMetaInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val key: String,
    )

    data class DeleteMangaMetaPayload(
        val clientMutationId: String?,
        val meta: MangaMetaType?,
        val manga: MangaType,
    )

    fun deleteMangaMeta(input: DeleteMangaMetaInput): DeleteMangaMetaPayload {
        val (clientMutationId, mangaId, key) = input

        val (meta, manga) =
            transaction {
                val meta =
                    MangaMetaTable.select { (MangaMetaTable.ref eq mangaId) and (MangaMetaTable.key eq key) }
                        .firstOrNull()

                MangaMetaTable.deleteWhere { (MangaMetaTable.ref eq mangaId) and (MangaMetaTable.key eq key) }

                val manga =
                    transaction {
                        MangaType(MangaTable.select { MangaTable.id eq mangaId }.first())
                    }

                if (meta != null) {
                    MangaMetaType(meta)
                } else {
                    null
                } to manga
            }

        return DeleteMangaMetaPayload(clientMutationId, meta, manga)
    }
}
