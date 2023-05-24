package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import com.expediagroup.graphql.server.extensions.getValuesFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * TODO Mutations
 * - Check for updates?
 * - Download
 * - Delete download
 */
class ChapterMutation {
    data class UpdateChapterPatch(
        val isBookmarked: Boolean? = null,
        val isRead: Boolean? = null,
        val lastPageRead: Int? = null
    )

    data class UpdateChapterPayload(
        val clientMutationId: String?,
        val chapter: ChapterType
    )
    data class UpdateChapterInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateChapterPatch
    )

    data class UpdateChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>
    )
    data class UpdateChaptersInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
        val patch: UpdateChapterPatch
    )

    private fun updateChapters(ids: List<Int>, patch: UpdateChapterPatch) {
        transaction {
            if (patch.isRead != null || patch.isBookmarked != null || patch.lastPageRead != null) {
                val now = Instant.now().epochSecond
                ChapterTable.update({ ChapterTable.id inList ids }) { update ->
                    patch.isRead?.also {
                        update[isRead] = it
                    }
                    patch.isBookmarked?.also {
                        update[isBookmarked] = it
                    }
                    patch.lastPageRead?.also {
                        update[lastPageRead] = it
                        update[lastReadAt] = now
                    }
                }
            }
        }
    }

    fun updateChapter(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UpdateChapterInput
    ): CompletableFuture<UpdateChapterPayload> {
        val (clientMutationId, id, patch) = input

        updateChapters(listOf(id), patch)

        return dataFetchingEnvironment.getValueFromDataLoader<Int, ChapterType>("ChapterDataLoader", id).thenApply { chapter ->
            UpdateChapterPayload(
                clientMutationId = clientMutationId,
                chapter = chapter
            )
        }
    }

    fun updateChapters(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UpdateChaptersInput
    ): CompletableFuture<UpdateChaptersPayload> {
        val (clientMutationId, ids, patch) = input

        updateChapters(ids, patch)

        return dataFetchingEnvironment.getValuesFromDataLoader<Int, ChapterType>("ChapterDataLoader", ids).thenApply { chapters ->
            UpdateChaptersPayload(
                clientMutationId = clientMutationId,
                chapters = chapters
            )
        }
    }

    data class FetchChaptersInput(
        val clientMutationId: String? = null,
        val mangaId: Int
    )
    data class FetchChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>
    )

    fun fetchChapters(
        input: FetchChaptersInput
    ): CompletableFuture<FetchChaptersPayload> {
        val (clientMutationId, mangaId) = input

        return future {
            Chapter.fetchChapterList(mangaId)
        }.thenApply {
            val chapters = ChapterTable.select { ChapterTable.manga eq mangaId }
                .orderBy(ChapterTable.sourceOrder)
                .map { ChapterType(it) }
            FetchChaptersPayload(
                clientMutationId = clientMutationId,
                chapters = chapters
            )
        }
    }
}
