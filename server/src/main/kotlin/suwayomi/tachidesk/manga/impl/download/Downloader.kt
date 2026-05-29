package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReadyById
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Downloading
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Error
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Finished
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Queued
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdate
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.ERROR
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.FINISHED
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.PAUSED
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.PROGRESS
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.STOPPED
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.util.concurrent.CopyOnWriteArrayList

class Downloader(
    private val scope: CoroutineScope,
    val id: String,
    private val downloadQueue: CopyOnWriteArrayList<DownloadQueueItem>,
    private val notifier: (immediate: Boolean, download: DownloadUpdate?) -> Unit,
    private val onComplete: () -> Unit,
    private val onDownloadFinished: () -> Unit,
) {
    companion object {
        private const val MAX_RETRIES = 3
    }

    private val logger = KotlinLogging.logger("${Downloader::class.java.name} source_chapter($id)")

    private var job: Job? = null

    class StopDownloadException : Exception("Cancelled download")

    class PauseDownloadException : Exception("Pause download")

    class EmptyChapterException : Exception("Chapter does not have any pages to download")

    private suspend fun step(
        downloadUpdate: DownloadUpdate?,
        immediate: Boolean,
    ) {
        val download = downloadUpdate?.downloadQueueItem
        notifier(immediate, downloadUpdate)
        currentCoroutineContext().ensureActive()
        if (download != null) {
            val firstValid = downloadQueue.find { it.id == id && it.state != Error }
            if (download != firstValid) {
                if (download in downloadQueue) {
                    throw PauseDownloadException()
                } else {
                    throw StopDownloadException()
                }
            }
        }
    }

    val isActive
        get() = job?.isActive == true

    fun start() {
        if (!isActive) {
            job =
                scope
                    .launch {
                        run()
                    }.also { job ->
                        job.invokeOnCompletion {
                            if (it !is CancellationException) {
                                logger.debug { "completed" }
                                onComplete()
                            }
                        }
                    }
            logger.debug { "started" }
            notifier(false, null)
        }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        logger.debug { "stopped" }
    }

    private fun finishDownload(download: DownloadQueueItem) {
        notifier(true, DownloadUpdate(FINISHED, download))
        downloadQueue -= download
        onDownloadFinished()
        logger.debug { "Finished download for chapter ${download.chapterId}" }
    }

    private suspend fun run() {
        while (downloadQueue.isNotEmpty() && currentCoroutineContext().isActive) {
            val download =
                downloadQueue
                    .find {
                        it.id == id && (it.state == Queued || it.state == Finished || (it.state == Error && it.tries < MAX_RETRIES))
                    } ?: break

            logger.debug { "Starting download for chapter ${download.chapterId}" }

            // handle cases were the downloader was stopped before the finished download could be removed from the queue
            // otherwise, it will create an endless loop, due to never removing the finished chapter and thinking that the
            // current download chapter was moved down in the queue
            if (download.state == Finished) {
                finishDownload(download)
                continue
            }

            try {
                download.state = Downloading
                step(DownloadUpdate(PROGRESS, download), true)

                val chapter = getChapterDownloadReadyById(download.chapterId)

                if (chapter.pageCount <= 0) {
                    throw EmptyChapterException()
                }

                download.pageCount = chapter.pageCount

                ChapterDownloadHelper.download(download.mangaId, download.chapterId, download, scope) { downloadChapter, _ ->
                    step(downloadChapter?.let { DownloadUpdate(PROGRESS, downloadChapter) }, false)
                }
                download.state = Finished
                withContext(Dispatchers.IO) {
                    transaction {
                        ChapterTable.update({ (ChapterTable.id eq download.chapterId) }) {
                            it[isDownloaded] = true
                        }
                    }
                }
                finishDownload(download)
            } catch (e: CancellationException) {
                logger.debug { "Downloader was stopped for ${download.chapterId}" }
                if (download.state == Downloading) download.state = Queued
                notifier(false, DownloadUpdate(STOPPED, download))
            } catch (e: PauseDownloadException) {
                logger.debug { "Paused download for ${download.chapterId}" }
                download.state = Queued
                notifier(false, DownloadUpdate(PAUSED, download))
            } catch (e: EmptyChapterException) {
                logger.warn(e) { "Failed download for ${download.chapterId} - empty chapter" }
                download.tries = MAX_RETRIES
                download.state = Error
                notifier(false, DownloadUpdate(ERROR, download))
            } catch (e: Exception) {
                logger.warn(e) { "Failed download for ${download.chapterId}" }
                download.tries++
                download.state = Queued
                if (download.tries >= MAX_RETRIES) {
                    download.state = Error
                    notifier(false, DownloadUpdate(ERROR, download))
                }
            }
        }
    }
}
