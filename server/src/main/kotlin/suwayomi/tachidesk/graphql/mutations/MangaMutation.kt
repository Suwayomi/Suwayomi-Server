package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.MangaMetaType
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * TODO Mutations
 * - Download x(all = -1) chapters
 * - Delete read/all downloaded chapters
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

    private fun updateMangas(userId: Int, ids: List<Int>, patch: UpdateMangaPatch) {
        transaction {
            val currentMangaUserItems = MangaUserTable.select { MangaUserTable.manga inList ids }
                .map { it[MangaUserTable.manga].value }
            if (currentMangaUserItems.size < ids.size) {
                MangaUserTable.batchInsert(ids - currentMangaUserItems.toSet()) {
                    this[MangaUserTable.user] = userId
                    this[MangaUserTable.manga] = it
                }
            }

            if (patch.inLibrary != null) {
                val now = Instant.now().epochSecond
                MangaUserTable.update({ MangaUserTable.manga inList ids }) { update ->
                    patch.inLibrary.also {
                        update[inLibrary] = it
                        if (patch.inLibrary) {
                            update[inLibraryAt] = now
                        }
                    }
                }
            }
        }
    }

    fun updateManga(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UpdateMangaInput
    ): UpdateMangaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, id, patch) = input

        updateMangas(userId, listOf(id), patch)

        val manga = transaction {
            MangaType(MangaTable.getWithUserData(userId).select { MangaTable.id eq id }.first())
        }

        return UpdateMangaPayload(
            clientMutationId = clientMutationId,
            manga = manga
        )
    }

    fun updateMangas(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UpdateMangasInput
    ): UpdateMangasPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, ids, patch) = input

        updateMangas(userId, ids, patch)

        val mangas = transaction {
            MangaTable.getWithUserData(userId).select { MangaTable.id inList ids }.map { MangaType(it) }
        }

        return UpdateMangasPayload(
            clientMutationId = clientMutationId,
            mangas = mangas
        )
    }

    data class FetchMangaInput(
        val clientMutationId: String? = null,
        val id: Int
    )
    data class FetchMangaPayload(
        val clientMutationId: String?,
        val manga: MangaType
    )

    fun fetchManga(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: FetchMangaInput
    ): CompletableFuture<FetchMangaPayload> {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, id) = input

        return future {
            Manga.fetchManga(id)
        }.thenApply {
            val manga = transaction {
                MangaTable.getWithUserData(userId).select { MangaTable.id eq id }.first()
            }
            FetchMangaPayload(
                clientMutationId = clientMutationId,
                manga = MangaType(manga)
            )
        }
    }

    data class SetMangaMetaInput(
        val clientMutationId: String? = null,
        val meta: MangaMetaType
    )
    data class SetMangaMetaPayload(
        val clientMutationId: String?,
        val meta: MangaMetaType
    )
    fun setMangaMeta(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: SetMangaMetaInput
    ): SetMangaMetaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, meta) = input

        Manga.modifyMangaMeta(userId, meta.mangaId, meta.key, meta.value)

        return SetMangaMetaPayload(clientMutationId, meta)
    }

    data class DeleteMangaMetaInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val key: String
    )
    data class DeleteMangaMetaPayload(
        val clientMutationId: String?,
        val meta: MangaMetaType?,
        val manga: MangaType
    )
    fun deleteMangaMeta(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DeleteMangaMetaInput
    ): DeleteMangaMetaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, mangaId, key) = input

        val (meta, manga) = transaction {
            val meta = MangaMetaTable.select {
                MangaMetaTable.user eq userId and
                    (MangaMetaTable.ref eq mangaId) and
                    (MangaMetaTable.key eq key)
            }.firstOrNull()

            MangaMetaTable.deleteWhere {
                MangaMetaTable.user eq userId and
                    (MangaMetaTable.ref eq mangaId) and
                    (MangaMetaTable.key eq key)
            }

            val manga = transaction {
                MangaType(MangaTable.getWithUserData(userId).select { MangaTable.id eq mangaId }.first())
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
