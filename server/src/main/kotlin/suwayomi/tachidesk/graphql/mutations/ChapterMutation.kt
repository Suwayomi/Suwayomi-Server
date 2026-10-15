@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.queries.filter.prefixLikePattern
import suwayomi.tachidesk.graphql.types.ChapterMetaType
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.MetaInput
import suwayomi.tachidesk.graphql.types.SyncConflictInfoType
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReadyById
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.JavalinSetup.future
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

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
        userId: Int,
        ids: List<Int>,
        patch: UpdateChapterPatch,
    ) {
        if (ids.isEmpty()) {
            return
        }

        // REST parity: only chapters that actually exist get user rows — the request's
        // ids are filtered through the table first
        val existingChapterIds =
            transaction {
                val chapterIdToPageCount =
                    ChapterTable
                        .select(ChapterTable.id, ChapterTable.pageCount)
                        .where { ChapterTable.id inList ids }
                        .associateBy(
                            { it[ChapterTable.id].value },
                            { it[ChapterTable.pageCount] },
                        )

                if (patch.isRead != null || patch.isBookmarked != null || patch.lastPageRead != null) {
                    val now = Instant.now().epochSecond

                    ChapterUserTable.batchUpsert(
                        chapterIdToPageCount.keys.toList(),
                        ChapterUserTable.chapter,
                        ChapterUserTable.user,
                    ) { chapterId ->
                        this[ChapterUserTable.user] = userId
                        this[ChapterUserTable.chapter] = chapterId

                        patch.isRead?.also {
                            this[ChapterUserTable.isRead] = it
                        }
                        patch.isBookmarked?.also {
                            this[ChapterUserTable.isBookmarked] = it
                        }
                        patch.lastPageRead?.also {
                            this[ChapterUserTable.lastPageRead] =
                                // every upserted id was just selected, so a missing page count is a
                                // data bug — fail instead of silently clamping the progress to 0
                                it.coerceAtMost(chapterIdToPageCount.getValue(chapterId)).coerceAtLeast(0)
                            this[ChapterUserTable.lastReadAt] = now
                        }
                    }
                }

                chapterIdToPageCount.keys
            }

        if (patch.isRead == true && existingChapterIds.isNotEmpty()) {
            // REST parity: marking chapters read also pushes progress to logged-in trackers
            val mangaIds =
                transaction {
                    ChapterTable
                        .select(ChapterTable.manga)
                        .where { ChapterTable.id inList existingChapterIds.toList() }
                        .map { it[ChapterTable.manga].value }
                        .toSet()
                }
            Track.asyncTrackChapter(userId, mangaIds)
        }

        // Sync with KoreaderSync when progress is updated
        if (patch.lastPageRead != null || patch.isRead == true) {
            GlobalScope.launch {
                existingChapterIds.forEach { chapterId ->
                    KoreaderSyncService.pushProgress(userId, chapterId)
                }
            }
        }
    }

    @RequireAuth
    fun updateChapter(
        @GraphQLIgnore
        userId: Int,
        input: UpdateChapterInput,
    ): UpdateChapterPayload? {
        val (clientMutationId, id, patch) = input

        updateChapters(userId, listOf(id), patch)

        val chapter =
            transaction {
                ChapterType(
                    ChapterTable
                        .getWithUserData(userId)
                        .selectAll()
                        .where { ChapterTable.id eq id }
                        .firstOrNull() ?: throw IllegalArgumentException("Chapter not found: $id"),
                )
            }

        return UpdateChapterPayload(
            clientMutationId = clientMutationId,
            chapter = chapter,
        )
    }

    @RequireAuth
    fun updateChapters(
        @GraphQLIgnore
        userId: Int,
        input: UpdateChaptersInput,
    ): UpdateChaptersPayload? {
        val (clientMutationId, ids, patch) = input

        updateChapters(userId, ids, patch)

        val chapters =
            transaction {
                ChapterTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { ChapterTable.id inList ids }
                    .map { ChapterType(it) }
            }

        return UpdateChaptersPayload(
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

    @RequireAuth
    @GraphQLDeprecated("Deprecated in Tachiyomix 1.6", ReplaceWith("fetchMangaAndChapters"))
    fun fetchChapters(
        @GraphQLIgnore
        userId: Int,
        input: FetchChaptersInput,
    ): CompletableFuture<FetchChaptersPayload?> {
        val (clientMutationId, mangaId) = input

        return future {
            Manga.updateMangaAndChapters(mangaId, updateManga = false)

            val chapters =
                transaction {
                    ChapterTable
                        .getWithUserData(userId)
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

    data class SetChapterMetaInput(
        val clientMutationId: String? = null,
        val meta: ChapterMetaType,
    )

    data class SetChapterMetaPayload(
        val clientMutationId: String?,
        val meta: ChapterMetaType,
    )

    @RequireAuth
    fun setChapterMeta(
        @GraphQLIgnore
        userId: Int,
        input: SetChapterMetaInput,
    ): SetChapterMetaPayload? {
        val (clientMutationId, meta) = input

        Chapter.modifyChapterMeta(userId, meta.chapterId, meta.key, meta.value)

        return SetChapterMetaPayload(clientMutationId, meta)
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

    @RequireAuth
    fun deleteChapterMeta(
        @GraphQLIgnore
        userId: Int,
        input: DeleteChapterMetaInput,
    ): DeleteChapterMetaPayload? {
        val (clientMutationId, chapterId, key) = input

        val (meta, chapter) =
            transaction {
                val meta =
                    ChapterMetaTable
                        .selectAll()
                        .where {
                            ChapterMetaTable.user eq userId and
                                (ChapterMetaTable.ref eq chapterId) and
                                (ChapterMetaTable.key eq key)
                        }.firstOrNull()

                ChapterMetaTable.deleteWhere {
                    ChapterMetaTable.user eq userId and
                        (ChapterMetaTable.ref eq chapterId) and
                        (ChapterMetaTable.key eq key)
                }

                val chapter =
                    transaction {
                        ChapterType(
                            ChapterTable
                                .getWithUserData(userId)
                                .selectAll()
                                .where { ChapterTable.id eq chapterId }
                                .firstOrNull() ?: throw IllegalArgumentException("Chapter not found: $chapterId"),
                        )
                    }

                if (meta != null) {
                    ChapterMetaType(meta)
                } else {
                    null
                } to chapter
            }

        return DeleteChapterMetaPayload(clientMutationId, meta, chapter)
    }

    data class SetChapterMetasItem(
        val chapterIds: List<Int>,
        val metas: List<MetaInput>,
    )

    data class SetChapterMetasInput(
        val clientMutationId: String? = null,
        val items: List<SetChapterMetasItem>,
    )

    data class SetChapterMetasPayload(
        val clientMutationId: String?,
        val metas: List<ChapterMetaType>,
        val chapters: List<ChapterType>,
    )

    @RequireAuth
    fun setChapterMetas(
        @GraphQLIgnore
        userId: Int,
        input: SetChapterMetasInput,
    ): SetChapterMetasPayload? {
        val (clientMutationId, items) = input

        val metaByChapterId =
            items
                .flatMap { item ->
                    val metaMap = item.metas.associate { it.key to it.value }
                    item.chapterIds.map { chapterId -> chapterId to metaMap }
                }.groupBy({ it.first }, { it.second })
                .mapValues { (_, maps) -> maps.reduce { acc, map -> acc + map } }

        Chapter.modifyChaptersMetas(userId, metaByChapterId)

        val allChapterIds = metaByChapterId.keys
        val allMetaKeys = metaByChapterId.values.flatMap { it.keys }.distinct()

        val (updatedMetas, chapters) =
            transaction {
                val updatedMetas =
                    ChapterMetaTable
                        .selectAll()
                        .where {
                            (ChapterMetaTable.user eq userId) and (ChapterMetaTable.ref inList allChapterIds) and
                                (ChapterMetaTable.key inList allMetaKeys)
                        }.map { ChapterMetaType(it) }

                val chapters =
                    ChapterTable
                        .getWithUserData(userId)
                        .selectAll()
                        .where { ChapterTable.id inList allChapterIds }
                        .map { ChapterType(it) }
                        .distinctBy { it.id }

                updatedMetas to chapters
            }

        return SetChapterMetasPayload(clientMutationId, updatedMetas, chapters)
    }

    data class DeleteChapterMetasItem(
        val chapterIds: List<Int>,
        val keys: List<String>? = null,
        val prefixes: List<String>? = null,
    )

    data class DeleteChapterMetasInput(
        val clientMutationId: String? = null,
        val items: List<DeleteChapterMetasItem>,
    )

    data class DeleteChapterMetasPayload(
        val clientMutationId: String?,
        val metas: List<ChapterMetaType>,
        val chapters: List<ChapterType>,
    )

    @RequireAuth
    fun deleteChapterMetas(
        @GraphQLIgnore
        userId: Int,
        input: DeleteChapterMetasInput,
    ): DeleteChapterMetasPayload? {
        val (clientMutationId, items) = input

        items.forEach { item ->
            require(!item.keys.isNullOrEmpty() || !item.prefixes.isNullOrEmpty()) {
                "Either 'keys' or 'prefixes' must be provided for each item"
            }
        }

        val (allDeletedMetas, allChapterIds) =
            transaction {
                val deletedMetas = mutableListOf<ChapterMetaType>()
                val chapterIds = mutableSetOf<Int>()

                items.forEach { item ->
                    val keyCondition: Op<Boolean>? =
                        item.keys?.takeIf { it.isNotEmpty() }?.let { ChapterMetaTable.key inList it }

                    // empty prefixes can't form a meaningful LIKE pattern, and each prefix is
                    // escaped so a caller's '%', '_' or '\' can't act as a wildcard
                    val prefixCondition: Op<Boolean>? =
                        item.prefixes
                            ?.filter { it.isNotEmpty() }
                            ?.map { (ChapterMetaTable.key like prefixLikePattern(it)) as Op<Boolean> }
                            ?.reduceOrNull { acc, op -> acc or op }

                    val metaKeyCondition =
                        if (keyCondition != null && prefixCondition != null) {
                            keyCondition or prefixCondition
                        } else {
                            keyCondition ?: prefixCondition
                        }
                    requireNotNull(metaKeyCondition) {
                        "At least one non-empty key or prefix is required for each item"
                    }

                    val condition = (ChapterMetaTable.user eq userId) and (ChapterMetaTable.ref inList item.chapterIds) and metaKeyCondition

                    deletedMetas +=
                        ChapterMetaTable
                            .selectAll()
                            .where { condition }
                            .map { ChapterMetaType(it) }

                    ChapterMetaTable.deleteWhere { condition }
                    chapterIds += item.chapterIds
                }

                deletedMetas to chapterIds
            }

        val chapters =
            transaction {
                ChapterTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { ChapterTable.id inList allChapterIds }
                    .map { ChapterType(it) }
                    .distinctBy { it.id }
            }

        return DeleteChapterMetasPayload(clientMutationId, allDeletedMetas, chapters)
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

    @RequireAuth
    fun fetchChapterPages(
        @GraphQLIgnore
        userId: Int,
        input: FetchChapterPagesInput,
    ): CompletableFuture<FetchChapterPagesPayload?> {
        val (clientMutationId, chapterId) = input
        val paramsMap = input.toParams()

        return future {
            val chapter = getChapterDownloadReadyById(userId, chapterId)
            // a hung or failing sync server must not stall page loading:
            // time-box the progress pull and degrade to "no sync" on failure (REST parity)
            val syncResult =
                runCatching {
                    withTimeoutOrNull(10.seconds) {
                        KoreaderSyncService.checkAndPullProgress(userId, chapter.id)
                    }
                }.onFailure {
                    logger.warn(it) { "KoreaderSync progress check failed for chapter ${chapter.id}, continuing without it" }
                }.getOrNull()
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
                        ChapterUserTable.upsert(ChapterUserTable.chapter, ChapterUserTable.user) {
                            it[user] = userId
                            it[ChapterUserTable.chapter] = chapter.id
                            it[lastPageRead] = syncResult.pageRead
                            it[lastReadAt] = syncResult.timestamp
                        }
                    }
                }
                // For PROMPT, SILENT, and RECEIVE, return the remote progress — applied to
                // the payload chapter below so a PROMPT client can show the remote position
                // without it being committed to the DB
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

            val chapterRow =
                transaction {
                    ChapterTable
                        .getWithUserData(userId)
                        .selectAll()
                        .where { ChapterTable.id eq chapter.id }
                        .firstOrNull() ?: throw IllegalArgumentException("Chapter not found: ${chapter.id}")
                }

            FetchChapterPagesPayload(
                clientMutationId = clientMutationId,
                pages =
                    List(chapter.pageCount) { index ->
                        "/api/v1/manga/${chapter.mangaId}/chapter/${chapter.index}/page/${index}$params"
                    },
                chapter =
                    ChapterType(
                        id = chapterRow[ChapterTable.id].value,
                        url = chapterRow[ChapterTable.url],
                        name = chapterRow[ChapterTable.name],
                        uploadDate = chapterRow[ChapterTable.date_upload],
                        chapterNumber = chapterRow[ChapterTable.chapter_number],
                        scanlator = chapterRow[ChapterTable.scanlator],
                        mangaId = chapterRow[ChapterTable.manga].value,
                        sourceOrder = chapterRow[ChapterTable.sourceOrder],
                        realUrl = chapterRow[ChapterTable.realUrl],
                        fetchedAt = chapterRow[ChapterTable.fetchedAt],
                        pageCount = chapterRow[ChapterTable.pageCount],
                        isRead = chapterRow.getOrNull(ChapterUserTable.isRead) ?: false,
                        isBookmarked = chapterRow.getOrNull(ChapterUserTable.isBookmarked) ?: false,
                        // every non-null syncResult surfaces the remote progress (REST parity);
                        // for shouldUpdate it is also what the row already carries
                        lastPageRead = syncResult?.pageRead ?: (chapterRow.getOrNull(ChapterUserTable.lastPageRead) ?: 0),
                        lastReadAt = syncResult?.timestamp ?: (chapterRow.getOrNull(ChapterUserTable.lastReadAt) ?: 0),
                        isDownloaded = chapterRow.getOrNull(ChapterUserTable.isDownloaded) ?: false,
                    ),
                syncConflict = syncConflictInfo,
            )
        }
    }
}
