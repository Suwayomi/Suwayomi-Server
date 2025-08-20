package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.ChapterMetaType
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.SyncConflictInfoType
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReadyById
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import java.net.URLEncoder
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
                        .associateBy(
                            { it[ChapterTable.id].value },
                            { it[ChapterTable.pageCount] },
                        )
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

        // Sync with KoreaderSync when progress is updated
        if (patch.lastPageRead != null || patch.isRead == true) {
            GlobalScope.launch {
                ids.forEach { chapterId ->
                    KoreaderSyncService.pushProgress(chapterId)
                }
            }
        }
    }

    fun updateChapter(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UpdateChapterInput,
    ): DataFetcherResult<UpdateChapterPayload?> =
        asDataFetcherResult {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
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

    fun updateChapters(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UpdateChaptersInput,
    ): DataFetcherResult<UpdateChaptersPayload?> =
        asDataFetcherResult {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
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

    fun fetchChapters(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: FetchChaptersInput,
    ): CompletableFuture<DataFetcherResult<FetchChaptersPayload?>> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
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

    fun setChapterMeta(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: SetChapterMetaInput,
    ): DataFetcherResult<SetChapterMetaPayload?> =
        asDataFetcherResult {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
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

    fun deleteChapterMeta(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DeleteChapterMetaInput,
    ): DataFetcherResult<DeleteChapterMetaPayload?> =
        asDataFetcherResult {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
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
        val syncConflict: SyncConflictInfoType?,
    )

    fun fetchChapterPages(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: FetchChapterPagesInput,
    ): CompletableFuture<DataFetcherResult<FetchChapterPagesPayload?>> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapterId) = input
        val paramsMap = input.toParams()

        return future {
            asDataFetcherResult {
                var chapter = getChapterDownloadReadyById(chapterId)
                val syncResult = KoreaderSyncService.checkAndPullProgress(chapter.id)
                var syncConflictInfo: SyncConflictInfoType? = null

                if (syncResult != null) {
                    if (syncResult.isConflict) {
                        syncConflictInfo =
                            SyncConflictInfoType(
                                deviceName = syncResult.device,
                                remotePage = syncResult.pageRead,
                            )
                    }

                    if (syncResult.shouldUpdate) {
                        // Update DB for SILENT and RECEIVE
                        transaction {
                            ChapterTable.update({ ChapterTable.id eq chapter.id }) {
                                it[lastPageRead] = syncResult.pageRead
                                it[lastReadAt] = syncResult.timestamp
                            }
                        }
                    }
                    // For PROMPT, SILENT, and RECEIVE, return the remote progress
                    chapter =
                        chapter.copy(
                            lastPageRead = if (syncResult.shouldUpdate) syncResult.pageRead else chapter.lastPageRead,
                            lastReadAt = if (syncResult.shouldUpdate) syncResult.timestamp else chapter.lastReadAt,
                        )
                }

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
                                append(URLEncoder.encode(entry.value, Charsets.UTF_8))
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
                    syncConflict = syncConflictInfo,
                )
            }
        }
    }
}
