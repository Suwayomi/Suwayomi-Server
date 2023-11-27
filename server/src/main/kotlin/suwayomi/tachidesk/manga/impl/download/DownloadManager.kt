package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Downloading
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Error
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Queued
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.model.Status
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@OptIn(FlowPreview::class)
object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clients = ConcurrentHashMap<String, WsContext>()
    private val downloadQueue = CopyOnWriteArrayList<DownloadChapter>()
    private val downloaders = ConcurrentHashMap<String, Downloader>()

    private const val DOWNLOAD_QUEUE_KEY = "downloadQueueKey"
    private val sharedPreferences =
        Injekt.get<Application>().getSharedPreferences(DownloadManager::class.jvmName, Context.MODE_PRIVATE)

    private fun loadDownloadQueue(): List<Int> {
        return sharedPreferences.getStringSet(DOWNLOAD_QUEUE_KEY, emptySet())?.mapNotNull { it.toInt() }.orEmpty()
    }

    private fun saveDownloadQueue() {
        sharedPreferences.edit().putStringSet(DOWNLOAD_QUEUE_KEY, downloadQueue.map { it.chapter.id.toString() }.toSet())
            .apply()
    }

    private fun triggerSaveDownloadQueue() {
        scope.launch { saveQueueFlow.emit(Unit) }
    }

    fun restoreAndResumeDownloads() {
        scope.launch {
            logger.debug { "restoreAndResumeDownloads: Restore download queue..." }
            enqueue(EnqueueInput(loadDownloadQueue()))

            if (downloadQueue.size > 0) {
                logger.info { "restoreAndResumeDownloads: Restored download queue, starting downloads..." }
            }
        }
    }

    fun addClient(ctx: WsContext) {
        clients[ctx.sessionId] = ctx
    }

    fun removeClient(ctx: WsContext) {
        clients.remove(ctx.sessionId)
    }

    fun notifyClient(ctx: WsContext) {
        ctx.send(
            getStatus(),
        )
    }

    fun handleRequest(ctx: WsMessageContext) {
        when (ctx.message()) {
            "STATUS" -> notifyClient(ctx)
            else ->
                ctx.send(
                    """
                        |Invalid command.
                        |Supported commands are:
                        |    - STATUS
                        |       sends the current download status
                        |
                    """.trimMargin(),
                )
        }
    }

    private val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val statusFlow = MutableSharedFlow<Unit>()
    val status =
        statusFlow.onStart { emit(Unit) }
            .map { getStatus() }

    init {
        scope.launch {
            notifyFlow.sample(1.seconds).collect {
                statusFlow.emit(Unit)
                sendStatusToAllClients()
            }
        }
    }

    private val saveQueueFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        saveQueueFlow.onEach { saveDownloadQueue() }.launchIn(scope)
    }

    private fun sendStatusToAllClients() {
        val status = getStatus()
        clients.forEach {
            it.value.send(status)
        }
    }

    private fun notifyAllClients(immediate: Boolean = false) {
        scope.launch {
            notifyFlow.emit(Unit)

            if (immediate) {
                statusFlow.emit(Unit)
            }
        }
        if (immediate) {
            sendStatusToAllClients()
        }
    }

    private fun getStatus(): DownloadStatus {
        return DownloadStatus(
            if (downloadQueue.none { it.state == Downloading }) {
                Status.Stopped
            } else {
                Status.Started
            },
            downloadQueue.toList(),
        )
    }

    private val downloaderWatch = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        serverConfig.subscribeTo(serverConfig.maxSourcesInParallel, { maxSourcesInParallel ->
            val runningDownloaders = downloaders.values.filter { it.isActive }
            var downloadersToStop = runningDownloaders.size - maxSourcesInParallel

            logger.debug { "Max sources in parallel changed to $maxSourcesInParallel (running downloaders ${runningDownloaders.size})" }

            if (downloadersToStop > 0) {
                runningDownloaders.takeWhile {
                    it.stop()
                    --downloadersToStop > 0
                }
            } else {
                downloaderWatch.emit(Unit)
            }
        })

        scope.launch {
            downloaderWatch.sample(1.seconds).collect {
                val runningDownloaders = downloaders.values.filter { it.isActive }
                val availableDownloads = downloadQueue.filter { it.state != Error }

                logger.info {
                    "Running: ${runningDownloaders.size}, " +
                        "Queued: ${availableDownloads.size}, " +
                        "Failed: ${downloadQueue.size - availableDownloads.size}"
                }

                if (runningDownloaders.size < serverConfig.maxSourcesInParallel.value) {
                    availableDownloads.asSequence()
                        .map { it.manga.sourceId }
                        .distinct()
                        .minus(
                            runningDownloaders.map { it.sourceId }.toSet(),
                        )
                        .take(serverConfig.maxSourcesInParallel.value - runningDownloaders.size)
                        .map { getDownloader(it) }
                        .forEach {
                            it.start()
                        }
                    notifyAllClients()
                }
            }
        }
    }

    private fun refreshDownloaders() {
        scope.launch {
            downloaderWatch.emit(Unit)
        }
    }

    private fun getDownloader(sourceId: String) =
        downloaders.getOrPut(sourceId) {
            Downloader(
                scope = scope,
                sourceId = sourceId,
                downloadQueue = downloadQueue,
                notifier = ::notifyAllClients,
                onComplete = ::refreshDownloaders,
                onDownloadFinished = ::triggerSaveDownloadQueue,
            )
        }

    fun enqueueWithChapterIndex(
        mangaId: Int,
        chapterIndex: Int,
    ) {
        val chapter =
            transaction {
                ChapterTable
                    .slice(ChapterTable.id)
                    .select { ChapterTable.manga.eq(mangaId) and ChapterTable.sourceOrder.eq(chapterIndex) }
                    .first()
            }
        enqueue(EnqueueInput(chapterIds = listOf(chapter[ChapterTable.id].value)))
    }

    @Serializable
    // Input might have additional formats in the future, such as "All for mangaID" or "Unread for categoryID"
    // Having this input format is just future-proofing
    data class EnqueueInput(
        val chapterIds: List<Int>?,
    )

    fun enqueue(input: EnqueueInput) {
        if (input.chapterIds.isNullOrEmpty()) return

        val chapters =
            transaction {
                (ChapterTable innerJoin MangaTable)
                    .select { ChapterTable.id inList input.chapterIds }
                    .orderBy(ChapterTable.manga)
                    .orderBy(ChapterTable.sourceOrder)
                    .toList()
            }

        val mangas =
            transaction {
                chapters.distinctBy { chapter -> chapter[MangaTable.id] }
                    .map { MangaTable.toDataClass(it) }
                    .associateBy { it.id }
            }

        val inputPairs =
            transaction {
                chapters.map {
                    Pair(
                        // this should be safe because mangas is created above from chapters
                        mangas[it[ChapterTable.manga].value]!!,
                        ChapterTable.toDataClass(it),
                    )
                }
            }

        addMultipleToQueue(inputPairs)
    }

    /**
     * Tries to add multiple inputs to queue
     * If any of inputs was actually added to queue, starts the queue
     */
    private fun addMultipleToQueue(inputs: List<Pair<MangaDataClass, ChapterDataClass>>) {
        val addedChapters = inputs.mapNotNull { addToQueue(it.first, it.second) }
        if (addedChapters.isNotEmpty()) {
            start()
            notifyAllClients(true)
        }
        scope.launch {
            downloaderWatch.emit(Unit)
        }
    }

    /**
     * Tries to add chapter to queue.
     * If chapter is added, returns the created DownloadChapter, otherwise returns null
     */
    private fun addToQueue(
        manga: MangaDataClass,
        chapter: ChapterDataClass,
    ): DownloadChapter? {
        val downloadChapter = downloadQueue.firstOrNull { it.mangaId == manga.id && it.chapterIndex == chapter.index }

        val addToQueue = downloadChapter == null
        if (addToQueue) {
            val newDownloadChapter =
                DownloadChapter(
                    chapter.index,
                    manga.id,
                    chapter,
                    manga,
                )
            downloadQueue.add(newDownloadChapter)
            triggerSaveDownloadQueue()
            logger.debug { "Added chapter ${chapter.id} to download queue ($newDownloadChapter)" }
            return newDownloadChapter
        }

        val retryDownload = downloadChapter?.state == Error
        if (retryDownload) {
            logger.debug { "Chapter ${chapter.id} download failed, retry download ($downloadChapter)" }

            downloadChapter?.state = Queued
            downloadChapter?.progress = 0f

            return downloadChapter
        }

        logger.debug { "Chapter ${chapter.id} already present in queue ($downloadChapter)" }
        return null
    }

    fun dequeue(input: EnqueueInput) {
        if (input.chapterIds.isNullOrEmpty()) return
        dequeue(downloadQueue.filter { it.chapter.id in input.chapterIds }.toSet())
    }

    fun dequeue(
        chapterIndex: Int,
        mangaId: Int,
    ) {
        dequeue(downloadQueue.filter { it.mangaId == mangaId && it.chapterIndex == chapterIndex }.toSet())
    }

    fun dequeue(
        mangaIds: List<Int>,
        chaptersToIgnore: List<Int> = emptyList(),
    ) {
        dequeue(downloadQueue.filter { it.mangaId in mangaIds && it.chapter.id !in chaptersToIgnore }.toSet())
    }

    private fun dequeue(chapterDownloads: Set<DownloadChapter>) {
        logger.debug { "dequeue ${chapterDownloads.size} chapters [${chapterDownloads.joinToString(separator = ", ") { "$it" }}]" }

        downloadQueue.removeAll(chapterDownloads)
        triggerSaveDownloadQueue()

        notifyAllClients()
    }

    fun reorder(
        chapterIndex: Int,
        mangaId: Int,
        to: Int,
    ) {
        val download =
            downloadQueue.find { it.mangaId == mangaId && it.chapterIndex == chapterIndex }
                ?: return

        reorder(download, to)
    }

    fun reorder(
        chapterId: Int,
        to: Int,
    ) {
        val download =
            downloadQueue.find { it.chapter.id == chapterId }
                ?: return

        reorder(download, to)
    }

    private fun reorder(
        download: DownloadChapter,
        to: Int,
    ) {
        require(to >= 0) { "'to' must be over or equal to 0" }

        logger.debug { "reorder download $download from ${downloadQueue.indexOf(download)} to $to" }

        downloadQueue -= download
        downloadQueue.add(to, download)
        triggerSaveDownloadQueue()
    }

    fun start() {
        logger.debug { "start" }

        scope.launch {
            downloaderWatch.emit(Unit)
        }
    }

    suspend fun stop() {
        logger.debug { "stop" }

        coroutineScope {
            downloaders.map { (_, downloader) ->
                async {
                    downloader.stop()
                }
            }.awaitAll()
        }
        notifyAllClients()
    }

    suspend fun clear() {
        logger.debug { "clear" }

        stop()
        downloadQueue.clear()
        triggerSaveDownloadQueue()
        notifyAllClients()
    }
}

enum class DownloaderState(val state: Int) {
    Stopped(0),
    Running(1),
    Paused(2),
}
