package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.chapter.ChapterSanitizer.sanitize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Manga.getManga
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.DownloadManager.EnqueueInput
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.dataclass.MangaChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.PaginatedList
import suwayomi.tachidesk.manga.model.dataclass.paginatedFrom
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import java.time.Instant
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import kotlin.math.max

object Chapter {
    private val logger = KotlinLogging.logger { }

    /** get chapter list when showing a manga */
    suspend fun getChapterList(
        mangaId: Int,
        onlineFetch: Boolean = false,
    ): List<ChapterDataClass> {
        return if (onlineFetch) {
            getSourceChapters(mangaId)
        } else {
            transaction {
                ChapterTable.select { ChapterTable.manga eq mangaId }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .map {
                        ChapterTable.toDataClass(it)
                    }
            }.ifEmpty {
                getSourceChapters(mangaId)
            }
        }
    }

    fun getCountOfMangaChapters(mangaId: Int): Int {
        return transaction { ChapterTable.select { ChapterTable.manga eq mangaId }.count().toInt() }
    }

    private suspend fun getSourceChapters(mangaId: Int): List<ChapterDataClass> {
        val chapterList = fetchChapterList(mangaId)

        val dbChapterMap =
            transaction {
                ChapterTable.select { ChapterTable.manga eq mangaId }
                    .associateBy({ it[ChapterTable.url] }, { it })
            }

        val chapterIds = chapterList.map { dbChapterMap.getValue(it.url)[ChapterTable.id] }
        val chapterMetas = getChaptersMetaMaps(chapterIds)

        return chapterList.mapIndexed { index, it ->

            val dbChapter = dbChapterMap.getValue(it.url)

            ChapterDataClass(
                id = dbChapter[ChapterTable.id].value,
                url = it.url,
                name = it.name,
                uploadDate = it.date_upload,
                chapterNumber = it.chapter_number,
                scanlator = it.scanlator,
                mangaId = mangaId,
                read = dbChapter[ChapterTable.isRead],
                bookmarked = dbChapter[ChapterTable.isBookmarked],
                lastPageRead = dbChapter[ChapterTable.lastPageRead],
                lastReadAt = dbChapter[ChapterTable.lastReadAt],
                index = chapterList.size - index,
                fetchedAt = dbChapter[ChapterTable.fetchedAt],
                realUrl = dbChapter[ChapterTable.realUrl],
                downloaded = dbChapter[ChapterTable.isDownloaded],
                pageCount = dbChapter[ChapterTable.pageCount],
                chapterCount = chapterList.size,
                meta = chapterMetas.getValue(dbChapter[ChapterTable.id]),
            )
        }
    }

    val map: Cache<Int, Mutex> =
        CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build()

    suspend fun fetchChapterList(mangaId: Int): List<SChapter> {
        val mutex = map.get(mangaId) { Mutex() }
        val chapterList =
            mutex.withLock {
                val manga = getManga(mangaId)
                val source = getCatalogueSourceOrStub(manga.sourceId.toLong())

                val sManga =
                    SManga.create().apply {
                        title = manga.title
                        url = manga.url
                    }

                val numberOfCurrentChapters = getCountOfMangaChapters(mangaId)
                val chapterList = source.getChapterList(sManga)

                if (chapterList.isEmpty()) {
                    throw Exception("No chapters found")
                }

                // Recognize number for new chapters.
                chapterList.forEach { chapter ->
                    (source as? HttpSource)?.prepareNewChapter(chapter, sManga)
                    val chapterNumber = ChapterRecognition.parseChapterNumber(manga.title, chapter.name, chapter.chapter_number.toDouble())
                    chapter.chapter_number = chapterNumber.toFloat()
                    chapter.name = chapter.name.sanitize(manga.title)
                    chapter.scanlator = chapter.scanlator?.ifBlank { null }?.trim()
                }

                val now = Instant.now().epochSecond
                // Used to not set upload date of older chapters
                // to a higher value than newer chapters
                var maxSeenUploadDate = 0L

                val chaptersInDb =
                    transaction {
                        ChapterTable.select { ChapterTable.manga eq mangaId }
                            .map { ChapterTable.toDataClass(it) }
                            .toList()
                    }

                val chaptersToInsert = mutableListOf<ChapterDataClass>()
                val chaptersToUpdate = mutableListOf<ChapterDataClass>()

                chapterList.reversed().forEachIndexed { index, fetchedChapter ->
                    val chapterEntry = chaptersInDb.find { it.url == fetchedChapter.url }

                    val chapterData =
                        ChapterDataClass.fromSChapter(
                            fetchedChapter,
                            chapterEntry?.id ?: 0,
                            index + 1,
                            now,
                            mangaId,
                            runCatching {
                                (source as? HttpSource)?.getChapterUrl(fetchedChapter)
                            }.getOrNull(),
                        )

                    if (chapterEntry == null) {
                        val newChapterData =
                            if (chapterData.uploadDate == 0L) {
                                val altDateUpload = if (maxSeenUploadDate == 0L) now else maxSeenUploadDate
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
                val deletedDownloadedChapterNumbers = TreeSet<Float>()
                val deletedChapterNumberDateFetchMap = mutableMapOf<Float, Long>()

                // clear any orphaned/duplicate chapters that are in the db but not in `chapterList`
                val chapterUrls = chapterList.map { it.url }.toSet()

                val chaptersIdsToDelete =
                    chaptersInDb.mapNotNull { dbChapter ->
                        if (!chapterUrls.contains(dbChapter.url)) {
                            if (dbChapter.read) deletedReadChapterNumbers.add(dbChapter.chapterNumber)
                            if (dbChapter.bookmarked) deletedBookmarkedChapterNumbers.add(dbChapter.chapterNumber)
                            if (dbChapter.downloaded) deletedDownloadedChapterNumbers.add(dbChapter.chapterNumber)
                            deletedChapterNumbers.add(dbChapter.chapterNumber)
                            deletedChapterNumberDateFetchMap[dbChapter.chapterNumber] = dbChapter.fetchedAt
                            dbChapter.id
                        } else {
                            null
                        }
                    }

                // we got some clean up due
                if (chaptersIdsToDelete.isNotEmpty()) {
                    transaction {
                        PageTable.deleteWhere { PageTable.chapter inList chaptersIdsToDelete }
                        ChapterTable.deleteWhere { ChapterTable.id inList chaptersIdsToDelete }
                    }
                }

                transaction {
                    if (chaptersToInsert.isNotEmpty()) {
                        ChapterTable.batchInsert(chaptersToInsert) { chapter ->
                            this[ChapterTable.url] = chapter.url
                            this[ChapterTable.name] = chapter.name
                            this[ChapterTable.date_upload] = chapter.uploadDate
                            this[ChapterTable.chapter_number] = chapter.chapterNumber
                            this[ChapterTable.scanlator] = chapter.scanlator
                            this[ChapterTable.sourceOrder] = chapter.index
                            this[ChapterTable.fetchedAt] = chapter.fetchedAt
                            this[ChapterTable.manga] = chapter.mangaId
                            this[ChapterTable.realUrl] = chapter.realUrl
                            this[ChapterTable.isRead] = false
                            this[ChapterTable.isBookmarked] = false
                            this[ChapterTable.isDownloaded] = false

                            // is recognized chapter number
                            if (chapter.chapterNumber >= 0f && chapter.chapterNumber in deletedChapterNumbers) {
                                this[ChapterTable.isRead] = chapter.chapterNumber in deletedReadChapterNumbers
                                this[ChapterTable.isBookmarked] = chapter.chapterNumber in deletedBookmarkedChapterNumbers
                                this[ChapterTable.isDownloaded] = chapter.chapterNumber in deletedDownloadedChapterNumbers
                                // Try to use the fetch date of the original entry to not pollute 'Updates' tab
                                deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                                    this[ChapterTable.fetchedAt] = it
                                }
                            }
                        }
                    }

                    if (chaptersToUpdate.isNotEmpty()) {
                        BatchUpdateStatement(ChapterTable).apply {
                            chaptersToUpdate.forEach {
                                addBatch(EntityID(it.id, ChapterTable))
                                this[ChapterTable.name] = it.name
                                this[ChapterTable.date_upload] = it.uploadDate
                                this[ChapterTable.chapter_number] = it.chapterNumber
                                this[ChapterTable.scanlator] = it.scanlator
                                this[ChapterTable.sourceOrder] = it.index
                                this[ChapterTable.realUrl] = it.realUrl
                            }
                            execute(this@transaction)
                        }
                    }

                    MangaTable.update({ MangaTable.id eq mangaId }) {
                        it[MangaTable.chaptersLastFetchedAt] = Instant.now().epochSecond
                    }
                }

                val newChapters =
                    transaction {
                        ChapterTable.select { ChapterTable.manga eq mangaId }
                            .orderBy(ChapterTable.sourceOrder to SortOrder.DESC).toList()
                    }

                if (manga.inLibrary) {
                    downloadNewChapters(mangaId, numberOfCurrentChapters, newChapters)
                }

                chapterList
            }

        return chapterList
    }

    private fun downloadNewChapters(
        mangaId: Int,
        prevNumberOfChapters: Int,
        updatedChapterList: List<ResultRow>,
    ) {
        val log =
            KotlinLogging.logger(
                "${logger.name}::downloadNewChapters(" +
                    "mangaId= $mangaId, " +
                    "prevNumberOfChapters= $prevNumberOfChapters, " +
                    "updatedChapterList= ${updatedChapterList.size}, " +
                    "autoDownloadNewChaptersLimit= ${serverConfig.autoDownloadNewChaptersLimit.value}" +
                    ")",
            )

        if (!serverConfig.autoDownloadNewChapters.value) {
            log.debug { "automatic download is not configured" }
            return
        }

        // Only download if there are new chapters, or if this is the first fetch
        val newNumberOfChapters = updatedChapterList.size
        val numberOfNewChapters = newNumberOfChapters - prevNumberOfChapters

        val areNewChaptersAvailable = numberOfNewChapters > 0
        val wasInitialFetch = prevNumberOfChapters == 0

        if (!areNewChaptersAvailable) {
            log.debug { "no new chapters available" }
            return
        }

        if (wasInitialFetch) {
            log.debug { "skipping download on initial fetch" }
            return
        }

        // Verify the manga is configured to be downloaded based on it's categories.
        var mangaCategories = CategoryManga.getMangaCategories(mangaId).toSet()
        // if the manga has no categories, then it's implicitly in the default category
        if (mangaCategories.isEmpty()) {
            val defaultCategory = Category.getCategoryById(Category.DEFAULT_CATEGORY_ID)
            if (defaultCategory != null) {
                mangaCategories = setOf(defaultCategory)
            } else {
                log.warn { "missing default category" }
            }
        }

        if (mangaCategories.isNotEmpty()) {
            val downloadCategoriesMap = Category.getCategoryList().groupBy { it.includeInDownload }
            val unsetCategories = downloadCategoriesMap[IncludeOrExclude.UNSET].orEmpty()
            // We only download if it's in the include list, and not in the exclude list.
            // Use the unset categories as the included categories if the included categories is
            // empty
            val includedCategories = downloadCategoriesMap[IncludeOrExclude.INCLUDE].orEmpty().ifEmpty { unsetCategories }
            val excludedCategories = downloadCategoriesMap[IncludeOrExclude.EXCLUDE].orEmpty()
            // Only download manga that aren't in any excluded categories
            val mangaExcludeCategories = mangaCategories.intersect(excludedCategories.toSet())
            if (mangaExcludeCategories.isNotEmpty()) {
                log.debug { "download excluded by categories: '${mangaExcludeCategories.joinToString("', '") { it.name }}'" }
                return
            }
            val mangaDownloadCategories = mangaCategories.intersect(includedCategories.toSet())
            if (mangaDownloadCategories.isNotEmpty()) {
                log.debug { "download inluded by categories: '${mangaDownloadCategories.joinToString("', '") { it.name }}'" }
            } else {
                log.debug { "skipping download due to download categories configuration" }
                return
            }
        } else {
            log.debug { "no categories configured, skipping check for category download include/excludes" }
        }

        val newChapters = updatedChapterList.subList(0, numberOfNewChapters)

        // make sure to only consider the latest chapters. e.g. old unread chapters should be ignored
        val latestReadChapterIndex =
            updatedChapterList.indexOfFirst { it[ChapterTable.isRead] }.takeIf { it > -1 } ?: (updatedChapterList.size)
        val unreadChapters =
            updatedChapterList.subList(numberOfNewChapters, latestReadChapterIndex)
                .filter { !it[ChapterTable.isRead] }

        val skipDueToUnreadChapters = serverConfig.excludeEntryWithUnreadChapters.value && unreadChapters.isNotEmpty()
        if (skipDueToUnreadChapters) {
            log.debug { "ignore due to unread chapters" }
            return
        }

        val firstChapterToDownloadIndex =
            if (serverConfig.autoDownloadNewChaptersLimit.value > 0) {
                (numberOfNewChapters - serverConfig.autoDownloadNewChaptersLimit.value).coerceAtLeast(0)
            } else {
                0
            }

        val chapterIdsToDownload =
            newChapters.subList(firstChapterToDownloadIndex, numberOfNewChapters)
                .filter { !it[ChapterTable.isRead] && !it[ChapterTable.isDownloaded] }
                .map { it[ChapterTable.id].value }

        if (chapterIdsToDownload.isEmpty()) {
            log.debug { "no chapters available for download" }
            return
        }

        log.info { "download ${chapterIdsToDownload.size} new chapter(s)..." }

        DownloadManager.enqueue(EnqueueInput(chapterIdsToDownload))
    }

    fun modifyChapter(
        mangaId: Int,
        chapterIndex: Int,
        isRead: Boolean?,
        isBookmarked: Boolean?,
        markPrevRead: Boolean?,
        lastPageRead: Int?,
    ) {
        transaction {
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
                        update[ChapterTable.lastReadAt] = Instant.now().epochSecond
                    }
                }
            }

            markPrevRead?.let {
                ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder less chapterIndex) }) {
                    it[ChapterTable.isRead] = markPrevRead
                }
            }
        }

        if (isRead == true || markPrevRead == true) {
            Track.asyncTrackChapter(setOf(mangaId))
        }
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

    fun modifyChapters(
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
                mangaId != null ->
                    // mangaId is not null, scope query under manga
                    when {
                        input.chapterIds != null ->
                            Op.build { (ChapterTable.manga eq mangaId) and (ChapterTable.id inList input.chapterIds) }

                        input.chapterIndexes != null ->
                            Op.build { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder inList input.chapterIndexes) }

                        else -> null
                    }

                else -> {
                    // mangaId is null, only chapterIndexes is valid for this case
                    when {
                        input.chapterIds != null ->
                            Op.build { (ChapterTable.id inList input.chapterIds) }

                        else -> null
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
                    update[ChapterTable.lastReadAt] = now
                }
            }
        }

        if (isRead == true) {
            val mangaIds =
                transaction {
                    ChapterTable.select { condition }
                        .map { it[ChapterTable.manga].value }
                        .toSet()
                }
            Track.asyncTrackChapter(mangaIds)
        }
    }

    fun getChaptersMetaMaps(chapterIds: List<EntityID<Int>>): Map<EntityID<Int>, Map<String, String>> {
        return transaction {
            ChapterMetaTable.select { ChapterMetaTable.ref inList chapterIds }
                .groupBy { it[ChapterMetaTable.ref] }
                .mapValues { it.value.associate { it[ChapterMetaTable.key] to it[ChapterMetaTable.value] } }
                .withDefault { emptyMap<String, String>() }
        }
    }

    fun getChapterMetaMap(chapter: EntityID<Int>): Map<String, String> {
        return transaction {
            ChapterMetaTable.select { ChapterMetaTable.ref eq chapter }
                .associate { it[ChapterMetaTable.key] to it[ChapterMetaTable.value] }
        }
    }

    fun modifyChapterMeta(
        mangaId: Int,
        chapterIndex: Int,
        key: String,
        value: String,
    ) {
        transaction {
            val chapterId =
                ChapterTable.select { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                    .first()[ChapterTable.id].value
            modifyChapterMeta(chapterId, key, value)
        }
    }

    fun modifyChapterMeta(
        chapterId: Int,
        key: String,
        value: String,
    ) {
        transaction {
            val meta =
                ChapterMetaTable.select { (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }
                    .firstOrNull()

            if (meta == null) {
                ChapterMetaTable.insert {
                    it[ChapterMetaTable.key] = key
                    it[ChapterMetaTable.value] = value
                    it[ChapterMetaTable.ref] = chapterId
                }
            } else {
                ChapterMetaTable.update({ (ChapterMetaTable.ref eq chapterId) and (ChapterMetaTable.key eq key) }) {
                    it[ChapterMetaTable.value] = value
                }
            }
        }
    }

    fun deleteChapter(
        mangaId: Int,
        chapterIndex: Int,
    ) {
        transaction {
            val chapterId =
                ChapterTable.select { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                    .first()[ChapterTable.id].value

            ChapterDownloadHelper.delete(mangaId, chapterId)

            ChapterTable.update({ (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }) {
                it[isDownloaded] = false
            }
        }
    }

    private fun deleteChapters(
        input: MangaChapterBatchEditInput,
        mangaId: Int? = null,
    ) {
        if (input.chapterIds != null) {
            deleteChapters(input.chapterIds)
        } else if (input.chapterIndexes != null && mangaId != null) {
            transaction {
                val chapterIds =
                    ChapterTable.slice(ChapterTable.manga, ChapterTable.id)
                        .select { (ChapterTable.sourceOrder inList input.chapterIndexes) and (ChapterTable.manga eq mangaId) }
                        .map { row ->
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

    fun deleteChapters(chapterIds: List<Int>) {
        transaction {
            ChapterTable.slice(ChapterTable.manga, ChapterTable.id)
                .select { ChapterTable.id inList chapterIds }
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

    fun getRecentChapters(pageNum: Int): PaginatedList<MangaChapterDataClass> {
        return paginatedFrom(pageNum) {
            transaction {
                (ChapterTable innerJoin MangaTable)
                    .select { (MangaTable.inLibrary eq true) and (ChapterTable.fetchedAt greater MangaTable.inLibraryAt) }
                    .orderBy(ChapterTable.fetchedAt to SortOrder.DESC)
                    .map {
                        MangaChapterDataClass(
                            MangaTable.toDataClass(it),
                            ChapterTable.toDataClass(it),
                        )
                    }
            }
        }
    }
}
