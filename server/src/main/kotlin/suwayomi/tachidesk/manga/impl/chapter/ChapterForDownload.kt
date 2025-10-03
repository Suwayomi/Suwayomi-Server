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
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import kotlin.time.Duration.Companion.minutes

suspend fun getChapterDownloadReady(
    chapterId: Int? = null,
    chapterIndex: Int? = null,
    mangaId: Int? = null,
): ChapterDataClass {
    val chapter = ChapterForDownload(chapterId, chapterIndex, mangaId)
    return chapter.asDownloadReady()
}

suspend fun getChapterDownloadReadyById(chapterId: Int): ChapterDataClass = getChapterDownloadReady(chapterId = chapterId)

suspend fun getChapterDownloadReadyByIndex(
    chapterIndex: Int,
    mangaId: Int,
): ChapterDataClass = getChapterDownloadReady(chapterIndex = chapterIndex, mangaId = mangaId)

private val mutexByChapterId: Cache<Int, Mutex> =
    Cache
        .Builder<Int, Mutex>()
        .expireAfterAccess(10.minutes)
        .build()

private class ChapterForDownload(
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

        val downloadPageCount =
            try {
                ChapterDownloadHelper.getImageCount(mangaId, chapterId)
            } catch (_: Exception) {
                0
            }
        val isMarkedAsDownloaded = chapterEntry[ChapterTable.isDownloaded]
        val dbPageCount = chapterEntry[ChapterTable.pageCount]
        val doesDownloadExist = downloadPageCount != 0
        val doPageCountsMatch = dbPageCount == downloadPageCount

        log.debug { "isMarkedAsDownloaded= $isMarkedAsDownloaded, dbPageCount= $dbPageCount, downloadPageCount= $downloadPageCount" }

        return if (!doesDownloadExist) {
            log.debug { "reset download status and fetch page list" }
            updateDownloadStatusAndPageList(false)
        } else {
            transaction {
                var needsUpdate = false

                if (!isMarkedAsDownloaded) {
                    log.debug { "mark as downloaded" }
                    ChapterTable.update({ ChapterTable.id eq chapterId }) {
                        it[isDownloaded] = true
                    }
                    needsUpdate = true
                }

                if (!doPageCountsMatch) {
                    log.debug { "use page count of downloaded chapter" }
                    ChapterTable.update({ ChapterTable.id eq chapterId }) {
                        it[pageCount] = downloadPageCount
                        it[lastPageRead] = chapterEntry[ChapterTable.lastPageRead].coerceAtMost(downloadPageCount - 1).coerceAtLeast(0)
                    }
                    needsUpdate = true
                }

                // Return updated chapter data
                val updatedRow =
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.id eq chapterId }
                        .first()

                if (needsUpdate) {
                    chapterEntry = updatedRow
                }

                ChapterTable.toDataClass(updatedRow)
            }
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

    private suspend fun updateDownloadStatusAndPageList(downloaded: Boolean): ChapterDataClass {
        val mutex = mutexByChapterId.get(chapterId) { Mutex() }
        return mutex.withLock {
            val pageList = fetchPageList()

            transaction {
                // Update download status
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[isDownloaded] = downloaded
                }

                // Clear existing pages and insert new ones
                PageTable.deleteWhere { PageTable.chapter eq chapterId }
                PageTable.batchInsert(pageList) { page ->
                    this[PageTable.index] = page.index
                    this[PageTable.url] = page.url
                    this[PageTable.imageUrl] = page.imageUrl
                    this[PageTable.chapter] = chapterId
                }

                // Update page count
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[pageCount] = pageList.size
                    it[lastPageRead] = chapterEntry[ChapterTable.lastPageRead].coerceAtMost(pageList.size - 1).coerceAtLeast(0)
                }

                // Get updated chapter data
                val updatedRow =
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.id eq chapterId }
                        .first()

                chapterEntry = updatedRow
                ChapterTable.toDataClass(updatedRow)
            }
        }
    }

    private suspend fun fetchPageList(): List<Page> {
        val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
        val source = getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])

        return source
            .getPageList(
                SChapter.create().apply {
                    url = chapterEntry[ChapterTable.url]
                    name = chapterEntry[ChapterTable.name]
                    scanlator = chapterEntry[ChapterTable.scanlator]
                    chapter_number = chapterEntry[ChapterTable.chapter_number]
                    date_upload = chapterEntry[ChapterTable.date_upload]
                },
            ).mapIndexed { index, page -> Page(index, page.url, page.imageUrl, page.uri) }
    }
}
