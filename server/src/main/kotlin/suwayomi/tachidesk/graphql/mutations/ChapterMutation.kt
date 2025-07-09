package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.types.ChapterMetaType
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReadyById
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
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
        val lastPageRead: Int? = null,
    )

    data class UpdateChapterPayload(
        val clientMutationId: String?,
        val chapter: ChapterType,
    )

    data class UpdateChapterInput(
        val clientMutationId: String? = null,
        val id: Int,
        val patch: UpdateChapterPatch,
    )

    data class UpdateChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>,
    )

    data class UpdateChaptersInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
        val patch: UpdateChapterPatch,
    )

    private fun updateChapters(
        ids: List<Int>,
        patch: UpdateChapterPatch,
    ) {
        if (ids.isEmpty()) {
            return
        }

        transaction {
            val chapterIdToPageCount =
                if (patch.lastPageRead != null) {
                    ChapterTable
                        .select(ChapterTable.id, ChapterTable.pageCount)
                        .where { ChapterTable.id inList ids }
                        .groupBy { it[ChapterTable.id].value }
                        .mapValues { it.value.firstOrNull()?.let { it[ChapterTable.pageCount] } }
                } else {
                    emptyMap()
                }
            if (patch.isRead != null || patch.isBookmarked != null || patch.lastPageRead != null) {
                val now = Instant.now().epochSecond

                BatchUpdateStatement(ChapterTable).apply {
                    ids.forEach { chapterId ->
                        addBatch(EntityID(chapterId, ChapterTable))
                        patch.isRead?.also {
                            this[ChapterTable.isRead] = it
                        }
                        patch.isBookmarked?.also {
                            this[ChapterTable.isBookmarked] = it
                        }
                        patch.lastPageRead?.also {
                            this[ChapterTable.lastPageRead] = it.coerceAtMost(chapterIdToPageCount[chapterId] ?: 0).coerceAtLeast(0)
                            this[ChapterTable.lastReadAt] = now
                        }
                    }
                    execute(this@transaction)
                }
            }
        }
    }

    fun updateChapter(input: UpdateChapterInput): DataFetcherResult<UpdateChapterPayload?> =
        asDataFetcherResult {
            val (clientMutationId, id, patch) = input

            updateChapters(listOf(id), patch)

            val chapter =
                transaction {
                    ChapterType(ChapterTable.selectAll().where { ChapterTable.id eq id }.first())
                }

            UpdateChapterPayload(
                clientMutationId = clientMutationId,
                chapter = chapter,
            )
        }

    fun updateChapters(input: UpdateChaptersInput): DataFetcherResult<UpdateChaptersPayload?> =
        asDataFetcherResult {
            val (clientMutationId, ids, patch) = input

            updateChapters(ids, patch)

            val chapters =
                transaction {
                    ChapterTable.selectAll().where { ChapterTable.id inList ids }.map { ChapterType(it) }
                }

            UpdateChaptersPayload(
                clientMutationId = clientMutationId,
                chapters = chapters,
            )
        }

    data class FetchChaptersInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
    )

    data class FetchChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>,
    )

    fun fetchChapters(input: FetchChaptersInput): CompletableFuture<DataFetcherResult<FetchChaptersPayload?>> {
        val (clientMutationId, mangaId) = input

        return future {
            asDataFetcherResult {
                Chapter.fetchChapterList(mangaId)

                val chapters =
                    transaction {
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.manga eq mangaId }
                            .orderBy(ChapterTable.sourceOrder)
                            .map { ChapterType(it) }
                    }

                FetchChaptersPayload(
                    clientMutationId = clientMutationId,
                    chapters = chapters,
                )
            }
        }
    }

    data class SetChapterMetaInput(
        val clientMutationId: String? = null,
        val meta: ChapterMetaType,
    )

    data class SetChapterMetaPayload(
        val clientMutationId: String?,
        val meta: ChapterMetaType,
    )

    fun setChapterMeta(input: SetChapterMetaInput): DataFetcherResult<SetChapterMetaPayload?> =
        asDataFetcherResult {
            val (clientMutationId, meta) = input

            Chapter.modifyChapterMeta(meta.chapterId, meta.key, meta.value)

            SetChapterMetaPayload(clientMutationId, meta)
        }

    data class DeleteChapterMetaInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val key: String,
    )

    data class DeleteChapterMetaPayload(
        val clientMutationId: String?,
        val meta: ChapterMetaType?,
        val chapter: ChapterType,
    )

    fun deleteChapterMeta(input: DeleteChapterMetaInput): DataFetcherResult<DeleteChapterMetaPayload?> =
        asDataFetcherResult {
            val (clientMutationId, chapterId, key) = input

            val (meta, chapter) =
                transaction {
                    val meta =
                        ChapterMetaTable
                            .selectAll()
                            .where { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }
                            .firstOrNull()

                    ChapterMetaTable.deleteWhere { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }

                    val chapter =
                        transaction {
                            ChapterType(ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first())
                        }

                    if (meta != null) {
                        ChapterMetaType(meta)
                    } else {
                        null
                    } to chapter
                }

            DeleteChapterMetaPayload(clientMutationId, meta, chapter)
        }

    data class FetchChapterPagesInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val format: String? = null,
    ) {
        fun toParams(): Map<String, String> =
            buildMap {
                if (!format.isNullOrBlank()) {
                    put("format", format)
                }
            }
    }

    data class FetchChapterPagesPayload(
        val clientMutationId: String?,
        val pages: List<String>,
        val chapter: ChapterType,
    )

    fun fetchChapterPages(input: FetchChapterPagesInput): CompletableFuture<DataFetcherResult<FetchChapterPagesPayload?>> {
        val (clientMutationId, chapterId) = input
        val paramsMap = input.toParams()

        return future {
            asDataFetcherResult {
                val chapter = getChapterDownloadReadyById(chapterId)

                val params =
                    buildString {
                        if (paramsMap.isNotEmpty()) {
                            append("?")
                            paramsMap.entries.forEach { entry ->
                                if (length > 1) {
                                    append("&")
                                }
                                append(entry.key)
                                append("=")
                                append(entry.value)
                            }
                        }
                    }

                FetchChapterPagesPayload(
                    clientMutationId = clientMutationId,
                    pages =
                        List(chapter.pageCount) { index ->
                            "/api/v1/manga/${chapter.mangaId}/chapter/${chapter.index}/page/${index}$params"
                        },
                    chapter = ChapterType(chapter),
                )
            }
        }
    }
}
