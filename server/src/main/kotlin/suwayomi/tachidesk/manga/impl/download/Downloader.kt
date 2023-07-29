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

private val logger = KotlinLogging.logger {}

class Downloader(
    private val scope: CoroutineScope,
    val sourceId: String,
    private val downloadQueue: CopyOnWriteArrayList<DownloadChapter>,
    private val notifier: (immediate: Boolean) -> Unit,
    private val onComplete: () -> Unit
) {
    private var job: Job? = null
    class StopDownloadException : Exception("Cancelled download")
    class PauseDownloadException : Exception("Pause download")

    private suspend fun step(download: DownloadChapter?, immediate: Boolean) {
        notifier(immediate)
        currentCoroutineContext().ensureActive()
        if (download != null && download != downloadQueue.firstOrNull { it.manga.sourceId == sourceId && it.state != Error }) {
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
                        onComplete()
                    }
                }
            }
        }

        notifier(false)
    }

    suspend fun stop() {
        job?.cancelAndJoin()
    }

    private suspend fun run() {
        while (downloadQueue.isNotEmpty() && currentCoroutineContext().isActive) {
            val download = downloadQueue.firstOrNull {
                it.manga.sourceId == sourceId &&
                    (it.state == Queued || (it.state == Error && it.tries < 3)) // 3 re-tries per download
            } ?: break

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
            } catch (e: CancellationException) {
                logger.debug("Downloader was stopped")
                downloadQueue.filter { it.manga.sourceId == sourceId && it.state == Downloading }.forEach { it.state = Queued }
            } catch (e: PauseDownloadException) {
                download.state = Queued
            } catch (e: Exception) {
                logger.warn("Downloader faced an exception", e)
                download.tries++
                download.state = Error
            } finally {
                notifier(false)
            }
        }
    }
}
