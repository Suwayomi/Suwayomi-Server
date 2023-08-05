package suwayomi.tachidesk.graphql.mutations

import eu.kanade.tachiyomi.source.model.SChapter
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.ChapterMetaType
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * TODO Mutations
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
        input: UpdateChapterInput
    ): UpdateChapterPayload {
        val (clientMutationId, id, patch) = input

        updateChapters(listOf(id), patch)

        val chapter = transaction {
            ChapterType(ChapterTable.select { ChapterTable.id eq id }.first())
        }

        return UpdateChapterPayload(
            clientMutationId = clientMutationId,
            chapter = chapter
        )
    }

    fun updateChapters(
        input: UpdateChaptersInput
    ): UpdateChaptersPayload {
        val (clientMutationId, ids, patch) = input

        updateChapters(ids, patch)

        val chapters = transaction {
            ChapterTable.select { ChapterTable.id inList ids }.map { ChapterType(it) }
        }

        return UpdateChaptersPayload(
            clientMutationId = clientMutationId,
            chapters = chapters
        )
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
            val numberOfCurrentChapters = Chapter.getCountOfMangaChapters(mangaId)
            Chapter.fetchChapterList(mangaId)
            numberOfCurrentChapters
        }.thenApply { numberOfCurrentChapters ->
            val chapters = transaction {
                ChapterTable.select { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.sourceOrder)
            }

            // download new chapters if settings flag is enabled
            Chapter.downloadNewChapters(mangaId, numberOfCurrentChapters, chapters.toList())

            FetchChaptersPayload(
                clientMutationId = clientMutationId,
                chapters = chapters.map { ChapterType(it) }
            )
        }
    }

    data class SetChapterMetaInput(
        val clientMutationId: String? = null,
        val meta: ChapterMetaType
    )
    data class SetChapterMetaPayload(
        val clientMutationId: String?,
        val meta: ChapterMetaType
    )
    fun setChapterMeta(
        input: SetChapterMetaInput
    ): SetChapterMetaPayload {
        val (clientMutationId, meta) = input

        Chapter.modifyChapterMeta(meta.chapterId, meta.key, meta.value)

        return SetChapterMetaPayload(clientMutationId, meta)
    }

    data class DeleteChapterMetaInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val key: String
    )
    data class DeleteChapterMetaPayload(
        val clientMutationId: String?,
        val meta: ChapterMetaType?,
        val chapter: ChapterType
    )
    fun deleteChapterMeta(
        input: DeleteChapterMetaInput
    ): DeleteChapterMetaPayload {
        val (clientMutationId, chapterId, key) = input

        val (meta, chapter) = transaction {
            val meta = ChapterMetaTable.select { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }
                .firstOrNull()

            ChapterMetaTable.deleteWhere { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }

            val chapter = transaction {
                ChapterType(ChapterTable.select { ChapterTable.id eq chapterId }.first())
            }

            if (meta != null) {
                ChapterMetaType(meta)
            } else {
                null
            } to chapter
        }

        return DeleteChapterMetaPayload(clientMutationId, meta, chapter)
    }

    data class FetchChapterPagesInput(
        val clientMutationId: String? = null,
        val chapterId: Int
    )
    data class FetchChapterPagesPayload(
        val clientMutationId: String?,
        val pages: List<String>,
        val chapter: ChapterType
    )
    fun fetchChapterPages(
        input: FetchChapterPagesInput
    ): CompletableFuture<FetchChapterPagesPayload> {
        val (clientMutationId, chapterId) = input

        val chapter = transaction { ChapterTable.select { ChapterTable.id eq chapterId }.first() }
        val manga = transaction { MangaTable.select { MangaTable.id eq chapter[ChapterTable.manga] }.first() }
        val source = getCatalogueSourceOrNull(manga[MangaTable.sourceReference])!!

        return future {
            source.getPageList(
                SChapter.create().apply {
                    url = chapter[ChapterTable.url]
                    name = chapter[ChapterTable.name]
                }
            )
        }.thenApply { pageList ->
            transaction {
                PageTable.deleteWhere { PageTable.chapter eq chapterId }
                PageTable.batchInsert(pageList) { page ->
                    this[PageTable.index] = page.index
                    this[PageTable.url] = page.url
                    this[PageTable.imageUrl] = page.imageUrl
                    this[PageTable.chapter] = chapterId
                }
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[ChapterTable.pageCount] = pageList.size
                }
            }

            val mangaId = manga[MangaTable.id].value
            val chapterIndex = chapter[ChapterTable.sourceOrder]
            FetchChapterPagesPayload(
                clientMutationId = clientMutationId,
                pages = List(pageList.size) { index ->
                    "/api/v1/manga/$mangaId/chapter/$chapterIndex/page/$index"
                },
                chapter = ChapterType(
                    transaction { ChapterTable.select { ChapterTable.id eq chapterId }.first() }
                )
            )
        }
    }
}
