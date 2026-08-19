package suwayomi.tachidesk.manga.impl.chapter

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.toDataClass
import kotlin.time.Duration.Companion.minutes

/**
 * Updates chapter download status and page count in the database if they differ from the file system.
 */
fun updateChapterPersistence(
    chapterId: Int,
    isMarkedAsDownloaded: Boolean,
    dbPageCount: Int,
    downloadPageCount: Int,
    logger: KLogger,
): Boolean {
    if (isMarkedAsDownloaded && dbPageCount == downloadPageCount) {
        return false
    }

    return transaction {
        var needsUpdate = false
        if (!isMarkedAsDownloaded) {
            logger.debug { "mark as downloaded" }
            ChapterTable.update({ ChapterTable.id eq chapterId }) {
                it[isDownloaded] = true
            }
            needsUpdate = true
        }

        if (dbPageCount != downloadPageCount) {
            logger.debug { "use page count of downloaded chapter" }
            ChapterTable.update({ ChapterTable.id eq chapterId }) {
                it[pageCount] = downloadPageCount
            }
            clampLastPageReads(chapterId, downloadPageCount)
            needsUpdate = true
        }
        needsUpdate
    }
}

private fun clampLastPageReads(chapterId: Int, pageCount: Int) {
    ChapterUserTable
        .selectAll()
        .where {
            ChapterUserTable.chapter eq chapterId and (ChapterUserTable.lastPageRead greaterEq pageCount)
        }.forEach { row ->
            ChapterUserTable.update({ ChapterUserTable.id eq row[ChapterUserTable.id] }) {
                it[ChapterUserTable.lastPageRead] = row[ChapterUserTable.lastPageRead].coerceAtMost(pageCount - 1).coerceAtLeast(0)
            }
        }
}

suspend fun refreshChapterPageList(
    mangaId: Int,
    chapterId: Int,
    existingChapterEntry: ResultRow? = null,
): Int {
    val mutex = mutexByChapterId.get(chapterId) { Mutex() }
    return mutex.withLock {
        val chapterEntry = existingChapterEntry ?: transaction { ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first() }
        val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
        val source = getSourceOrStub(mangaEntry[MangaTable.sourceReference])

        val pageList =
            source
                .getPageList(
                    SChapter.create().apply {
                        url = chapterEntry[ChapterTable.url]
                        name = chapterEntry[ChapterTable.name]
                        scanlator = chapterEntry[ChapterTable.scanlator]
                        chapter_number = chapterEntry[ChapterTable.chapter_number]
                        date_upload = chapterEntry[ChapterTable.date_upload]
                        memo = chapterEntry[ChapterTable.memo]
                    },
                ).mapIndexed { index, page -> Page(index, page.url, page.imageUrl, page.uri) }

        transaction {
            ChapterTable.update({ ChapterTable.id eq chapterId }) {
                it[isDownloaded] = false
            }

            PageTable.deleteWhere { PageTable.chapter eq chapterId }
            PageTable.batchInsert(pageList) { page ->
                this[PageTable.index] = page.index
                this[PageTable.url] = page.url
                this[PageTable.imageUrl] = page.imageUrl
                this[PageTable.chapter] = chapterId
            }

            ChapterTable.update({ ChapterTable.id eq chapterId }) {
                it[pageCount] = pageList.size
            }
            clampLastPageReads(chapterId, pageList.size)
        }
        pageList.size
    }
}

suspend fun getChapterDownloadReady(
    userId: Int,
    chapterId: Int? = null,
    chapterIndex: Int? = null,
    mangaId: Int? = null,
): ChapterDataClass {
    val chapter = ChapterForDownload(userId, chapterId, chapterIndex, mangaId)
    return chapter.asDownloadReady()
}

suspend fun getChapterDownloadReadyById(
    userId: Int,
    chapterId: Int,
): ChapterDataClass = getChapterDownloadReady(userId = userId, chapterId = chapterId)

suspend fun getChapterDownloadReadyByIndex(
    userId: Int,
    chapterIndex: Int,
    mangaId: Int,
): ChapterDataClass = getChapterDownloadReady(userId = userId, chapterIndex = chapterIndex, mangaId = mangaId)

private val mutexByChapterId: Cache<Int, Mutex> =
    Cache
        .Builder<Int, Mutex>()
        .expireAfterAccess(10.minutes)
        .build()

private class ChapterForDownload(
    private val userId: Int,
    optChapterId: Int? = null,
    optChapterIndex: Int? = null,
    optMangaId: Int? = null,
) {
    var chapterEntry: ResultRow
    val chapterId: Int
    val chapterIndex: Int
    val mangaId: Int

    val logger: KLogger

    suspend fun asDownloadReady(): ChapterDataClass {
        val log = KotlinLogging.logger("${logger.name}::asDownloadReady")

        val downloadPageCount = runCatching { ChapterDownloadHelper.getImageCount(mangaId, chapterId) }.getOrDefault(0)
        val isMarkedAsDownloaded = chapterEntry[ChapterTable.isDownloaded]
        val dbPageCount = chapterEntry[ChapterTable.pageCount]
        val doesDownloadExist = downloadPageCount != 0

        log.debug { "isMarkedAsDownloaded= $isMarkedAsDownloaded, dbPageCount= $dbPageCount, downloadPageCount= $downloadPageCount" }

        return if (!doesDownloadExist) {
            log.debug { "reset download status and fetch page list" }
            refreshChapterPageList(mangaId, chapterId, chapterEntry)
            chapterEntry = freshChapterEntry(optChapterId = chapterId)
            ChapterTable.toDataClass(chapterEntry)
        } else {
            if (updateChapterPersistence(
                    chapterId,
                    isMarkedAsDownloaded,
                    dbPageCount,
                    downloadPageCount,
                    log,
                )
            ) {
                chapterEntry = freshChapterEntry(optChapterId = chapterId)
            }
            ChapterTable.toDataClass(chapterEntry)
        }
    }

    init {
        chapterEntry = freshChapterEntry(optChapterId, optChapterIndex, optMangaId)
        chapterId = chapterEntry[ChapterTable.id].value
        chapterIndex = chapterEntry[ChapterTable.sourceOrder]
        mangaId = chapterEntry[ChapterTable.manga].value

        logger =
            KotlinLogging.logger(
                "${ChapterForDownload::class.java.name}(mangaId= $mangaId, chapterId= $chapterId, chapterIndex= $chapterIndex)",
            )
    }

    private fun freshChapterEntry(
        optChapterId: Int? = null,
        optChapterIndex: Int? = null,
        optMangaId: Int? = null,
    ) = transaction {
        ChapterTable
            .getWithUserData(userId)
            .selectAll()
            .where {
                if (optChapterId != null) {
                    ChapterTable.id eq optChapterId
                } else if (optChapterIndex != null && optMangaId != null) {
                    (ChapterTable.sourceOrder eq optChapterIndex) and (ChapterTable.manga eq optMangaId)
                } else {
                    throw Exception("'optChapterId' or 'optChapterIndex' and 'optMangaId' have to be passed")
                }
            }.first()
    }
}
