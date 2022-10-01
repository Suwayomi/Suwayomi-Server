package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Chapter.writeMetaData
import suwayomi.tachidesk.manga.impl.Page.getPageImage
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReady
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Downloading
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Error
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Finished
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Queued
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

class Downloader(private val downloadQueue: CopyOnWriteArrayList<DownloadChapter>, val notifier: () -> Unit) : Thread() {
    var shouldStop: Boolean = false

    class DownloadShouldStopException : Exception()

    fun step() {
        notifier()
        synchronized(shouldStop) {
            if (shouldStop) throw DownloadShouldStopException()
        }
    }

    override fun run() {
        do {
            val download = downloadQueue.firstOrNull {
                it.state == Queued ||
                    (it.state == Error && it.tries < 3) // 3 re-tries per download
            } ?: break

            try {
                download.state = Downloading
                step()

                download.chapter = runBlocking { getChapterDownloadReady(download.chapterIndex, download.mangaId) }
                step()

                val pageCount = download.chapter.pageCount
                for (pageNum in 0 until pageCount) {
                    runBlocking { getPageImage(download.mangaId, download.chapterIndex, pageNum) }
                    // TODO: retry on error with 2,4,8 seconds of wait
                    // TODO: download multiple pages at once, possible solution: rx observer's strategy is used in Tachiyomi
                    // TODO: fine grained download percentage
                    download.progress = (pageNum + 1).toFloat() / pageCount
                    step()
                }
                download.state = Finished
                transaction {
                    ChapterTable.update({ (ChapterTable.manga eq download.mangaId) and (ChapterTable.sourceOrder eq download.chapterIndex) }) {
                        it[isDownloaded] = true
                    }
                }

                writeMetaData(download.mangaId, download.chapterIndex)
                step()

                downloadQueue.removeIf { it.mangaId == download.mangaId && it.chapterIndex == download.chapterIndex }
                step()
            } catch (e: DownloadShouldStopException) {
                logger.debug("Downloader was stopped")
                downloadQueue.filter { it.state == Downloading }.forEach { it.state = Queued }
            } catch (e: Exception) {
                logger.debug("Downloader faced an exception")
                downloadQueue.filter { it.state == Downloading }.forEach { it.state = Error; it.tries++ }
                e.printStackTrace()
            } finally {
                notifier()
            }
        } while (!shouldStop)
    }
}
