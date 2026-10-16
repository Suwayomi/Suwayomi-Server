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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.chapter.ChapterSanitizer.sanitize
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.impl.util.updateChapterDownloadDir
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.PAGINATION_FACTOR
import suwayomi.tachidesk.manga.model.dataclass.PaginatedList
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.columns.validateMetaValueLength
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.manga.model.table.updateStrategyOrDefault
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import java.time.Instant
import java.util.TreeSet
import kotlin.math.max

private fun List<ChapterDataClass>.removeDuplicates(currentChapter: ChapterDataClass): List<ChapterDataClass> =
    groupBy { it.chapterNumber }
        .map { (_, chapters) ->
            chapters.find { it.id == currentChapter.id }
                ?: chapters.find { it.scanlator == currentChapter.scanlator }
                ?: chapters.first()
        }

/** The source returned no chapters for the manga (or the chapter list is still empty after a fetch). */
class NoChaptersFoundException(
    mangaId: Int,
) : Exception("No chapters found for manga $mangaId")

object Chapter {
    private val logger = KotlinLogging.logger { }

    /** get chapter list when showing a manga */
    suspend fun getChapterList(
        userId: Int,
        mangaId: Int,
        onlineFetch: Boolean = false,
    ): List<ChapterDataClass> =
        if (onlineFetch) {
            getSourceChapters(userId, mangaId)
        } else {
            val (chapters, chaptersLastFetchedAt) =
                transaction {
                    ChapterTable
                        .getWithUserData(userId)
                        .selectAll()
                        .where { ChapterTable.manga eq mangaId }
                        .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                        .map {
                            ChapterTable.toDataClass(it)
                        } to
                        (
                            MangaTable
                                .select(MangaTable.chaptersLastFetchedAt)
                                .where { MangaTable.id eq mangaId }
                                .firstOrNull()
                                ?.get(MangaTable.chaptersLastFetchedAt) ?: 0L
                        )
                }
            // Contract: an empty chapter list only triggers a source fetch while the manga
            // was never fetched (chaptersLastFetchedAt == 0). A source that legitimately
            // has zero chapters must not be hit on every request — callers force a
            // refetch via onlineFetch = true.
            chapters.ifEmpty {
                if (chaptersLastFetchedAt == 0L) {
                    getSourceChapters(userId, mangaId)
                } else {
                    emptyList()
                }
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

    private suspend fun getSourceChapters(
        userId: Int,
        mangaId: Int,
    ): List<ChapterDataClass> {
        Manga.updateMangaAndChapters(
            mangaId,
            updateManga = false,
            updateChapters = true,
        )

        return transaction {
            ChapterTable
                .getWithUserData(userId)
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                .map {
                    ChapterTable.toDataClass(it)
                }
        }
    }

    suspend fun updateChapterListDatabase(
        mangaEntry: ResultRow,
        chapters: List<SChapter>,
        source: Source,
    ): List<SChapter> {
        val currentLatestChapterNumber = Manga.getLatestChapter(mangaEntry[MangaTable.id].value)?.chapterNumber ?: 0f
        val numberOfCurrentChapters = getCountOfMangaChapters(mangaEntry[MangaTable.id].value)
        // it's possible that the source returns a list containing chapters with the same url
        // once such duplicated chapters have been added, they aren't being removed anymore as long as there is
        // a chapter with the same url in the fetched chapter list, even if the duplicated chapter itself
        // does not exist anymore on the source
        val uniqueChapters = chapters.distinctBy { it.url }

        if (uniqueChapters.isEmpty()) {
            throw NoChaptersFoundException(mangaEntry[MangaTable.id].value)
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
                update_strategy = updateStrategyOrDefault(mangaEntry[MangaTable.updateStrategy])
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

        // Phase A — a single snapshot transaction: the chapter rows and the per-user
        // state of the chapters being deleted used to be read in separate transactions,
        // which could observe inconsistent delete sets
        val plan =
            transaction {
                val chaptersInDb =
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.manga eq mangaEntry[MangaTable.id].value }
                        .map { ChapterTable.toDataClass(it) }
                        .toList()

                // url lookups: first match wins, same as List.find (duplicate urls in
                // the db are possible — see the comment on uniqueChapters above)
                val chaptersInDbByUrl =
                    chaptersInDb.distinctBy { it.url }.associateBy { it.url }
                val chaptersInDbById = chaptersInDb.associateBy { it.id }

                val chaptersToInsert = mutableListOf<ChapterDataClass>() // do not yet have an ID from the database
                val chaptersToUpdate = mutableListOf<ChapterDataClass>()

                uniqueChapters.reversed().forEachIndexed { index, fetchedChapter ->
                    val chapterEntry = chaptersInDbByUrl[fetchedChapter.url]

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
                val deletedDownloadedChapterByChapterNumber = mutableMapOf<Float, ChapterDataClass>()
                val deletedChapterNumberDateFetchMap = mutableMapOf<Float, Long>()
                val deletedUserStateByChapterNumber = mutableMapOf<Float, MutableMap<Int, DeletedChapterUserData>>()

                // clear any orphaned/duplicate chapters that are in the db but not in `chapterList`
                val chapterUrls = uniqueChapters.map { it.url }.toSet()

                val chaptersIdsToDelete =
                    chaptersInDb.mapNotNull { dbChapter ->
                        if (!chapterUrls.contains(dbChapter.url)) {
                            if (dbChapter.downloaded) deletedDownloadedChapterByChapterNumber[dbChapter.chapterNumber] = dbChapter
                            deletedChapterNumbers.add(dbChapter.chapterNumber)
                            deletedChapterNumberDateFetchMap[dbChapter.chapterNumber] = dbChapter.fetchedAt
                            dbChapter.id
                        } else {
                            null
                        }
                    }

                if (chaptersIdsToDelete.isNotEmpty()) {
                    val chapterNumberById = chaptersInDb.associate { it.id to it.chapterNumber }
                    ChapterUserTable
                        .selectAll()
                        .where { ChapterUserTable.chapter inList chaptersIdsToDelete }
                        .map {
                            Triple(
                                it[ChapterUserTable.user].value,
                                it[ChapterUserTable.chapter].value,
                                DeletedChapterUserData(
                                    isRead = it[ChapterUserTable.isRead],
                                    isBookmarked = it[ChapterUserTable.isBookmarked],
                                    lastPageRead = it[ChapterUserTable.lastPageRead],
                                    lastReadAt = it[ChapterUserTable.lastReadAt],
                                    version = it[ChapterUserTable.version],
                                    isDownloaded = it[ChapterUserTable.isDownloaded],
                                    isDownloadRequested = it[ChapterUserTable.isDownloadRequested],
                                ),
                            )
                        }.forEach { (userId, chapterId, userData) ->
                            val chapterNumber = chapterNumberById[chapterId] ?: return@forEach
                            val userStates = deletedUserStateByChapterNumber.getOrPut(chapterNumber) { mutableMapOf() }
                            userStates[userId] = userData
                        }
                }

                ChapterUpdatePlan(
                    chaptersInDbById = chaptersInDbById,
                    chaptersToInsert = chaptersToInsert,
                    chaptersToUpdate = chaptersToUpdate,
                    chaptersIdsToDelete = chaptersIdsToDelete,
                    deletedChapterNumbers = deletedChapterNumbers,
                    deletedDownloadedChapterByChapterNumber = deletedDownloadedChapterByChapterNumber,
                    deletedChapterNumberDateFetchMap = deletedChapterNumberDateFetchMap,
                    deletedUserStateByChapterNumber = deletedUserStateByChapterNumber,
                )
            }

        // Phase B — filesystem work runs outside any database transaction. Renames are
        // attempted against the snapshot above; if the write transaction below rolls
        // back afterwards, download-directory drift self-heals on the next refresh
        // (the chapter is treated as not-downloaded and can be downloaded again).
        val preservedInsertUrls = mutableSetOf<String>()
        plan.chaptersToInsert.forEach { chapter ->
            val deletedChapter =
                plan.deletedDownloadedChapterByChapterNumber[chapter.chapterNumber] ?: return@forEach

            // For a new (unrecognized) chapter, we have to handle the existing downloads as obsolete in case the scanlator changed because we can't assume that the pages are still the same
            val isSameScanlator = chapter.scanlator == deletedChapter.scanlator
            if (isSameScanlator && updateChapterDownloadDir(deletedChapter, chapter)) {
                preservedInsertUrls.add(chapter.url)
            }
        }

        val downloadInvalidatedChapterIds = mutableListOf<Int>()
        plan.chaptersToUpdate.forEach { chapter ->
            val currentChapter = plan.chaptersInDbById[chapter.id] ?: return@forEach
            if (!currentChapter.downloaded) {
                return@forEach
            }

            if (!updateChapterDownloadDir(currentChapter, chapter)) {
                downloadInvalidatedChapterIds.add(chapter.id)
            }
        }

        // new chapters after they have been added to the database for auto downloads
        val insertedChapterIds = mutableListOf<Int>()

        // Phase C — pure database writes; nothing below touches the filesystem
        suspendTransaction {
            // we got some clean up due
            if (plan.chaptersIdsToDelete.isNotEmpty()) {
                PageTable.deleteWhere { chapter inList plan.chaptersIdsToDelete }
                ChapterTable.deleteWhere { id inList plan.chaptersIdsToDelete }
            }

            if (plan.chaptersToInsert.isNotEmpty()) {
                val insertedChapters =
                    ChapterTable
                        .batchInsert(plan.chaptersToInsert) { chapter ->
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
                            // download preservation was decided by the rename in phase B
                            val isDownloadPreserved = chapter.url in preservedInsertUrls
                            this[ChapterTable.isDownloaded] = isDownloadPreserved
                            this[ChapterTable.pageCount] =
                                if (isDownloadPreserved) {
                                    plan.deletedDownloadedChapterByChapterNumber[chapter.chapterNumber]?.pageCount ?: -1
                                } else {
                                    -1
                                }

                            // is recognized chapter number
                            if (chapter.chapterNumber >= 0f && chapter.chapterNumber in plan.deletedChapterNumbers) {
                                // Try to use the fetch date of the original entry to not pollute 'Updates' tab
                                plan.deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                                    this[ChapterTable.fetchedAt] = it
                                }
                            }
                        }.map { ChapterTable.toDataClass(it) }

                insertedChapters.forEach { insertedChapterIds.add(it.id) }

                val preservedDownloadChapterIds =
                    insertedChapters
                        .filter { it.url in preservedInsertUrls }
                        .map { it.id }
                        .toSet()

                // migrate the per-user state of the deleted chapters to the re-inserted chapters,
                // matched by recognized chapter number, for all users
                val migratedUserStates =
                    insertedChapters
                        .filter { it.chapterNumber >= 0f && it.chapterNumber in plan.deletedChapterNumbers }
                        .flatMap { chapter ->
                            plan.deletedUserStateByChapterNumber[chapter.chapterNumber]
                                ?.map { (userId, userData) -> Triple(chapter.id, userId, userData) }
                                .orEmpty()
                        }

                if (migratedUserStates.isNotEmpty()) {
                    ChapterUserTable.batchInsert(migratedUserStates) { (chapterId, userId, userData) ->
                        this[ChapterUserTable.chapter] = EntityID(chapterId, ChapterTable)
                        this[ChapterUserTable.user] = EntityID(userId, UserAccountTable)
                        this[ChapterUserTable.isRead] = userData.isRead
                        this[ChapterUserTable.isBookmarked] = userData.isBookmarked
                        this[ChapterUserTable.lastPageRead] = userData.lastPageRead
                        this[ChapterUserTable.lastReadAt] = userData.lastReadAt
                        // migrate the per-user download state only if the shared download file was preserved,
                        // otherwise the download is gone for all users
                        val downloadPreserved = chapterId in preservedDownloadChapterIds
                        this[ChapterUserTable.isDownloadRequested] = downloadPreserved && userData.isDownloadRequested
                        this[ChapterUserTable.isDownloaded] = downloadPreserved && userData.isDownloaded

                        this[ChapterUserTable.version] = userData.version
                    }
                }
            }

            if (plan.chaptersToUpdate.isNotEmpty()) {
                BatchUpdateStatement(ChapterTable)
                    .apply {
                        plan.chaptersToUpdate.forEach {
                            addBatch(EntityID(it.id, ChapterTable))

                            val currentChapter = plan.chaptersInDbById[it.id]!!
                            val isDownloadInvalidated = it.id in downloadInvalidatedChapterIds

                            this[ChapterTable.name] = it.name
                            this[ChapterTable.date_upload] = it.uploadDate
                            this[ChapterTable.chapter_number] = it.chapterNumber
                            this[ChapterTable.scanlator] = it.scanlator
                            this[ChapterTable.sourceOrder] = it.index
                            this[ChapterTable.realUrl] = it.realUrl
                            this[ChapterTable.memo] = it.memo
                            this[ChapterTable.isDownloaded] = currentChapter.downloaded && !isDownloadInvalidated
                            this[ChapterTable.pageCount] = if (isDownloadInvalidated) -1 else currentChapter.pageCount
                        }
                    }.toExecutable()
                    .execute(this@suspendTransaction)

                // the shared download is gone, so clear the per-user download state as well
                if (downloadInvalidatedChapterIds.isNotEmpty()) {
                    ChapterUserTable.update({ ChapterUserTable.chapter inList downloadInvalidatedChapterIds }) {
                        it[ChapterUserTable.isDownloaded] = false
                        it[ChapterUserTable.isDownloadRequested] = false
                    }
                }
            }

            MangaTable.update({ MangaTable.id eq mangaEntry[MangaTable.id].value }) {
                it[chaptersLastFetchedAt] = Instant.now().epochSecond
            }
        }

        // Phase D — queue management runs after commit: a rollback must not drop
        // download requests for chapters that are still in the database
        if (plan.chaptersIdsToDelete.isNotEmpty()) {
            DownloadManager.dequeue(plan.chaptersIdsToDelete)
        }

        val inLibraryUserIds =
            transaction {
                MangaUserTable
                    .select(MangaUserTable.user)
                    .where { MangaUserTable.manga eq mangaEntry[MangaTable.id] and (MangaUserTable.inLibrary eq true) }
                    .map { it[MangaUserTable.user].value }
                    .toList()
            }
        if (inLibraryUserIds.isNotEmpty()) {
            // We have to query the inserted chapters to get the up-to-date data. I.e. "last_modified_at" is not returned by the insert statement, due to being set by a DB trigger
            val insertedChapters =
                transaction {
                    ChapterTable.selectAll().where { ChapterTable.id inList insertedChapterIds }.map(
                        ChapterTable::toDataClass,
                    )
                }
            inLibraryUserIds.forEach { userId ->
                downloadNewChapters(
                    userId,
                    mangaEntry[MangaTable.id].value,
                    currentLatestChapterNumber,
                    numberOfCurrentChapters,
                    insertedChapters,
                )
            }
        }

        return uniqueChapters
    }

    private fun downloadNewChapters(
        userId: Int,
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
                    "autoDownloadNewChaptersLimit= ${userConfig.autoDownloadNewChaptersLimit.value(userId)}, " +
                    "autoDownloadIgnoreReUploads= ${userConfig.autoDownloadIgnoreReUploads.value(userId)}" +
                    ")",
            )

        if (!userConfig.autoDownloadNewChapters.value(userId)) {
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

        if (!Manga.isInIncludedDownloadCategory(userId, log, mangaId)) {
            return
        }

        val unreadChapters = Manga.getUnreadChapters(userId, mangaId).subtract(newChapters.toSet())

        val skipDueToUnreadChapters = userConfig.excludeEntryWithUnreadChapters.value(userId) && unreadChapters.isNotEmpty()
        if (skipDueToUnreadChapters) {
            log.debug { "ignore due to unread chapters" }
            return
        }

        val chapterIdsToDownload = getNewChapterIdsToDownload(userId, newChapters, prevLatestChapterNumber)

        if (chapterIdsToDownload.isEmpty()) {
            log.debug { "no chapters available for download" }
            return
        }

        log.info { "download ${chapterIdsToDownload.size} new chapter(s)..." }

        DownloadManager.enqueue(userId, chapterIdsToDownload)
    }

    private fun getNewChapterIdsToDownload(
        userId: Int,
        newChapters: List<ChapterDataClass>,
        prevLatestChapterNumber: Float,
    ): List<Int> {
        val reUploadedChapters = newChapters.filter { it.chapterNumber < prevLatestChapterNumber }
        val actualNewChapters = newChapters.subtract(reUploadedChapters.toSet()).toList()
        val chaptersToConsiderForDownloadLimit =
            if (userConfig.autoDownloadIgnoreReUploads.value(userId)) {
                if (actualNewChapters.isNotEmpty()) actualNewChapters.removeDuplicates(actualNewChapters[0]) else emptyList()
            } else {
                newChapters.removeDuplicates(newChapters[0])
            }.sortedBy { it.index }

        val latestChapterToDownloadIndex =
            if (userConfig.autoDownloadNewChaptersLimit.value(userId) == 0) {
                chaptersToConsiderForDownloadLimit.size
            } else {
                userConfig.autoDownloadNewChaptersLimit.value(userId).coerceIn(0, chaptersToConsiderForDownloadLimit.size)
            }
        val limitedChaptersToDownload = chaptersToConsiderForDownloadLimit.subList(0, latestChapterToDownloadIndex)
        val limitedChapterNumbers = limitedChaptersToDownload.mapTo(HashSet()) { it.chapterNumber }
        val limitedChaptersToDownloadWithDuplicates =
            (
                limitedChaptersToDownload +
                    newChapters.filter { it.chapterNumber in limitedChapterNumbers }
            ).toSet()

        return limitedChaptersToDownloadWithDuplicates.map { it.id }
    }

    fun modifyChapter(
        userId: Int,
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
                        .where {
                            (ChapterTable.manga eq mangaId) and
                                (ChapterTable.sourceOrder eq chapterIndex)
                        }.first()

                val chapterIdValue = chapter[ChapterTable.id].value

                if (listOf(isRead, isBookmarked, lastPageRead).any { it != null }) {
                    ChapterUserTable.upsert(ChapterUserTable.user, ChapterUserTable.chapter) {
                        it[ChapterUserTable.chapter] = chapterIdValue
                        it[ChapterUserTable.user] = userId
                        isRead?.also { isRead ->
                            it[ChapterUserTable.isRead] = isRead
                        }
                        isBookmarked?.also { isBookmarked ->
                            it[ChapterUserTable.isBookmarked] = isBookmarked
                        }
                        lastPageRead?.also { lastPageRead ->
                            it[ChapterUserTable.lastPageRead] = lastPageRead
                            it[ChapterUserTable.lastReadAt] = Instant.now().epochSecond
                        }
                    }
                }

                markPrevRead?.let { markPrevRead ->
                    val chapters =
                        ChapterTable
                            .select(ChapterTable.id)
                            .where { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder less chapterIndex) }
                            .map { it[ChapterTable.id].value }

                    ChapterUserTable.batchUpsert(chapters, ChapterUserTable.user, ChapterUserTable.chapter) { chapterId ->
                        this[ChapterUserTable.user] = userId
                        this[ChapterUserTable.chapter] = chapterId
                        this[ChapterUserTable.isRead] = markPrevRead
                    }
                }
                chapterIdValue
            }

        if (isRead == true || markPrevRead == true) {
            Track.asyncTrackChapter(userId, setOf(mangaId))
        }

        return chapterId
    }

    /** mutation plan computed from the phase-A snapshot in [updateChapterListDatabase] */
    private class ChapterUpdatePlan(
        val chaptersInDbById: Map<Int, ChapterDataClass>,
        val chaptersToInsert: List<ChapterDataClass>,
        val chaptersToUpdate: List<ChapterDataClass>,
        val chaptersIdsToDelete: List<Int>,
        val deletedChapterNumbers: Set<Float>,
        val deletedDownloadedChapterByChapterNumber: Map<Float, ChapterDataClass>,
        val deletedChapterNumberDateFetchMap: Map<Float, Long>,
        val deletedUserStateByChapterNumber: Map<Float, Map<Int, DeletedChapterUserData>>,
    )

    /** per-user state of a deleted chapter, migrated to re-inserted chapters with the same recognized chapter number */
    private data class DeletedChapterUserData(
        val isRead: Boolean,
        val isBookmarked: Boolean,
        val lastPageRead: Int,
        val lastReadAt: Long,
        val version: Long,
        val isDownloaded: Boolean,
        val isDownloadRequested: Boolean,
    )

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

    private const val MAX_CHAPTER_BATCH_SIZE = 1000

    suspend fun modifyChapters(
        userId: Int,
        input: MangaChapterBatchEditInput,
        mangaId: Int? = null,
    ) {
        // unbounded id lists explode the generated `IN (...)` clause and the transaction
        // runtime; reject oversized batches as client errors (400)
        require((input.chapterIds?.size ?: 0) <= MAX_CHAPTER_BATCH_SIZE) {
            "'chapterIds' supports at most $MAX_CHAPTER_BATCH_SIZE entries"
        }
        require((input.chapterIndexes?.size ?: 0) <= MAX_CHAPTER_BATCH_SIZE) {
            "'chapterIndexes' supports at most $MAX_CHAPTER_BATCH_SIZE entries"
        }

        // Make sure change is defined
        if (input.change == null) return
        val (isRead, isBookmarked, lastPageRead, delete) = input.change

        // Handle deleting separately
        if (delete == true) {
            deleteChapters(userId, input, mangaId)
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
            val chapters =
                ChapterTable
                    .select(ChapterTable.id)
                    .where { condition }
                    .map { it[ChapterTable.id].value }

            ChapterUserTable.batchUpsert(chapters, ChapterUserTable.chapter, ChapterUserTable.user) { chapter ->
                this[ChapterUserTable.user] = userId
                this[ChapterUserTable.chapter] = chapter
                isRead?.also {
                    this[ChapterUserTable.isRead] = it
                }
                isBookmarked?.also {
                    this[ChapterUserTable.isBookmarked] = it
                }
                lastPageRead?.also {
                    this[ChapterUserTable.lastPageRead] = it
                    this[ChapterUserTable.lastReadAt] = now
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
            Track.asyncTrackChapter(userId, mangaIds)
        }
    }

    fun getChaptersMetaMaps(
        userId: Int,
        chapterIds: List<Int>,
    ): Map<Int, Map<String, String>> =
        transaction {
            ChapterMetaTable
                .selectAll()
                .where { ChapterMetaTable.ref inList chapterIds and (ChapterMetaTable.user eq userId) }
                .groupBy { it[ChapterMetaTable.ref].value }
                .mapValues { it.value.associate { it[ChapterMetaTable.key] to it[ChapterMetaTable.value] } }
                .withDefault { emptyMap() }
        }

    fun getChapterMetaMap(
        userId: Int,
        chapter: Int,
    ): Map<String, String> =
        transaction {
            ChapterMetaTable
                .selectAll()
                .where { ChapterMetaTable.user eq userId and (ChapterMetaTable.ref eq chapter) }
                .associate { it[ChapterMetaTable.key] to it[ChapterMetaTable.value] }
        }

    fun modifyChapterMeta(
        userId: Int,
        mangaId: Int,
        chapterIndex: Int,
        key: String,
        value: String,
    ) {
        transaction {
            val chapterId =
                ChapterTable
                    .selectAll()
                    .where {
                        (ChapterTable.manga eq mangaId) and
                            (ChapterTable.sourceOrder eq chapterIndex)
                    }.first()[ChapterTable.id]
                    .value
            modifyChapterMeta(userId, chapterId, key, value)
        }
    }

    fun modifyChapterMeta(
        userId: Int,
        chapterId: Int,
        key: String,
        value: String,
    ) {
        modifyChaptersMetas(userId, mapOf(chapterId to mapOf(key to value)))
    }

    fun modifyChaptersMetas(
        userId: Int,
        metaByChapterId: Map<Int, Map<String, String>>,
    ) {
        // CHAPTERMETA.value is varchar(4096): reject oversized input before the transaction
        metaByChapterId.values.forEach { it.values.forEach(::validateMetaValueLength) }
        transaction {
            val chapterIds = metaByChapterId.keys
            val metaKeys = metaByChapterId.flatMap { it.value.keys }

            val dbMetaByChapterId =
                ChapterMetaTable
                    .selectAll()
                    .where {
                        (ChapterMetaTable.ref inList chapterIds) and (ChapterMetaTable.key inList metaKeys) and
                            (ChapterMetaTable.user eq userId)
                    }.groupBy { it[ChapterMetaTable.ref].value }

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
                    this[ChapterMetaTable.user] = userId
                }
            }
        }
    }

    suspend fun deleteChapter(
        userId: Int,
        mangaId: Int,
        chapterIndex: Int,
    ) {
        // only the chapterId lookup runs inside a transaction — the delete itself must
        // stay fully post-commit (filesystem removal + dequeue) like the batch path.
        // Wrapping it would nest the post-commit steps inside an open transaction and
        // re-create the FS-in-transaction drift the four-phase flow removed.
        val chapterId =
            suspendTransaction {
                ChapterTable
                    .selectAll()
                    .where { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                    .first()[ChapterTable.id]
                    .value
            }

        deleteDownloadedChapters(userId, listOf(chapterId))
    }

    private suspend fun deleteChapters(
        userId: Int,
        input: MangaChapterBatchEditInput,
        mangaId: Int? = null,
    ) {
        val chapterIds =
            input.chapterIds
                ?: if (input.chapterIndexes != null && mangaId != null) {
                    transaction {
                        ChapterTable
                            .select(ChapterTable.id)
                            .where {
                                (ChapterTable.sourceOrder inList input.chapterIndexes) and
                                    (ChapterTable.manga eq mangaId)
                            }.map { it[ChapterTable.id].value }
                    }
                } else {
                    return
                }

        deleteDownloadedChapters(userId, chapterIds)
    }

    /**
     * Clears the download request of [userId] for the given chapters and removes the
     * shared download only if no user has requested it anymore.
     */
    suspend fun deleteDownloadedChapters(
        userId: Int,
        chapterIds: List<Int>,
    ) {
        if (chapterIds.isEmpty()) return

        // the transaction only decides WHICH downloads go away — filesystem deletion
        // runs after commit, so a rollback cannot orphan already-deleted files
        val deletedChapters =
            suspendTransaction {
                // Clear the caller's download request and status
                ChapterUserTable.update({ (ChapterUserTable.user eq userId) and (ChapterUserTable.chapter inList chapterIds) }) {
                    it[ChapterUserTable.isDownloadRequested] = false
                    it[ChapterUserTable.isDownloaded] = false
                }

                // Only remove the shared download if no user has requested it anymore
                val notRequestedByAnyUserQuery =
                    notExists(
                        ChapterUserTable.select(ChapterUserTable.id).where {
                            (ChapterUserTable.chapter eq ChapterTable.id) and (ChapterUserTable.isDownloadRequested eq true)
                        },
                    )
                val chaptersToDelete =
                    ChapterTable
                        .select(ChapterTable.manga, ChapterTable.id)
                        .where { (ChapterTable.id inList chapterIds) and notRequestedByAnyUserQuery }
                        .map { row -> Pair(row[ChapterTable.manga].value, row[ChapterTable.id].value) }

                if (chaptersToDelete.isNotEmpty()) {
                    ChapterTable.update({ ChapterTable.id inList chaptersToDelete.map { it.second } }) {
                        it[ChapterTable.isDownloaded] = false
                    }
                }

                chaptersToDelete
            }

        if (deletedChapters.isEmpty()) return

        // post-commit filesystem cleanup — on failure the DB state stays correct and the
        // leftover files get reclaimed on the next refresh
        deletedChapters.forEach { (mangaId, chapterId) ->
            ChapterDownloadHelper.delete(mangaId, chapterId)
        }

        // Stop any in-progress shared download that no user wants anymore
        DownloadManager.dequeue(deletedChapters.map { it.second })
    }

    fun getRecentChapters(
        userId: Int,
        pageNum: Int,
    ): PaginatedList<MangaChapterDataClass> =
        transaction {
            // DB-level pagination: fetch one extra row as the hasNextPage sentinel instead
            // of materializing the whole recent-chapters result in memory. Keeps the same
            // page size and hasNextPage semantics as paginatedFrom, including the negative
            // pageNum clamp and the Long offset arithmetic.
            val offset = pageNum.coerceAtLeast(0).toLong() * PAGINATION_FACTOR
            val rows =
                (ChapterTable innerJoin MangaTable.getWithUserData(userId))
                    .selectAll()
                    .where { (MangaUserTable.inLibrary eq true) and (ChapterTable.fetchedAt greater MangaUserTable.inLibraryAt) }
                    .orderBy(ChapterTable.fetchedAt to SortOrder.DESC)
                    .limit(PAGINATION_FACTOR + 1)
                    .offset(offset)
                    .map {
                        MangaChapterDataClass(
                            MangaTable.toDataClass(it),
                            ChapterTable.toDataClass(it),
                        )
                    }
            PaginatedList(rows.take(PAGINATION_FACTOR), rows.size > PAGINATION_FACTOR)
        }

    fun updateChapterProgress(
        userId: Int,
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
            userId,
            mangaId,
            chapterIndex,
            isRead = isRead,
            lastPageRead = pageNo,
            isBookmarked = null,
            markPrevRead = null,
        )

        return chapterData.id
    }
}
