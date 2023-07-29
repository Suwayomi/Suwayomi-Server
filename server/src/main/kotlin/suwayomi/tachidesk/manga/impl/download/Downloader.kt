package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReady
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Downloading
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Error
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Finished
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Queued
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.util.concurrent.CopyOnWriteArrayList

class Downloader(
    private val scope: CoroutineScope,
    val sourceId: String,
    private val downloadQueue: CopyOnWriteArrayList<DownloadChapter>,
    private val notifier: (immediate: Boolean) -> Unit,
    private val onComplete: () -> Unit
) {
    private val logger = KotlinLogging.logger("${Downloader::class.java.name} source($sourceId)")

    private var job: Job? = null
    private val availableSourceDownloads
        get() = downloadQueue.filter { it.manga.sourceId == sourceId }

    class StopDownloadException : Exception("Cancelled download")
    class PauseDownloadException : Exception("Pause download")

    private suspend fun step(download: DownloadChapter?, immediate: Boolean) {
        notifier(immediate)
        currentCoroutineContext().ensureActive()
        if (download != null && download != availableSourceDownloads.firstOrNull { it.state != Error }) {
            if (download in downloadQueue) {
                throw PauseDownloadException()
            } else {
                throw StopDownloadException()
            }
        }
    }

    val isActive
        get() = job?.isActive == true

    fun start() {
        if (!isActive) {
            job = scope.launch {
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
        }

        notifier(false)
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        logger.debug { "stopped" }
    }

    private suspend fun run() {
        while (downloadQueue.isNotEmpty() && currentCoroutineContext().isActive) {
            val download = availableSourceDownloads.firstOrNull {
                (it.state == Queued || (it.state == Error && it.tries < 3)) // 3 re-tries per download
            } ?: break

            val logContext = "${logger.name} - downloadChapter(${download.manga.title} (${download.mangaId}) - ${download.chapter.name} (${download.chapter.id}))"
            val downloadLogger = KotlinLogging.logger(logContext)

            downloadLogger.debug { "start" }

            try {
                download.state = Downloading
                step(download, true)

                download.chapter = getChapterDownloadReady(download.chapterIndex, download.mangaId)
                step(download, false)

                ChapterDownloadHelper.download(download.mangaId, download.chapter.id, download, scope, this::step)
                download.state = Finished
                transaction {
                    ChapterTable.update({ (ChapterTable.manga eq download.mangaId) and (ChapterTable.sourceOrder eq download.chapterIndex) }) {
                        it[isDownloaded] = true
                    }
                }
                step(download, true)

                downloadQueue.removeIf { it.mangaId == download.mangaId && it.chapterIndex == download.chapterIndex }
                step(null, false)
                downloadLogger.debug { "finished" }
            } catch (e: CancellationException) {
                logger.debug("Downloader was stopped")
                availableSourceDownloads.filter { it.state == Downloading }.forEach { it.state = Queued }
            } catch (e: PauseDownloadException) {
                downloadLogger.debug { "paused" }
                download.state = Queued
            } catch (e: Exception) {
                downloadLogger.warn("failed due to", e)
                download.tries++
                download.state = Error
            } finally {
                notifier(false)
            }
        }
    }
}
