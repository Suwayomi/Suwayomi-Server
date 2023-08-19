package suwayomi.tachidesk.graphql.mutations

import eu.kanade.tachiyomi.source.model.SChapter
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.ChapterMetaType
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser
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

    private fun updateChapters(userId: Int, ids: List<Int>, patch: UpdateChapterPatch) {
        transaction {
            val currentChapterUserItems = ChapterUserTable.select { ChapterUserTable.chapter inList ids }
                .map { it[ChapterUserTable.chapter].value }
            if (currentChapterUserItems.size < ids.size) {
                ChapterUserTable.batchInsert(ids - currentChapterUserItems.toSet()) {
                    this[ChapterUserTable.user] = userId
                    this[ChapterUserTable.chapter] = it
                }
            }
            if (patch.isRead != null || patch.isBookmarked != null || patch.lastPageRead != null) {
                val now = Instant.now().epochSecond
                ChapterUserTable.update({ ChapterUserTable.chapter inList ids }) { update ->
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
    ): UpdateChapterPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, id, patch) = input

        updateChapters(userId, listOf(id), patch)

        val chapter = transaction {
            ChapterType(ChapterTable.getWithUserData(userId).select { ChapterTable.id eq id }.first())
        }

        return UpdateChapterPayload(
            clientMutationId = clientMutationId,
            chapter = chapter
        )
    }

    fun updateChapters(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UpdateChaptersInput
    ): UpdateChaptersPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, ids, patch) = input

        updateChapters(userId, ids, patch)

        val chapters = transaction {
            ChapterTable.getWithUserData(userId).select { ChapterTable.id inList ids }.map { ChapterType(it) }
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
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: FetchChaptersInput
    ): CompletableFuture<FetchChaptersPayload> {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, mangaId) = input

        return future {
            Chapter.fetchChapterList(userId, mangaId)
        }.thenApply {
            val chapters = transaction {
                ChapterTable.getWithUserData(userId).select { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.sourceOrder)
                    .map { ChapterType(it) }
            }

            FetchChaptersPayload(
                clientMutationId = clientMutationId,
                chapters = chapters
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
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: SetChapterMetaInput
    ): SetChapterMetaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, meta) = input

        Chapter.modifyChapterMeta(userId, meta.chapterId, meta.key, meta.value)

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
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DeleteChapterMetaInput
    ): DeleteChapterMetaPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapterId, key) = input

        val (meta, chapter) = transaction {
            val meta = ChapterMetaTable.select {
                ChapterMetaTable.user eq userId and
                    (ChapterMetaTable.ref eq chapterId) and
                    (ChapterMetaTable.key eq key)
            }.firstOrNull()

            ChapterMetaTable.deleteWhere {
                ChapterMetaTable.user eq userId and
                    (ChapterMetaTable.ref eq chapterId) and
                    (ChapterMetaTable.key eq key)
            }

            val chapter = transaction {
                ChapterType(ChapterTable.getWithUserData(userId).select { ChapterTable.id eq chapterId }.first())
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
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: FetchChapterPagesInput
    ): CompletableFuture<FetchChapterPagesPayload> {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
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
                val pageCount = pageList.size
                if (chapter[ChapterTable.pageCount] != pageCount) {
                    ChapterTable.update({ ChapterTable.id eq chapterId }) {
                        it[ChapterTable.pageCount] = pageCount
                    }
                    ChapterUserTable.select {
                        ChapterUserTable.chapter eq chapterId and (ChapterUserTable.lastPageRead greater pageCount)
                    }.forEach { row ->
                        ChapterUserTable.update({ ChapterUserTable.id eq row[ChapterUserTable.id] }) {
                            it[ChapterUserTable.lastPageRead] = pageCount
                        }
                    }
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
                    transaction { ChapterTable.getWithUserData(userId).select { ChapterTable.id eq chapterId }.first() }
                )
            )
        }
    }
}
