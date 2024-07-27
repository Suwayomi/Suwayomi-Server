package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
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
        dataFetchingEnvironment: DataFetchingEnvironment,
    ) {
        if (ids.isEmpty()) {
            return
        }

        transaction {
            if (patch.isRead != null || patch.isBookmarked != null || patch.lastPageRead != null) {
                val now = Instant.now().epochSecond

                val chapterIdToInfoMap =
                    ChapterTable
                        .slice(ChapterTable.id, ChapterTable.manga, ChapterTable.pageCount)
                        .select { ChapterTable.id inList ids }
                        .associateBy { it[ChapterTable.id].value }

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
                            val pageCount = chapterIdToInfoMap[chapterId]?.let { resultRow -> resultRow[ChapterTable.pageCount] } ?: 0
                            this[ChapterTable.lastPageRead] = it.coerceAtMost(pageCount).coerceAtLeast(0)
                            this[ChapterTable.lastReadAt] = now
                        }
                    }
                    execute(this@transaction)
                }

                ChapterType.clearCacheFor(chapterIdToInfoMap.mapValues { it.value[ChapterTable.manga].value }, dataFetchingEnvironment)
            }
        }
    }

    fun updateChapter(
        input: UpdateChapterInput,
        dataFetchingEnvironment: DataFetchingEnvironment,
    ): DataFetcherResult<UpdateChapterPayload?> {
        return asDataFetcherResult {
            val (clientMutationId, id, patch) = input

            updateChapters(listOf(id), patch, dataFetchingEnvironment)

            val chapter =
                transaction {
                    ChapterType(ChapterTable.select { ChapterTable.id eq id }.first())
                }

            UpdateChapterPayload(
                clientMutationId = clientMutationId,
                chapter = chapter,
            )
        }
    }

    fun updateChapters(
        input: UpdateChaptersInput,
        dataFetchingEnvironment: DataFetchingEnvironment,
    ): DataFetcherResult<UpdateChaptersPayload?> {
        return asDataFetcherResult {
            val (clientMutationId, ids, patch) = input

            updateChapters(ids, patch, dataFetchingEnvironment)

            val chapters =
                transaction {
                    ChapterTable.select { ChapterTable.id inList ids }.map { ChapterType(it) }
                }

            UpdateChaptersPayload(
                clientMutationId = clientMutationId,
                chapters = chapters,
            )
        }
    }

    data class FetchChaptersInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
    )

    data class FetchChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>,
    )

    fun fetchChapters(
        input: FetchChaptersInput,
        dataFetchingEnvironment: DataFetchingEnvironment,
    ): CompletableFuture<DataFetcherResult<FetchChaptersPayload?>> {
        val (clientMutationId, mangaId) = input

        return future {
            asDataFetcherResult {
                Chapter.fetchChapterList(mangaId)

                val chapters =
                    transaction {
                        ChapterTable.select { ChapterTable.manga eq mangaId }
                            .orderBy(ChapterTable.sourceOrder)
                            .map { ChapterType(it) }
                    }

                ChapterType.clearCacheFor(chapters.associate { it.id to it.mangaId }, dataFetchingEnvironment)

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

    fun setChapterMeta(
        input: SetChapterMetaInput,
        dateFetchingEnvironment: DataFetchingEnvironment,
    ): DataFetcherResult<SetChapterMetaPayload?> {
        return asDataFetcherResult {
            val (clientMutationId, meta) = input

            Chapter.modifyChapterMeta(meta.chapterId, meta.key, meta.value)

            val chapterId = input.meta.chapterId
            val mangaId = transaction { ChapterTable.select { ChapterTable.id eq chapterId }.first()[ChapterTable.manga].value }
            ChapterType.clearCacheFor(chapterId, mangaId, dateFetchingEnvironment)

            SetChapterMetaPayload(clientMutationId, meta)
        }
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

    fun deleteChapterMeta(
        input: DeleteChapterMetaInput,
        dataFetchingEnvironment: DataFetchingEnvironment,
    ): DataFetcherResult<DeleteChapterMetaPayload?> {
        return asDataFetcherResult {
            val (clientMutationId, chapterId, key) = input

            val (meta, chapter) =
                transaction {
                    val meta =
                        ChapterMetaTable.select { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }
                            .firstOrNull()

                    ChapterMetaTable.deleteWhere { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }

                    val chapter =
                        transaction {
                            ChapterType(ChapterTable.select { ChapterTable.id eq chapterId }.first())
                        }

                    ChapterType.clearCacheFor(chapter.id, chapter.mangaId, dataFetchingEnvironment)

                    if (meta != null) {
                        ChapterMetaType(meta)
                    } else {
                        null
                    } to chapter
                }

            DeleteChapterMetaPayload(clientMutationId, meta, chapter)
        }
    }

    data class FetchChapterPagesInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
    )

    data class FetchChapterPagesPayload(
        val clientMutationId: String?,
        val pages: List<String>,
        val chapter: ChapterType,
    )

    fun fetchChapterPages(
        input: FetchChapterPagesInput,
        dataFetchingEnvironment: DataFetchingEnvironment,
    ): CompletableFuture<DataFetcherResult<FetchChapterPagesPayload?>> {
        val (clientMutationId, chapterId) = input

        return future {
            asDataFetcherResult {
                val chapter = getChapterDownloadReadyById(chapterId)

                ChapterType.clearCacheFor(chapter.id, chapter.mangaId, dataFetchingEnvironment)

                FetchChapterPagesPayload(
                    clientMutationId = clientMutationId,
                    pages =
                        List(chapter.pageCount) { index ->
                            "/api/v1/manga/${chapter.mangaId}/chapter/${chapter.index}/page/$index"
                        },
                    chapter = ChapterType(chapter),
                )
            }
        }
    }
}
