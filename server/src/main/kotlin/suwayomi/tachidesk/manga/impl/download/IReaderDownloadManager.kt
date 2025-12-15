package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.IReaderNovel
import suwayomi.tachidesk.manga.impl.IReaderSource
import suwayomi.tachidesk.manga.model.table.IReaderChapterTable
import suwayomi.tachidesk.manga.model.table.IReaderNovelTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

/**
 * Download state for IReader chapters
 */
enum class IReaderDownloadState {
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
}

/**
 * Download queue item for IReader chapters
 */
data class IReaderDownloadItem(
    val chapterId: Int,
    val novelId: Int,
    val sourceId: Long,
    val chapterUrl: String,
    val chapterName: String,
    val novelTitle: String,
    var state: IReaderDownloadState = IReaderDownloadState.QUEUED,
    var progress: Float = 0f,
    var error: String? = null,
)

/**
 * Download status update
 */
data class IReaderDownloadUpdate(
    val item: IReaderDownloadItem,
    val type: IReaderDownloadUpdateType,
)

enum class IReaderDownloadUpdateType {
    QUEUED,
    STARTED,
    PROGRESS,
    COMPLETED,
    ERROR,
    REMOVED,
}

/**
 * Manager for downloading IReader (novel) chapters.
 * Stores chapter content as text files for offline reading.
 */
object IReaderDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val applicationDirs: ApplicationDirs by injectLazy()

    private val downloadQueue = CopyOnWriteArrayList<IReaderDownloadItem>()
    private val downloadingItems = ConcurrentHashMap<Int, IReaderDownloadItem>()
    private val mutex = Mutex()

    private var isRunning = false
    private var currentDownload: IReaderDownloadItem? = null

    private val _updates = MutableSharedFlow<IReaderDownloadUpdate>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates = _updates.asSharedFlow()

    /**
     * Get the download directory for a novel
     */
    fun getNovelDownloadDir(novelId: Int): File {
        val dir = File(applicationDirs.downloadsRoot, "novels/$novelId")
        dir.mkdirs()
        return dir
    }

    /**
     * Get the download file for a chapter
     */
    fun getChapterFile(
        novelId: Int,
        chapterId: Int,
    ): File {
        return File(getNovelDownloadDir(novelId), "$chapterId.txt")
    }

    /**
     * Check if a chapter is downloaded
     */
    fun isChapterDownloaded(
        novelId: Int,
        chapterId: Int,
    ): Boolean {
        return getChapterFile(novelId, chapterId).exists()
    }

    /**
     * Get downloaded chapter content
     */
    fun getDownloadedContent(
        novelId: Int,
        chapterId: Int,
    ): String? {
        val file = getChapterFile(novelId, chapterId)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Enqueue chapters for download
     */
    fun enqueue(chapterIds: List<Int>) {
        scope.launch {
            mutex.withLock {
                val items =
                    transaction {
                        chapterIds.mapNotNull { chapterId ->
                            val chapter =
                                IReaderChapterTable.selectAll()
                                    .where { IReaderChapterTable.id eq chapterId }
                                    .firstOrNull() ?: return@mapNotNull null

                            val novelId = chapter[IReaderChapterTable.novel].value
                            val novel =
                                IReaderNovelTable.selectAll()
                                    .where { IReaderNovelTable.id eq novelId }
                                    .firstOrNull() ?: return@mapNotNull null

                            // Skip if already downloaded
                            if (isChapterDownloaded(novelId, chapterId)) {
                                return@mapNotNull null
                            }

                            // Skip if already in queue
                            if (downloadQueue.any { it.chapterId == chapterId }) {
                                return@mapNotNull null
                            }

                            IReaderDownloadItem(
                                chapterId = chapterId,
                                novelId = novelId,
                                sourceId = novel[IReaderNovelTable.sourceReference],
                                chapterUrl = chapter[IReaderChapterTable.url],
                                chapterName = chapter[IReaderChapterTable.name],
                                novelTitle = novel[IReaderNovelTable.title],
                            )
                        }
                    }

                items.forEach { item ->
                    downloadQueue.add(item)
                    _updates.emit(IReaderDownloadUpdate(item, IReaderDownloadUpdateType.QUEUED))
                }

                if (!isRunning && downloadQueue.isNotEmpty()) {
                    startDownloading()
                }
            }
        }
    }

    /**
     * Remove chapters from download queue
     */
    fun dequeue(chapterIds: List<Int>) {
        scope.launch {
            mutex.withLock {
                chapterIds.forEach { chapterId ->
                    val item = downloadQueue.find { it.chapterId == chapterId }
                    if (item != null) {
                        downloadQueue.remove(item)
                        _updates.emit(IReaderDownloadUpdate(item, IReaderDownloadUpdateType.REMOVED))
                    }
                }
            }
        }
    }

    /**
     * Delete downloaded chapter
     */
    fun deleteDownload(
        novelId: Int,
        chapterId: Int,
    ) {
        val file = getChapterFile(novelId, chapterId)
        if (file.exists()) {
            file.delete()
        }

        // Update database
        transaction {
            IReaderChapterTable.update({ IReaderChapterTable.id eq chapterId }) {
                it[isDownloaded] = false
            }
        }
    }

    /**
     * Delete all downloads for a novel
     */
    fun deleteNovelDownloads(novelId: Int) {
        val dir = getNovelDownloadDir(novelId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }

        // Update database
        transaction {
            IReaderChapterTable.update({ IReaderChapterTable.novel eq novelId }) {
                it[isDownloaded] = false
            }
        }
    }

    /**
     * Get current download status
     */
    fun getStatus(): IReaderDownloadStatus {
        return IReaderDownloadStatus(
            isRunning = isRunning,
            queue = downloadQueue.toList(),
            currentDownload = currentDownload,
        )
    }

    /**
     * Start the download process
     */
    private fun startDownloading() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            while (downloadQueue.isNotEmpty()) {
                val item =
                    mutex.withLock {
                        downloadQueue.firstOrNull()?.also {
                            currentDownload = it
                            it.state = IReaderDownloadState.DOWNLOADING
                        }
                    } ?: break

                try {
                    _updates.emit(IReaderDownloadUpdate(item, IReaderDownloadUpdateType.STARTED))
                    downloadChapter(item)

                    item.state = IReaderDownloadState.DOWNLOADED
                    item.progress = 1f
                    _updates.emit(IReaderDownloadUpdate(item, IReaderDownloadUpdateType.COMPLETED))

                    // Update database
                    transaction {
                        IReaderChapterTable.update({ IReaderChapterTable.id eq item.chapterId }) {
                            it[isDownloaded] = true
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to download chapter ${item.chapterName}" }
                    item.state = IReaderDownloadState.ERROR
                    item.error = e.message
                    _updates.emit(IReaderDownloadUpdate(item, IReaderDownloadUpdateType.ERROR))
                }

                mutex.withLock {
                    downloadQueue.remove(item)
                    currentDownload = null
                }
            }

            isRunning = false
        }
    }

    /**
     * Download a single chapter
     */
    private suspend fun downloadChapter(item: IReaderDownloadItem) {
        // Fetch chapter content
        val pages = IReaderNovel.getChapterContent(item.sourceId, item.chapterUrl)

        // Combine all text pages into a single file
        val content =
            pages
                .filterIsInstance<ireader.core.source.model.Text>()
                .joinToString("\n\n") { it.text }

        if (content.isBlank()) {
            throw Exception("No content found for chapter")
        }

        // Save to file
        val file = getChapterFile(item.novelId, item.chapterId)
        file.parentFile?.mkdirs()
        file.writeText(content)

        logger.debug { "Downloaded chapter: ${item.chapterName} (${content.length} chars)" }
    }

    /**
     * Stop all downloads
     */
    fun stop() {
        isRunning = false
        currentDownload = null
    }

    /**
     * Clear the download queue
     */
    fun clearQueue() {
        scope.launch {
            mutex.withLock {
                downloadQueue.forEach { item ->
                    _updates.emit(IReaderDownloadUpdate(item, IReaderDownloadUpdateType.REMOVED))
                }
                downloadQueue.clear()
            }
        }
    }
}

/**
 * Current download status
 */
data class IReaderDownloadStatus(
    val isRunning: Boolean,
    val queue: List<IReaderDownloadItem>,
    val currentDownload: IReaderDownloadItem?,
)
