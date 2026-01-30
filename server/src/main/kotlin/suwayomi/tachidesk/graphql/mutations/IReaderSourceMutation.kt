/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.execution.DataFetcherResult
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangasPageInfo
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderChapterType
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.graphql.types.IReaderPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.manga.impl.IReaderSource
import suwayomi.tachidesk.manga.model.table.IReaderChapterTable
import suwayomi.tachidesk.manga.model.table.IReaderNovelTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceMetaTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.time.Instant
import java.util.concurrent.CompletableFuture

class IReaderSourceMutation {
    enum class FetchIReaderNovelType {
        SEARCH,
        POPULAR,
        LATEST,
    }

    data class FetchIReaderNovelsInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val source: Long,
        @GraphQLDescription("Type of fetch operation")
        val type: FetchIReaderNovelType,
        @GraphQLDescription("Page number (1-indexed)")
        val page: Int,
        @GraphQLDescription("Search query (required for SEARCH type)")
        val query: String? = null,
    )

    data class FetchIReaderNovelsPayload(
        val clientMutationId: String?,
        val novels: List<IReaderNovelType>,
        val hasNextPage: Boolean,
    )


    @RequireAuth
    @GraphQLDescription("Fetch novels from an IReader source")
    fun fetchIReaderNovels(input: FetchIReaderNovelsInput): CompletableFuture<DataFetcherResult<FetchIReaderNovelsPayload?>> {
        val (clientMutationId, sourceId, type, page, query) = input

        return future {
            asDataFetcherResult {
                require(page > 0) { "Page must be greater than 0" }

                val novelsPage =
                    when (type) {
                        FetchIReaderNovelType.SEARCH -> {
                            require(!query.isNullOrBlank()) { "Query is required for SEARCH type" }
                            IReaderNovel.searchNovels(sourceId, query, page)
                        }
                        FetchIReaderNovelType.POPULAR -> {
                            IReaderNovel.getPopularNovels(sourceId, page)
                        }
                        FetchIReaderNovelType.LATEST -> {
                            IReaderNovel.getLatestNovels(sourceId, page)
                        }
                    }

                // Save novels to database and get their IDs
                val novels = insertOrUpdateNovels(novelsPage, sourceId)

                FetchIReaderNovelsPayload(
                    clientMutationId = clientMutationId,
                    novels = novels,
                    hasNextPage = novelsPage.hasNextPage,
                )
            }
        }
    }

    /**
     * Insert or update novels in the database and return the list with database IDs
     */
    private fun insertOrUpdateNovels(
        novelsPage: MangasPageInfo,
        sourceId: Long,
    ): List<IReaderNovelType> =
        transaction {
            val novels = novelsPage.mangas

            // Find existing novels by URL and source
            val existingNovelsByUrl =
                IReaderNovelTable
                    .selectAll()
                    .where {
                        (IReaderNovelTable.sourceReference eq sourceId) and
                            (IReaderNovelTable.url inList novels.map { it.key })
                    }.associateBy { it[IReaderNovelTable.url] }

            val existingUrls = existingNovelsByUrl.keys

            // Insert new novels
            val novelsToInsert = novels.filter { it.key !in existingUrls }
            val insertedNovelsByUrl =
                IReaderNovelTable
                    .batchInsert(novelsToInsert) { novel ->
                        this[IReaderNovelTable.url] = novel.key
                        this[IReaderNovelTable.title] = novel.title
                        this[IReaderNovelTable.artist] = novel.artist
                        this[IReaderNovelTable.author] = novel.author
                        this[IReaderNovelTable.description] = novel.description
                        this[IReaderNovelTable.genre] = novel.genres.joinToString(", ")
                        this[IReaderNovelTable.status] = novel.status
                        this[IReaderNovelTable.thumbnailUrl] = novel.cover
                        this[IReaderNovelTable.sourceReference] = sourceId
                        this[IReaderNovelTable.lastFetchedAt] = Instant.now().epochSecond
                    }.associate { it[IReaderNovelTable.url] to it[IReaderNovelTable.id].value }

            // Update existing novels that are not in library
            val novelsToUpdate =
                novels
                    .mapNotNull { novel ->
                        existingNovelsByUrl[novel.key]?.let { novel to it }
                    }.filterNot { (_, row) -> row[IReaderNovelTable.inLibrary] }

            if (novelsToUpdate.isNotEmpty()) {
                BatchUpdateStatement(IReaderNovelTable).apply {
                    novelsToUpdate.forEach { (novel, row) ->
                        addBatch(EntityID(row[IReaderNovelTable.id].value, IReaderNovelTable))
                        this[IReaderNovelTable.title] = novel.title
                        this[IReaderNovelTable.artist] = novel.artist ?: row[IReaderNovelTable.artist]
                        this[IReaderNovelTable.author] = novel.author ?: row[IReaderNovelTable.author]
                        this[IReaderNovelTable.description] = novel.description ?: row[IReaderNovelTable.description]
                        this[IReaderNovelTable.genre] = novel.genres.joinToString(", ").ifEmpty { row[IReaderNovelTable.genre] }
                        this[IReaderNovelTable.status] = novel.status
                        this[IReaderNovelTable.thumbnailUrl] = novel.cover ?: row[IReaderNovelTable.thumbnailUrl]
                        this[IReaderNovelTable.lastFetchedAt] = Instant.now().epochSecond
                    }
                    execute(this@transaction)
                }
            }

            // Combine all URLs to IDs
            val allNovelsByUrl =
                existingNovelsByUrl.mapValues { it.value[IReaderNovelTable.id].value } + insertedNovelsByUrl

            // Return novels from database with proper IDs
            val novelIds = novels.mapNotNull { allNovelsByUrl[it.key] }
            IReaderNovelTable
                .selectAll()
                .where { IReaderNovelTable.id inList novelIds }
                .map { IReaderNovelType(it) }
        }


    data class FetchIReaderChaptersInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID (required if using novelUrl)")
        val source: Long? = null,
        @GraphQLDescription("Novel URL/key from the source (provide this OR novelId)")
        val novelUrl: String? = null,
        @GraphQLDescription("Database novel ID (provide this OR novelUrl + source)")
        val novelId: Int? = null,
    )

    data class FetchIReaderChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<IReaderChapterType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch chapters for a novel from an IReader source")
    fun fetchIReaderChapters(input: FetchIReaderChaptersInput): CompletableFuture<DataFetcherResult<FetchIReaderChaptersPayload?>> {
        val (clientMutationId, source, novelUrl, novelId) = input

        return future {
            asDataFetcherResult {
                val (resolvedNovelId, resolvedSourceId, resolvedNovelUrl) =
                    when {
                        novelId != null -> {
                            // Look up novel from database
                            val novel =
                                transaction {
                                    IReaderNovelTable
                                        .selectAll()
                                        .where { IReaderNovelTable.id eq novelId }
                                        .firstOrNull()
                                } ?: throw IllegalArgumentException("Novel with ID $novelId not found")
                            Triple(novelId, novel[IReaderNovelTable.sourceReference], novel[IReaderNovelTable.url])
                        }
                        novelUrl != null && source != null -> {
                            require(novelUrl.isNotBlank()) { "Novel URL cannot be empty" }
                            // Find or create the novel in the database
                            val dbNovelId = findOrCreateNovel(source, novelUrl)
                            Triple(dbNovelId, source, novelUrl)
                        }
                        else -> throw IllegalArgumentException("Either novelId OR (novelUrl + source) must be provided")
                    }

                val chapterInfos = IReaderNovel.getChapterList(resolvedSourceId, resolvedNovelUrl)

                // Save chapters to database and get their IDs
                val chapters = insertOrUpdateChapters(chapterInfos, resolvedNovelId)

                // Update novel's chaptersLastFetchedAt
                transaction {
                    IReaderNovelTable.update({ IReaderNovelTable.id eq resolvedNovelId }) {
                        it[chaptersLastFetchedAt] = Instant.now().epochSecond
                    }
                }

                FetchIReaderChaptersPayload(
                    clientMutationId = clientMutationId,
                    chapters = chapters,
                )
            }
        }
    }

    /**
     * Find an existing novel by URL and source, or create a placeholder
     */
    private fun findOrCreateNovel(
        sourceId: Long,
        novelUrl: String,
    ): Int =
        transaction {
            val existing =
                IReaderNovelTable
                    .selectAll()
                    .where {
                        (IReaderNovelTable.sourceReference eq sourceId) and
                            (IReaderNovelTable.url eq novelUrl)
                    }.firstOrNull()

            existing?.get(IReaderNovelTable.id)?.value
                ?: IReaderNovelTable
                    .batchInsert(listOf(novelUrl)) { url ->
                        this[IReaderNovelTable.url] = url
                        this[IReaderNovelTable.title] = "" // Will be populated when details are fetched
                        this[IReaderNovelTable.sourceReference] = sourceId
                    }.first()[IReaderNovelTable.id].value
        }

    /**
     * Insert or update chapters in the database and return the list with database IDs
     */
    private fun insertOrUpdateChapters(
        chapters: List<ChapterInfo>,
        novelId: Int,
    ): List<IReaderChapterType> =
        transaction {
            // Find existing chapters by URL and novel
            val existingChaptersByUrl =
                IReaderChapterTable
                    .selectAll()
                    .where {
                        (IReaderChapterTable.novel eq novelId) and
                            (IReaderChapterTable.url inList chapters.map { it.key })
                    }.associateBy { it[IReaderChapterTable.url] }

            val existingUrls = existingChaptersByUrl.keys

            // Insert new chapters
            val chaptersToInsert = chapters.filter { it.key !in existingUrls }
            val now = Instant.now().epochSecond
            val insertedChaptersByUrl =
                IReaderChapterTable
                    .batchInsert(chaptersToInsert.withIndex().toList()) { (index, chapter) ->
                        this[IReaderChapterTable.url] = chapter.key
                        this[IReaderChapterTable.name] = chapter.name
                        this[IReaderChapterTable.dateUpload] = chapter.dateUpload
                        this[IReaderChapterTable.chapterNumber] = chapter.number
                        this[IReaderChapterTable.scanlator] = chapter.scanlator.ifEmpty { null }
                        this[IReaderChapterTable.sourceOrder] = index
                        this[IReaderChapterTable.novel] = novelId
                        this[IReaderChapterTable.fetchedAt] = now
                    }.associate { it[IReaderChapterTable.url] to it[IReaderChapterTable.id].value }

            // Update existing chapters
            val chaptersToUpdate =
                chapters.mapNotNull { chapter ->
                    existingChaptersByUrl[chapter.key]?.let { chapter to it }
                }

            if (chaptersToUpdate.isNotEmpty()) {
                BatchUpdateStatement(IReaderChapterTable).apply {
                    chaptersToUpdate.forEach { (chapter, row) ->
                        addBatch(EntityID(row[IReaderChapterTable.id].value, IReaderChapterTable))
                        this[IReaderChapterTable.name] = chapter.name
                        this[IReaderChapterTable.dateUpload] = chapter.dateUpload
                        this[IReaderChapterTable.chapterNumber] = chapter.number
                        this[IReaderChapterTable.scanlator] = chapter.scanlator.ifEmpty { null }
                    }
                    execute(this@transaction)
                }
            }

            // Combine all URLs to IDs
            val allChaptersByUrl =
                existingChaptersByUrl.mapValues { it.value[IReaderChapterTable.id].value } + insertedChaptersByUrl

            // Return chapters from database with proper IDs
            val chapterIds = chapters.mapNotNull { allChaptersByUrl[it.key] }
            IReaderChapterTable
                .selectAll()
                .where { IReaderChapterTable.id inList chapterIds }
                .map { IReaderChapterType(it) }
        }


    data class FetchIReaderChapterContentInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID (required if using chapterUrl)")
        val source: Long? = null,
        @GraphQLDescription("Chapter URL/key from the source (provide this OR chapterId)")
        val chapterUrl: String? = null,
        @GraphQLDescription("Database chapter ID (provide this OR chapterUrl + source)")
        val chapterId: Int? = null,
    )

    data class FetchIReaderChapterContentPayload(
        val clientMutationId: String?,
        val pages: List<IReaderPageType>,
    )

    @RequireAuth
    @GraphQLDescription("Fetch content/pages for a chapter from an IReader source")
    fun fetchIReaderChapterContent(input: FetchIReaderChapterContentInput): CompletableFuture<DataFetcherResult<FetchIReaderChapterContentPayload?>> {
        val (clientMutationId, source, chapterUrl, chapterId) = input

        return future {
            asDataFetcherResult {
                val (resolvedSourceId, resolvedChapterUrl) =
                    when {
                        chapterId != null -> {
                            // Look up chapter from database and get source from associated novel
                            val chapterWithNovel =
                                transaction {
                                    (IReaderChapterTable innerJoin IReaderNovelTable)
                                        .selectAll()
                                        .where { IReaderChapterTable.id eq chapterId }
                                        .firstOrNull()
                                } ?: throw IllegalArgumentException("Chapter with ID $chapterId not found")
                            chapterWithNovel[IReaderNovelTable.sourceReference] to chapterWithNovel[IReaderChapterTable.url]
                        }
                        chapterUrl != null && source != null -> {
                            require(chapterUrl.isNotBlank()) { "Chapter URL cannot be empty" }
                            source to chapterUrl
                        }
                        else -> throw IllegalArgumentException("Either chapterId OR (chapterUrl + source) must be provided")
                    }

                val pages = IReaderNovel.getChapterContent(resolvedSourceId, resolvedChapterUrl)

                FetchIReaderChapterContentPayload(
                    clientMutationId = clientMutationId,
                    pages = pages.map { IReaderPageType.fromPage(it) },
                )
            }
        }
    }

    // ==================== Source Preferences ====================

    data class SetIReaderSourcePreferenceInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val sourceId: Long,
        @GraphQLDescription("Preference key")
        val key: String,
        @GraphQLDescription("Preference value as string")
        val value: String,
    )

    data class SetIReaderSourcePreferencePayload(
        val clientMutationId: String?,
        @GraphQLDescription("The preference that was set")
        val preference: IReaderSourcePreferenceResult?,
    )

    data class IReaderSourcePreferenceResult(
        val key: String,
        val value: String,
    )

    @RequireAuth
    @GraphQLDescription("Set a preference value for an IReader source")
    fun setIReaderSourcePreference(
        input: SetIReaderSourcePreferenceInput,
    ): CompletableFuture<DataFetcherResult<SetIReaderSourcePreferencePayload?>> {
        val (clientMutationId, sourceId, key, value) = input

        return future {
            asDataFetcherResult {
                // Verify source exists
                IReaderSource.getSource(sourceId)
                    ?: throw IllegalArgumentException("Source not found: $sourceId")

                // Store preference in meta table
                transaction {
                    val existing = IReaderSourceMetaTable.selectAll()
                        .where {
                            (IReaderSourceMetaTable.ref eq sourceId) and
                                (IReaderSourceMetaTable.key eq key)
                        }
                        .firstOrNull()

                    if (existing != null) {
                        IReaderSourceMetaTable.update({
                            (IReaderSourceMetaTable.ref eq sourceId) and
                                (IReaderSourceMetaTable.key eq key)
                        }) {
                            it[IReaderSourceMetaTable.value] = value
                        }
                    } else {
                        IReaderSourceMetaTable.insert {
                            it[ref] = sourceId
                            it[IReaderSourceMetaTable.key] = key
                            it[IReaderSourceMetaTable.value] = value
                        }
                    }
                }

                // Also update the PreferenceStore so the source picks up the change
                val prefStore = IReaderSource.getSourcePreferenceStore(sourceId)
                prefStore?.getString(key, "")?.set(value)

                SetIReaderSourcePreferencePayload(
                    clientMutationId = clientMutationId,
                    preference = IReaderSourcePreferenceResult(key, value),
                )
            }
        }
    }

    data class DeleteIReaderSourcePreferenceInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Source ID")
        val sourceId: Long,
        @GraphQLDescription("Preference key to delete")
        val key: String,
    )

    data class DeleteIReaderSourcePreferencePayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    @RequireAuth
    @GraphQLDescription("Delete a preference for an IReader source")
    fun deleteIReaderSourcePreference(
        input: DeleteIReaderSourcePreferenceInput,
    ): CompletableFuture<DataFetcherResult<DeleteIReaderSourcePreferencePayload?>> {
        val (clientMutationId, sourceId, key) = input

        return future {
            asDataFetcherResult {
                // Verify source exists
                IReaderSource.getSource(sourceId)
                    ?: throw IllegalArgumentException("Source not found: $sourceId")

                val deleted = transaction {
                    IReaderSourceMetaTable.deleteWhere {
                        (IReaderSourceMetaTable.ref eq sourceId) and
                            (IReaderSourceMetaTable.key eq key)
                    }
                }

                // Also delete from PreferenceStore
                val prefStore = IReaderSource.getSourcePreferenceStore(sourceId)
                prefStore?.getString(key, "")?.delete()

                DeleteIReaderSourcePreferencePayload(
                    clientMutationId = clientMutationId,
                    success = deleted > 0,
                )
            }
        }
    }
}
