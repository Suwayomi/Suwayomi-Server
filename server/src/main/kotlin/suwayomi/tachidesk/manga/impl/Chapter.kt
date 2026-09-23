package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.chapter.ChapterSanitizer.sanitize
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jsoup.Jsoup
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.DownloadManager.EnqueueInput
import suwayomi.tachidesk.manga.impl.download.lnreader.LnChapterDownloader
import suwayomi.tachidesk.manga.impl.download.lnreader.LnEpubStore
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.manga.impl.text.ChapterTextContent
import suwayomi.tachidesk.manga.impl.text.ChapterTextSanitizer
import suwayomi.tachidesk.manga.impl.text.ChapterTextSource
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.impl.util.updateChapterDownloadDir
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.PaginatedList
import suwayomi.tachidesk.manga.model.dataclass.paginatedFrom
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.manga.model.table.toSChapter
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.util.TreeSet
import kotlin.math.max

private val applicationDirs: ApplicationDirs by injectLazy()

private fun List<ChapterDataClass>.removeDuplicates(currentChapter: ChapterDataClass): List<ChapterDataClass> =
    groupBy { it.chapterNumber }
        .map { (_, chapters) ->
            chapters.find { it.id == currentChapter.id }
                ?: chapters.find { it.scanlator == currentChapter.scanlator }
                ?: chapters.first()
        }

object Chapter {
    private val logger = KotlinLogging.logger { }

    /** get chapter list when showing a manga */
    suspend fun getChapterList(
        mangaId: Int,
        onlineFetch: Boolean = false,
    ): List<ChapterDataClass> =
        if (onlineFetch) {
            getSourceChapters(mangaId)
        } else {
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .map {
                        ChapterTable.toDataClass(it)
                    }
            }.ifEmpty {
                getSourceChapters(mangaId)
            }
        }

    fun getCountOfMangaChapters(mangaId: Int): Int =
        transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .count()
                .toInt()
        }

    private suspend fun getSourceChapters(mangaId: Int): List<ChapterDataClass> {
        Manga.updateMangaAndChapters(
            mangaId,
            updateManga = false,
            updateChapters = true,
        )

        return transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                .map {
                    ChapterTable.toDataClass(it)
                }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun updateChapterListDatabase(
        mangaEntry: ResultRow,
        chapters: List<SChapter>,
        source: Source,
    ): List<SChapter> {
        val mangaId = mangaEntry[MangaTable.id].value
        val sourceContentType =
            when {
                source is ChapterTextSource -> SourceContentType.LIGHT_NOVEL
                source !is StubSource -> SourceContentType.MANGA
                else -> null
            }
        if (sourceContentType != null && mangaEntry[MangaTable.contentType] != sourceContentType) {
            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) { it[contentType] = sourceContentType }
            }
        }
        val isNovel =
            if (source is StubSource) {
                mangaEntry[MangaTable.contentType] == SourceContentType.LIGHT_NOVEL
            } else {
                source is ChapterTextSource
            }
        val currentLatestChapterNumber = Manga.getLatestChapter(mangaEntry[MangaTable.id].value)?.chapterNumber ?: 0f
        val numberOfCurrentChapters = getCountOfMangaChapters(mangaEntry[MangaTable.id].value)
        // it's possible that the source returns a list containing chapters with the same url
        // once such duplicated chapters have been added, they aren't being removed anymore as long as there is
        // a chapter with the same url in the fetched chapter list, even if the duplicated chapter itself
        // does not exist anymore on the source
        val uniqueChapters = chapters.distinctBy { it.url }

        if (uniqueChapters.isEmpty()) {
            throw Exception("No chapters found")
        }

        // Recognize number for new chapters.
        val sManga =
            SManga.create().apply {
                url = mangaEntry[MangaTable.url]
                title = mangaEntry[MangaTable.title]
                thumbnail_url = mangaEntry[MangaTable.thumbnail_url]
                artist = mangaEntry[MangaTable.artist]
                author = mangaEntry[MangaTable.author]
                description = mangaEntry[MangaTable.description]
                genre = mangaEntry[MangaTable.genre]
                status = mangaEntry[MangaTable.status]
                update_strategy = UpdateStrategy.valueOf(mangaEntry[MangaTable.updateStrategy])
                memo = mangaEntry[MangaTable.memo]
                initialized = mangaEntry[MangaTable.initialized]
            }
        uniqueChapters.forEach { chapter ->
            (source as? HttpSource)?.prepareNewChapter(chapter, sManga)
            val chapterNumber =
                ChapterRecognition.parseChapterNumber(
                    mangaEntry[MangaTable.title],
                    chapter.name,
                    chapter.chapter_number.toDouble(),
                )
            chapter.chapter_number = chapterNumber.toFloat()
            chapter.name = chapter.name.sanitize(mangaEntry[MangaTable.title])
            chapter.scanlator = chapter.scanlator?.ifBlank { null }?.trim()
        }

        val now = Instant.now()
        // Used to not set upload date of older chapters
        // to a higher value than newer chapters
        var maxSeenUploadDate = 0L

        val chaptersInDb =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaEntry[MangaTable.id].value }
                    .map { ChapterTable.toDataClass(it) }
                    .toList()
            }

        // new chapters after they have been added to the database for auto downloads
        val insertedChapterIds = mutableListOf<Int>()

        val chaptersToInsert = mutableListOf<ChapterDataClass>() // do not yet have an ID from the database
        val chaptersToUpdate = mutableListOf<ChapterDataClass>()

        uniqueChapters.reversed().forEachIndexed { index, fetchedChapter ->
            val chapterEntry = chaptersInDb.find { it.url == fetchedChapter.url }

            if (mangaEntry[MangaTable.contentType] == SourceContentType.LIGHT_NOVEL) {
                val sanitizedMemo = fetchedChapter.memo.filterNot { (key, _) -> key.startsWith("suwayomi.") }.toMutableMap()
                if (chapterEntry != null) {
                    chapterEntry.memo.filter { (key, _) -> key.startsWith("suwayomi.") }.forEach { (key, value) ->
                        sanitizedMemo[key] = value
                    }
                }
                fetchedChapter.memo = JsonObject(sanitizedMemo)
            }

            val chapterData =
                ChapterDataClass.fromSChapter(
                    fetchedChapter,
                    chapterEntry?.id ?: 0,
                    index + 1,
                    now.epochSecond,
                    mangaEntry[MangaTable.id].value,
                    runCatching {
                        (source as? HttpSource)?.getChapterUrl(fetchedChapter)
                    }.getOrNull(),
                )

            if (chapterEntry == null) {
                val newChapterData =
                    if (chapterData.uploadDate == 0L) {
                        val altDateUpload = if (maxSeenUploadDate == 0L) now.toEpochMilli() else maxSeenUploadDate
                        chapterData.copy(uploadDate = altDateUpload)
                    } else {
                        maxSeenUploadDate = max(maxSeenUploadDate, chapterData.uploadDate)
                        chapterData
                    }
                chaptersToInsert.add(newChapterData)
            } else {
                val newChapterData =
                    if (chapterData.uploadDate == 0L) {
                        chapterData.copy(uploadDate = chapterEntry.uploadDate)
                    } else {
                        chapterData
                    }
                chaptersToUpdate.add(newChapterData)
            }
        }

        val deletedChapterNumbers = TreeSet<Float>()
        val deletedReadChapterNumbers = TreeSet<Float>()
        val deletedBookmarkedChapterNumbers = TreeSet<Float>()
        val deletedDownloadedChapterByChapterNumber = mutableMapOf<Float, ChapterDataClass>()
        val deletedChapterNumberDateFetchMap = mutableMapOf<Float, Long>()
        val deletedChapterByChapterNumber = mutableMapOf<Float, ChapterDataClass>()

        // clear any orphaned/duplicate chapters that are in the db but not in `chapterList`
        val chapterUrls = uniqueChapters.map { it.url }.toSet()

        val chaptersIdsToDelete =
            chaptersInDb.mapNotNull { dbChapter ->
                if (!chapterUrls.contains(dbChapter.url)) {
                    if (dbChapter.read) deletedReadChapterNumbers.add(dbChapter.chapterNumber)
                    if (dbChapter.bookmarked) deletedBookmarkedChapterNumbers.add(dbChapter.chapterNumber)
                    if (dbChapter.downloaded) deletedDownloadedChapterByChapterNumber[dbChapter.chapterNumber] = dbChapter
                    deletedChapterNumbers.add(dbChapter.chapterNumber)
                    deletedChapterNumberDateFetchMap[dbChapter.chapterNumber] = dbChapter.fetchedAt
                    deletedChapterByChapterNumber[dbChapter.chapterNumber] = dbChapter
                    dbChapter.id
                } else {
                    null
                }
            }

        suspendTransaction {
            // we got some clean up due
            if (chaptersIdsToDelete.isNotEmpty()) {
                DownloadManager.dequeue(chaptersIdsToDelete)
                if (isNovel) {
                    val mangaId = mangaEntry[MangaTable.id].value
                    chaptersIdsToDelete.forEach { chapterId ->
                        LnEpubStore.delete(mangaId, chapterId)
                    }
                }
                PageTable.deleteWhere { chapter inList chaptersIdsToDelete }
                ChapterTable.deleteWhere { id inList chaptersIdsToDelete }
            }

            if (chaptersToInsert.isNotEmpty()) {
                val insertedChapters =
                    ChapterTable
                        .batchInsert(chaptersToInsert) { chapter ->
                            this[ChapterTable.url] = chapter.url
                            this[ChapterTable.name] = chapter.name
                            this[ChapterTable.date_upload] = chapter.uploadDate
                            this[ChapterTable.chapter_number] = chapter.chapterNumber
                            this[ChapterTable.scanlator] = chapter.scanlator
                            this[ChapterTable.sourceOrder] = chapter.index
                            this[ChapterTable.fetchedAt] = chapter.fetchedAt
                            this[ChapterTable.manga] = chapter.mangaId
                            this[ChapterTable.realUrl] = chapter.realUrl
                            this[ChapterTable.memo] = chapter.memo
                            this[ChapterTable.isRead] = false
                            this[ChapterTable.isBookmarked] = false
                            this[ChapterTable.isDownloaded] = false
                            this[ChapterTable.lastModifiedAt] = chapter.lastModifiedAt
                            this[ChapterTable.version] = chapter.version
                            this[ChapterTable.pageCount] = -1

                            // is recognized chapter number
                            if (chapter.chapterNumber >= 0f && chapter.chapterNumber in deletedChapterNumbers) {
                                this[ChapterTable.isRead] = chapter.chapterNumber in deletedReadChapterNumbers
                                this[ChapterTable.isBookmarked] = chapter.chapterNumber in deletedBookmarkedChapterNumbers

                                // Try to use the fetch date of the original entry to not pollute 'Updates' tab
                                deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                                    this[ChapterTable.fetchedAt] = it
                                }

                                if (isNovel) {
                                    val oldDeleted = deletedChapterByChapterNumber[chapter.chapterNumber]
                                    if (oldDeleted != null) {
                                        val oldText =
                                            oldDeleted.memo["suwayomi.text"]?.let {
                                                if (it is JsonObject) it else null
                                            }
                                        if (oldText != null && oldText.containsKey("progress")) {
                                            val newText =
                                                buildJsonObject {
                                                    oldText["progress"]?.let { put("progress", it) }
                                                    oldText["updatedAt"]?.let { put("updatedAt", it) }
                                                }
                                            val currentMemo = chapter.memo.toMutableMap()
                                            currentMemo["suwayomi.text"] = newText
                                            this[ChapterTable.memo] = JsonObject(currentMemo)
                                        }
                                    }
                                }
                            }
                        }.map { ChapterTable.toDataClass(it) }

                insertedChapters.forEach { insertedChapterIds.add(it.id) }

                val chaptersToPreserveDownload =
                    insertedChapters.filter { chapter ->
                        if (isNovel) return@filter false
                        val deletedChapter =
                            deletedDownloadedChapterByChapterNumber[chapter.chapterNumber] ?: return@filter false

                        // For a new (unrecognized) chapter, we have to handle the existing downloads as obsolete in case the scanlator changed because we can't assume that the pages are still the same
                        val isSameScanlator = chapter.scanlator == deletedChapter.scanlator
                        val isPreservable = isSameScanlator && updateChapterDownloadDir(deletedChapter, chapter)

                        isPreservable
                    }

                if (chaptersToPreserveDownload.isNotEmpty()) {
                    BatchUpdateStatement(ChapterTable)
                        .apply {
                            chaptersToPreserveDownload.forEach {
                                addBatch(EntityID(it.id, ChapterTable))

                                this[ChapterTable.isDownloaded] = true
                                this[ChapterTable.pageCount] = deletedDownloadedChapterByChapterNumber[it.chapterNumber]!!.pageCount
                            }
                        }.toExecutable()
                        .execute(this@suspendTransaction)
                }
            }

            if (chaptersToUpdate.isNotEmpty()) {
                BatchUpdateStatement(ChapterTable)
                    .apply {
                        chaptersToUpdate.forEach {
                            addBatch(EntityID(it.id, ChapterTable))

                            val currentChapter = chaptersInDb.find { dbChapter -> dbChapter.id == it.id }!!

                            this[ChapterTable.name] = it.name
                            this[ChapterTable.date_upload] = it.uploadDate
                            this[ChapterTable.chapter_number] = it.chapterNumber
                            this[ChapterTable.scanlator] = it.scanlator
                            this[ChapterTable.sourceOrder] = it.index
                            this[ChapterTable.realUrl] = it.realUrl
                            this[ChapterTable.memo] = it.memo
                            this[ChapterTable.isDownloaded] = currentChapter.downloaded
                            this[ChapterTable.pageCount] = currentChapter.pageCount

                            if (!currentChapter.downloaded) {
                                return@forEach
                            }

                            val isDownloadPreservable = if (isNovel) true else updateChapterDownloadDir(currentChapter, it)
                            if (!isDownloadPreservable) {
                                this[ChapterTable.isDownloaded] = false
                                this[ChapterTable.pageCount] = -1
                            }
                        }
                    }.toExecutable()
                    .execute(this@suspendTransaction)
            }

            MangaTable.update({ MangaTable.id eq mangaEntry[MangaTable.id].value }) {
                it[chaptersLastFetchedAt] = Instant.now().epochSecond
            }
        }

        if (mangaEntry[MangaTable.inLibrary]) {
            // We have to query the inserted chapters to get the up-to-date data. I.e. "last_modified_at" is not returned by the insert statement, due to being set by a DB trigger
            val insertedChapters =
                transaction {
                    ChapterTable.selectAll().where { ChapterTable.id inList insertedChapterIds }.map(
                        ChapterTable::toDataClass,
                    )
                }
            downloadNewChapters(
                mangaEntry[MangaTable.id].value,
                currentLatestChapterNumber,
                numberOfCurrentChapters,
                insertedChapters,
            )
        }

        return uniqueChapters
    }

    private fun downloadNewChapters(
        mangaId: Int,
        prevLatestChapterNumber: Float,
        prevNumberOfChapters: Int,
        newChapters: List<ChapterDataClass>,
    ) {
        val log =
            KotlinLogging.logger(
                "${logger.name}::downloadNewChapters(" +
                    "mangaId= $mangaId, " +
                    "prevLatestChapterNumber= $prevLatestChapterNumber, " +
                    "prevNumberOfChapters= $prevNumberOfChapters, " +
                    "newChapters= ${newChapters.size}, " +
                    "autoDownloadNewChaptersLimit= ${serverConfig.autoDownloadNewChaptersLimit.value}, " +
                    "autoDownloadIgnoreReUploads= ${serverConfig.autoDownloadIgnoreReUploads.value}" +
                    ")",
            )

        if (!serverConfig.autoDownloadNewChapters.value) {
            log.debug { "automatic download is not configured" }
            return
        }

        if (newChapters.isEmpty()) {
            log.debug { "no new chapters available" }
            return
        }

        val wasInitialFetch = prevNumberOfChapters == 0
        if (wasInitialFetch) {
            log.debug { "skipping download on initial fetch" }
            return
        }

        if (!Manga.isInIncludedDownloadCategory(log, mangaId)) {
            return
        }

        val unreadChapters = Manga.getUnreadChapters(mangaId).subtract(newChapters.toSet())

        val skipDueToUnreadChapters = serverConfig.excludeEntryWithUnreadChapters.value && unreadChapters.isNotEmpty()
        if (skipDueToUnreadChapters) {
            log.debug { "ignore due to unread chapters" }
            return
        }

        val chapterIdsToDownload = getNewChapterIdsToDownload(newChapters, prevLatestChapterNumber)

        if (chapterIdsToDownload.isEmpty()) {
            log.debug { "no chapters available for download" }
            return
        }

        log.info { "download ${chapterIdsToDownload.size} new chapter(s)..." }

        DownloadManager.enqueue(EnqueueInput(chapterIdsToDownload))
    }

    private fun getNewChapterIdsToDownload(
        newChapters: List<ChapterDataClass>,
        prevLatestChapterNumber: Float,
    ): List<Int> {
        val reUploadedChapters = newChapters.filter { it.chapterNumber < prevLatestChapterNumber }
        val actualNewChapters = newChapters.subtract(reUploadedChapters.toSet()).toList()
        val chaptersToConsiderForDownloadLimit =
            if (serverConfig.autoDownloadIgnoreReUploads.value) {
                if (actualNewChapters.isNotEmpty()) actualNewChapters.removeDuplicates(actualNewChapters[0]) else emptyList()
            } else {
                newChapters.removeDuplicates(newChapters[0])
            }.sortedBy { it.index }

        val latestChapterToDownloadIndex =
            if (serverConfig.autoDownloadNewChaptersLimit.value == 0) {
                chaptersToConsiderForDownloadLimit.size
            } else {
                serverConfig.autoDownloadNewChaptersLimit.value.coerceIn(0, chaptersToConsiderForDownloadLimit.size)
            }
        val limitedChaptersToDownload = chaptersToConsiderForDownloadLimit.subList(0, latestChapterToDownloadIndex)
        val limitedChaptersToDownloadWithDuplicates =
            (
                limitedChaptersToDownload +
                    newChapters.filter { newChapter ->
                        limitedChaptersToDownload.find { it.chapterNumber == newChapter.chapterNumber } != null
                    }
            ).toSet()

        return limitedChaptersToDownloadWithDuplicates.map { it.id }
    }

    fun modifyChapter(
        mangaId: Int,
        chapterIndex: Int,
        isRead: Boolean?,
        isBookmarked: Boolean?,
        markPrevRead: Boolean?,
        lastPageRead: Int?,
    ): Int {
        val chapterId =
            transaction {
                val chapter =
                    ChapterTable
                        .selectAll()
                        .where { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                        .first()

                val chapterIdValue = chapter[ChapterTable.id].value

                if (listOf(isRead, isBookmarked, lastPageRead).any { it != null }) {
                    ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }) { update ->
                        isRead?.also {
                            update[ChapterTable.isRead] = it
                        }
                        isBookmarked?.also {
                            update[ChapterTable.isBookmarked] = it
                        }
                        lastPageRead?.also {
                            update[ChapterTable.lastPageRead] = it
                            update[lastReadAt] = Instant.now().epochSecond
                        }
                    }
                }

                markPrevRead?.let {
                    ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder less chapterIndex) }) {
                        it[ChapterTable.isRead] = markPrevRead
                    }
                }

                chapterIdValue
            }

        if (isRead == true || markPrevRead == true) {
            Track.asyncTrackChapter(setOf(mangaId))
        }
        if (lastPageRead != null || isRead == true) {
            asyncPushKoreaderProgress(listOf(chapterId))
        }

        return chapterId
    }

    @Serializable
    data class ChapterChange(
        val isRead: Boolean? = null,
        val isBookmarked: Boolean? = null,
        val lastPageRead: Int? = null,
        val delete: Boolean? = null,
    )

    @Serializable
    data class MangaChapterBatchEditInput(
        val chapterIds: List<Int>? = null,
        val chapterIndexes: List<Int>? = null,
        val change: ChapterChange?,
    )

    @Serializable
    data class ChapterBatchEditInput(
        val chapterIds: List<Int>? = null,
        val change: ChapterChange?,
    )

    suspend fun modifyChapters(
        input: MangaChapterBatchEditInput,
        mangaId: Int? = null,
    ) {
        // Make sure change is defined
        if (input.change == null) return
        val (isRead, isBookmarked, lastPageRead, delete) = input.change

        // Handle deleting separately
        if (delete == true) {
            deleteChapters(input, mangaId)
        }

        // return early if there are no other changes
        if (listOfNotNull(isRead, isBookmarked, lastPageRead).isEmpty()) return

        // Make sure some filter is defined
        val condition =
            when {
                mangaId != null -> {
                    // mangaId is not null, scope query under manga
                    when {
                        input.chapterIds != null -> {
                            (ChapterTable.manga eq mangaId) and (ChapterTable.id inList input.chapterIds)
                        }

                        input.chapterIndexes != null -> {
                            (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder inList input.chapterIndexes)
                        }

                        else -> {
                            null
                        }
                    }
                }

                else -> {
                    // mangaId is null, only chapterIndexes is valid for this case
                    when {
                        input.chapterIds != null -> {
                            (ChapterTable.id inList input.chapterIds)
                        }

                        else -> {
                            null
                        }
                    }
                }
            } ?: return

        transaction {
            val now = Instant.now().epochSecond
            ChapterTable.update({ condition }) { update ->
                isRead?.also {
                    update[ChapterTable.isRead] = it
                }
                isBookmarked?.also {
                    update[ChapterTable.isBookmarked] = it
                }
                lastPageRead?.also {
                    update[ChapterTable.lastPageRead] = it
                    update[lastReadAt] = now
                }
            }
        }

        if (isRead == true) {
            val mangaIds =
                transaction {
                    ChapterTable
                        .selectAll()
                        .where(condition)
                        .map { it[ChapterTable.manga].value }
                        .toSet()
                }
            Track.asyncTrackChapter(mangaIds)
        }
        if (lastPageRead != null || isRead == true) {
            val targetIds = transaction { ChapterTable.select(ChapterTable.id).where(condition).map { it[ChapterTable.id].value } }
            if (targetIds.isNotEmpty()) {
                asyncPushKoreaderProgress(targetIds)
            }
        }
    }

    fun asyncPushKoreaderProgress(chapterIds: Collection<Int>) {
        if (chapterIds.isEmpty() || !KoreaderSyncService.hasConfiguredCredentials()) return
        CoroutineScope(Dispatchers.IO).launch {
            chapterIds.forEach { id ->
                KoreaderSyncService.pushProgress(id)
            }
        }
    }

    fun applyKoreaderSyncResult(
        chapterId: Int,
        syncResult: KoreaderSyncService.SyncResult,
    ): ChapterDataClass =
        transaction {
            val currentChapter =
                ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.firstOrNull()
                    ?: throw NoSuchElementException("Chapter $chapterId not found")
            if (syncResult.isNovel) {
                val currentMemo = currentChapter[ChapterTable.memo].toMutableMap()
                val existingText = currentMemo["suwayomi.text"]?.let { if (it is JsonObject) it else null }
                val newText =
                    buildJsonObject {
                        existingText?.forEach { (k, v) ->
                            if (k != "progress" && k != "koreaderProgress" && k != "koreaderProgressHash" && k != "updatedAt") {
                                put(k, v)
                            }
                        }
                        put("progress", syncResult.progressPercentage ?: 0f)
                        syncResult.rawKoreaderProgress?.let {
                            put("koreaderProgress", it)
                            syncResult.koreaderHash?.let { h -> put("koreaderProgressHash", h) }
                        }
                        put("updatedAt", syncResult.timestamp * 1000)
                    }
                currentMemo["suwayomi.text"] = newText
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[memo] = JsonObject(currentMemo)
                    it[lastReadAt] = syncResult.timestamp
                    it[version] = currentChapter[ChapterTable.version] + 1
                    it[lastModifiedAt] = Instant.now().epochSecond
                }
            } else {
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[lastPageRead] = syncResult.pageRead
                    it[lastReadAt] = syncResult.timestamp
                }
            }
            ChapterTable.toDataClass(ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first())
        }

    fun getChaptersMetaMaps(chapterIds: List<Int>): Map<Int, Map<String, String>> =
        transaction {
            ChapterMetaTable
                .selectAll()
                .where { ChapterMetaTable.ref inList chapterIds }
                .groupBy { it[ChapterMetaTable.ref].value }
                .mapValues { it.value.associate { it[ChapterMetaTable.key] to it[ChapterMetaTable.value] } }
                .withDefault { emptyMap() }
        }

    fun getChapterMetaMap(chapter: Int): Map<String, String> =
        transaction {
            ChapterMetaTable
                .selectAll()
                .where { ChapterMetaTable.ref eq chapter }
                .associate { it[ChapterMetaTable.key] to it[ChapterMetaTable.value] }
        }

    fun modifyChapterMeta(
        mangaId: Int,
        chapterIndex: Int,
        key: String,
        value: String,
    ) {
        transaction {
            val chapterId =
                ChapterTable
                    .selectAll()
                    .where { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                    .first()[ChapterTable.id]
                    .value
            modifyChapterMeta(chapterId, key, value)
        }
    }

    fun modifyChapterMeta(
        chapterId: Int,
        key: String,
        value: String,
    ) {
        modifyChaptersMetas(mapOf(chapterId to mapOf(key to value)))
    }

    fun modifyChaptersMetas(metaByChapterId: Map<Int, Map<String, String>>) {
        transaction {
            val chapterIds = metaByChapterId.keys
            val metaKeys = metaByChapterId.flatMap { it.value.keys }

            val dbMetaByChapterId =
                ChapterMetaTable
                    .selectAll()
                    .where { (ChapterMetaTable.ref inList chapterIds) and (ChapterMetaTable.key inList metaKeys) }
                    .groupBy { it[ChapterMetaTable.ref].value }

            val existingMetaByMetaId =
                chapterIds.flatMap { chapterId ->
                    val dbMetaByKey = dbMetaByChapterId[chapterId].orEmpty().associateBy { it[ChapterMetaTable.key] }
                    val existingMetas = metaByChapterId[chapterId].orEmpty().filter { (key) -> key in dbMetaByKey.keys }

                    existingMetas.map { entry ->
                        val metaId = dbMetaByKey[entry.key]!![ChapterMetaTable.id].value

                        metaId to entry
                    }
                }

            val newMetaByChapterId =
                chapterIds.flatMap { chapterId ->
                    val dbMetaByKey = dbMetaByChapterId[chapterId].orEmpty().associateBy { it[ChapterMetaTable.key] }

                    metaByChapterId[chapterId]
                        .orEmpty()
                        .filter { entry -> entry.key !in dbMetaByKey.keys }
                        .map { entry -> chapterId to entry }
                }

            if (existingMetaByMetaId.isNotEmpty()) {
                BatchUpdateStatement(ChapterMetaTable)
                    .apply {
                        existingMetaByMetaId.forEach { (metaId, entry) ->
                            addBatch(EntityID(metaId, ChapterMetaTable))
                            this[ChapterMetaTable.value] = entry.value
                        }
                    }.toExecutable()
                    .execute(this@transaction)
            }

            if (newMetaByChapterId.isNotEmpty()) {
                ChapterMetaTable.batchInsert(newMetaByChapterId) { (chapterId, entry) ->
                    this[ChapterMetaTable.ref] = EntityID(chapterId, ChapterTable)
                    this[ChapterMetaTable.key] = entry.key
                    this[ChapterMetaTable.value] = entry.value
                }
            }
        }
    }

    suspend fun deleteChapter(
        mangaId: Int,
        chapterIndex: Int,
    ) {
        suspendTransaction {
            val chapterId =
                ChapterTable
                    .selectAll()
                    .where { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                    .first()[ChapterTable.id]
                    .value

            ChapterDownloadHelper.delete(mangaId, chapterId)

            ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }) {
                it[isDownloaded] = false
            }
        }
    }

    private suspend fun deleteChapters(
        input: MangaChapterBatchEditInput,
        mangaId: Int? = null,
    ) {
        if (input.chapterIds != null) {
            deleteChapters(input.chapterIds)
        } else if (input.chapterIndexes != null && mangaId != null) {
            suspendTransaction {
                val chapterIds =
                    ChapterTable
                        .select(ChapterTable.manga, ChapterTable.id)
                        .where {
                            (ChapterTable.sourceOrder inList input.chapterIndexes) and
                                (ChapterTable.manga eq mangaId)
                        }.map { row ->
                            val chapterId = row[ChapterTable.id].value
                            ChapterDownloadHelper.delete(mangaId, chapterId)

                            chapterId
                        }

                ChapterTable.update({ ChapterTable.id inList chapterIds }) {
                    it[isDownloaded] = false
                }
            }
        }
    }

    suspend fun deleteChapters(chapterIds: List<Int>) {
        suspendTransaction {
            ChapterTable
                .select(ChapterTable.manga, ChapterTable.id)
                .where { ChapterTable.id inList chapterIds }
                .forEach { row ->
                    val chapterMangaId = row[ChapterTable.manga].value
                    val chapterId = row[ChapterTable.id].value
                    ChapterDownloadHelper.delete(chapterMangaId, chapterId)
                }

            ChapterTable.update({ ChapterTable.id inList chapterIds }) {
                it[isDownloaded] = false
            }
        }
    }

    fun getRecentChapters(pageNum: Int): PaginatedList<MangaChapterDataClass> =
        paginatedFrom(pageNum) {
            transaction {
                (ChapterTable innerJoin MangaTable)
                    .selectAll()
                    .where {
                        (MangaTable.inLibrary eq true) and
                            (MangaTable.contentType eq SourceContentType.MANGA) and
                            (ChapterTable.fetchedAt greater MangaTable.inLibraryAt)
                    }.orderBy(ChapterTable.fetchedAt to SortOrder.DESC)
                    .map {
                        MangaChapterDataClass(
                            MangaTable.toDataClass(it),
                            ChapterTable.toDataClass(it),
                        )
                    }
            }
        }

    fun updateChapterProgress(
        mangaId: Int,
        chapterIndex: Int,
        pageNo: Int,
    ): Int {
        val chapterData =
            transaction {
                ChapterTable
                    .selectAll()
                    .where {
                        (ChapterTable.sourceOrder eq chapterIndex) and
                            (ChapterTable.manga eq mangaId)
                    }.first()
                    .let { ChapterTable.toDataClass(it) }
            }

        val oneIndexedPageNo = pageNo.inc()
        val isRead = chapterData.pageCount.takeIf { it == oneIndexedPageNo }?.let { true }

        modifyChapter(
            mangaId,
            chapterIndex,
            isRead = isRead,
            lastPageRead = pageNo,
            isBookmarked = null,
            markPrevRead = null,
        )

        return chapterData.id
    }

    fun updateTextProgress(
        chapterId: Int,
        progress: Float,
    ): ChapterDataClass {
        require(progress.isFinite()) { "Novel reading progress must be finite" }
        val clampedProgress = progress.coerceIn(0.0f, 1.0f)
        val now = Instant.now().epochSecond

        val (updatedChapter, newlyMarkedRead) =
            transaction {
                val chapterRow =
                    (ChapterTable innerJoin MangaTable)
                        .select(ChapterTable.columns + MangaTable.contentType)
                        .where { ChapterTable.id eq chapterId }
                        .firstOrNull()
                        ?: throw NoSuchElementException("Chapter $chapterId not found")
                require(chapterRow[MangaTable.contentType] == SourceContentType.LIGHT_NOVEL) {
                    "Chapter $chapterId is not a light novel chapter"
                }

                val currentMemo = chapterRow[ChapterTable.memo].toMutableMap()
                val existingText = currentMemo["suwayomi.text"] as? JsonObject

                currentMemo["suwayomi.text"] =
                    buildJsonObject {
                        existingText?.forEach { (k, v) ->
                            if (k != "koreaderProgress" && k != "koreaderProgressHash") {
                                put(k, v)
                            }
                        }
                        put("progress", JsonPrimitive(clampedProgress))
                        put("updatedAt", JsonPrimitive(System.currentTimeMillis()))
                    }

                val willMarkRead = clampedProgress >= 0.95f && !chapterRow[ChapterTable.isRead]
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[memo] = JsonObject(currentMemo)
                    if (clampedProgress > 0f) {
                        it[lastReadAt] = now
                    }
                    if (!willMarkRead) {
                        it[version] = chapterRow[ChapterTable.version] + 1
                    }
                    if (clampedProgress >= 0.95f) {
                        it[isRead] = true
                    }
                }

                ChapterTable.toDataClass(ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()) to willMarkRead
            }

        if (newlyMarkedRead) {
            Track.asyncTrackChapter(setOf(updatedChapter.mangaId))
        }
        asyncPushKoreaderProgress(listOf(chapterId))

        return updatedChapter
    }

    suspend fun getChapterText(chapterId: Int): ChapterTextContent {
        val (chapterRow, mangaRow) =
            suspendTransaction {
                val ch =
                    ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.firstOrNull()
                        ?: throw NoSuchElementException("Chapter $chapterId not found")
                val mg =
                    MangaTable.selectAll().where { MangaTable.id eq ch[ChapterTable.manga] }.firstOrNull()
                        ?: throw NoSuchElementException("Manga ${ch[ChapterTable.manga]} not found")
                Pair(ch, mg)
            }

        val mangaId = mangaRow[MangaTable.id].value
        if (mangaRow[MangaTable.contentType] == SourceContentType.LIGHT_NOVEL &&
            LnEpubStore.existsValid(mangaId, chapterId)
        ) {
            val downloadedContent = LnEpubStore.readChapterContent(mangaId, chapterId, resolveAssetsToDataUri = true)
            if (downloadedContent != null) {
                LnEpubStore.healChapterDownloadState(mangaId, chapterId)
                return ChapterTextContent(
                    html = downloadedContent.html,
                    fromDownload = true,
                    customCss = downloadedContent.customCss,
                    customJs = downloadedContent.customJs,
                )
            }
        }
        val sourceId = mangaRow[MangaTable.sourceReference]
        val source = GetSource.getSourceOrNull(sourceId)
        val isNovel = mangaRow[MangaTable.contentType] == SourceContentType.LIGHT_NOVEL || source is ChapterTextSource
        if (!isNovel) {
            throw IllegalStateException("Chapter $chapterId is not a light novel chapter")
        }
        if (source !is ChapterTextSource) {
            throw IllegalStateException("Source $sourceId does not support text content")
        }

        if (mangaRow[MangaTable.contentType] != SourceContentType.LIGHT_NOVEL) {
            suspendTransaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[contentType] = SourceContentType.LIGHT_NOVEL
                }
            }
        }
        LnEpubStore.healChapterDownloadState(mangaId, chapterId)

        val sChapter = chapterRow.toSChapter()
        val rawText = source.getChapterText(sChapter)
        val baseUrl = (source as? HttpSource)?.baseUrl
        val sanitizedHtml = ChapterTextSanitizer.sanitize(rawText.text, baseUrl)
        val chapterDoc = Jsoup.parseBodyFragment(sanitizedHtml)
        chapterDoc.select("img[src]").forEach { img ->
            val imageUrl = img.attr("src").trim()
            if (
                imageUrl.startsWith("http://", ignoreCase = true) ||
                imageUrl.startsWith("https://", ignoreCase = true)
            ) {
                img
                    .attr("data-lnreader-image-url", imageUrl)
                    .attr("data-lnreader-chapter-id", chapterId.toString())
                    .attr(
                        "data-lnreader-image-token",
                        LnChapterDownloader.createLiveIllustrationToken(chapterId, imageUrl),
                    ).removeAttr("src")
            }
        }

        return ChapterTextContent(
            html = chapterDoc.body().html(),
            fromDownload = false,
            customCss = rawText.customCss,
            customJs = rawText.customJs,
        )
    }

    suspend fun getChapterTextOrNull(chapterId: Int): ChapterTextContent? =
        try {
            getChapterText(chapterId)
        } catch (_: IllegalStateException) {
            null
        } catch (_: NoSuchElementException) {
            null
        }
}
