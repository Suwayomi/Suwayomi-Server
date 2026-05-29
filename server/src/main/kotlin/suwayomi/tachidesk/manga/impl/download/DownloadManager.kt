package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Error
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Queued
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdate
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdates
import suwayomi.tachidesk.manga.impl.download.model.OldDownloadStatus
import suwayomi.tachidesk.manga.impl.download.model.Status
import suwayomi.tachidesk.manga.impl.util.storage.StorageScanner
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.reflect.jvm.jvmName
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@OptIn(FlowPreview::class)
object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clients = ConcurrentHashMap<String, WsContext>()
    private val downloadQueue = CopyOnWriteArrayList<DownloadQueueItem>()
    private val downloadUpdates = CopyOnWriteArraySet<DownloadUpdate>()
    private val downloaderList = ConcurrentHashMap<String, Downloader>()
    private val storageScanner = StorageScanner()
    private val applicationDirs: ApplicationDirs by injectLazy()

    private const val DOWNLOAD_QUEUE_KEY = "downloadQueueKey"
    private val sharedPreferences =
        Injekt.get<Application>().getSharedPreferences(DownloadManager::class.jvmName, Context.MODE_PRIVATE)

    private fun loadDownloadQueue(): List<Int> =
        sharedPreferences
            .getStringSet(DOWNLOAD_QUEUE_KEY, emptySet())
            ?.mapNotNull {
                it.toIntOrNull()
            }.orEmpty()

    private fun saveDownloadQueue() {
        sharedPreferences
            .edit()
            .putStringSet(DOWNLOAD_QUEUE_KEY, downloadQueue.map { it.chapterId.toString() }.toSet())
            .apply()
    }

    private fun triggerSaveDownloadQueue() {
        scope.launch { saveQueueFlow.emit(Unit) }
    }

    private fun onDownloadFinished() {
        triggerSaveDownloadQueue()
    }

    private fun handleDownloadUpdate(
        immediate: Boolean,
        download: DownloadUpdate? = null,
    ) {
        val brandNewImmediate = download?.type == DownloadUpdateType.FINISHED || download?.type == DownloadUpdateType.ERROR
        notifyAllClients(brandNewImmediate, listOfNotNull(download))

        if (brandNewImmediate && downloadQueue.isEmpty()) {
            logger.info { "Cola de descargas vacía por completo. Invalidando caché de almacenamiento..." }
            storageScanner.invalidateCache(applicationDirs.downloadsRoot)
        }
    }

    fun restoreAndResumeDownloads() {
        scope.launch {
            logger.debug { "restoreAndResumeDownloads: Restore download queue..." }
            enqueue(EnqueueInput(loadDownloadQueue()))

            if (downloadQueue.isNotEmpty()) {
                logger.info { "restoreAndResumeDownloads: Restored download queue, starting downloads..." }
            }
        }
    }

    fun addClient(ctx: WsContext) {
        clients[ctx.sessionId()] = ctx
    }

    fun removeClient(ctx: WsContext) {
        clients.remove(ctx.sessionId())
    }

    fun notifyClient(ctx: WsContext) {
        ctx.send(
            getStatus(),
        )
    }

    fun handleRequest(ctx: WsMessageContext) {
        when (ctx.message()) {
            "STATUS" -> {
                notifyClient(ctx)
            }

            else -> {
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
    }

    private val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val saveQueueFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Deprecated("Replaced with updatesFlow", replaceWith = ReplaceWith("updatesFlow"))
    private val statusFlow = MutableSharedFlow<DownloadStatus>()

    @Deprecated("Replaced with updates", replaceWith = ReplaceWith("updates"))
    val status = statusFlow.onStart { emit(getStatus()) }

    private val updatesFlow = MutableSharedFlow<DownloadUpdates>()
    val updates = updatesFlow.onStart { emit(getDownloadUpdates(addInitial = true)) }

    init {
        scope.launch {
            notifyFlow.sample(1.seconds).collect {
                notifyAllClients(immediate = true, gqlEmit = true)
            }
        }

        saveQueueFlow.sample(2.seconds).onEach { saveDownloadQueue() }.launchIn(scope)

        serverConfig.subscribeTo(serverConfig.maxDownloadsInParallel, { maxDownloadsInParallel ->
            val runningDownloaders = downloaderList.values.filter { it.isActive }
            val queueMap = downloadQueue.associateBy { it.id } // Indexación O(1)

            val runningBySource =
                runningDownloaders.groupBy { downloader ->
                    queueMap[downloader.id]?.sourceId ?: 0L
                }

            runningBySource.forEach { (_, downloadersOfSource) ->
                var toStop = downloadersOfSource.size - maxDownloadsInParallel
                if (toStop > 0) {
                    downloadersOfSource.takeWhile {
                        it.stop()
                        --toStop > 0
                    }
                }
            }
            downloaderWatch.emit(Unit)
        })

        // Suscriptor para cambios en tiempo real del número máximo de fuentes en paralelo
        serverConfig.subscribeTo(serverConfig.maxSourcesInParallel, { maxSourcesInParallel ->
            val runningDownloaders = downloaderList.values.filter { it.isActive }
            val queueMap = downloadQueue.associateBy { it.id } // Indexación O(1)

            val activeSources = runningDownloaders.mapNotNull { queueMap[it.id]?.sourceId }.distinct()

            if (activeSources.size > maxSourcesInParallel) {
                val sourcesToStop = activeSources.drop(maxSourcesInParallel).toSet()
                runningDownloaders
                    .filter { downloader ->
                        queueMap[downloader.id]?.sourceId in sourcesToStop
                    }.forEach { it.stop() }
            }
            downloaderWatch.emit(Unit)
        })

        scope.launch {
            downloaderWatch.sample(1.seconds).collect {
                val runningDownloaders = downloaderList.values.filter { it.isActive }
                val availableDownloads = downloadQueue.filter { it.state == Queued }

                logger.info {
                    "Running globally: ${runningDownloaders.size}, " +
                        "Queued: ${availableDownloads.size}, " +
                        "Failed: ${downloadQueue.size - availableDownloads.size}"
                }

                if (availableDownloads.isEmpty()) return@collect

                val queueMap = downloadQueue.associateBy { it.id }

                // 1. Mapeamos cuántas descargas activas tiene cada origen (sourceId) actualmente
                val activeCountsBySource =
                    runningDownloaders
                        .groupBy { queueMap[it.id]?.sourceId ?: 0L }
                        .mapValues { it.value.size }
                        .toMutableMap()

                // 2. Extraemos el conjunto de IDs de fuentes que ya están descargando algo en este momento
                val activeSources = activeCountsBySource.filterValues { it > 0 }.keys.toMutableSet()
                val runningIds = runningDownloaders.map { it.id }.toSet()

                // Límites dinámicos desde la configuración
                val limitDownloadsPerSource = serverConfig.maxDownloadsInParallel.value
                val limitSourcesInParallel = serverConfig.maxSourcesInParallel.value

                // 3. Procesamos secuencialmente los elementos en cola aplicando el doble filtro
                availableDownloads
                    .asSequence()
                    .map { it.id }
                    .distinct()
                    .minus(runningIds)
                    .mapNotNull { id -> queueMap[id] }
                    .filter { item ->
                        val currentSourceCount = activeCountsBySource.getOrDefault(item.sourceId, 0)

                        // REGLA 1: Comprobar el límite de descargas concurrentes de este origen específico
                        if (currentSourceCount >= limitDownloadsPerSource) return@filter false

                        // REGLA 2: Comprobar el límite global de fuentes activas en paralelo
                        if (!activeSources.contains(item.sourceId) && activeSources.size >= limitSourcesInParallel) return@filter false

                        // Si supera ambos filtros, registramos la ranura temporalmente para este ciclo
                        activeCountsBySource[item.sourceId] = currentSourceCount + 1
                        activeSources.add(item.sourceId)
                        true
                    }.map { getDownloader(it.id) }
                    .forEach {
                        it.start()
                    }

                notifyAllClients()
            }
        }
    }

    private fun notifyAllClients(
        immediate: Boolean = false,
        downloads: List<DownloadUpdate> = emptyList(),
        gqlEmit: Boolean = false,
    ) {
        val incomingChapterIds = downloads.map { it.downloadQueueItem.chapterId }.toSet()
        val outdatedUpdates =
            downloadUpdates.filter { it.downloadQueueItem.chapterId in incomingChapterIds }
        downloadUpdates.removeAll(outdatedUpdates.toSet())
        downloadUpdates.addAll(downloads)

        // There is a problem where too many immediate updates can cause the client to lag out (e.g., in case it has to
        // update the queue in the cache based on the updates).
        // This happens in case e.g., a source is broken and all its downloads error out basically immediately.
        // With each errored out download, a new one starts, which causes an immediate notification to the clients.
        // While the immediate notification functionality is no longer needed for the latest graphql download subscription,
        // it is still required for the deprecated version as well as the rest api subscription.
        if (gqlEmit) {
            val updates = getDownloadUpdates()

            downloadUpdates.clear()

            scope.launch {
                updatesFlow.emit(updates)
            }
        }

        if (immediate) {
            val status = getStatus()
            scope.launch {
                statusFlow.emit(status)
                if (clients.isNotEmpty()) {
                    val status = getOldStatus(status)
                    clients.forEach {
                        it.value.send(status)
                    }
                }
            }

            return
        }

        scope.launch {
            notifyFlow.emit(Unit)
        }
    }

    fun getStatus(): DownloadStatus =
        DownloadStatus(
            if (downloaderList.values.any { it.isActive }) {
                Status.Started
            } else {
                Status.Stopped
            },
            downloadQueue.toList(),
            directoryStats = storageScanner.getDirectoryStats(applicationDirs.downloadsRoot),
        )

    fun getOldStatus(status: DownloadStatus): OldDownloadStatus =
        OldDownloadStatus(
            status.status,
            run {
                val items = status.queue
                val mangaIds = items.map { it.mangaId }.toSet()
                val chapterIds = items.map { it.chapterId }.toSet()
                transaction {
                    val mangas =
                        MangaTable
                            .selectAll()
                            .where { MangaTable.id inList mangaIds }
                            .associateBy({ it[MangaTable.id].value }, { MangaTable.toDataClass(it) })
                    val chapters =
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.id inList chapterIds }
                            .associateBy({ it[ChapterTable.id].value }, { ChapterTable.toDataClass(it) })
                    items.mapNotNull {
                        DownloadChapter(
                            it.chapterIndex,
                            it.mangaId,
                            chapters[it.chapterId] ?: return@mapNotNull null,
                            mangas[it.mangaId] ?: return@mapNotNull null,
                            it.position,
                            it.state,
                            it.progress,
                            it.tries,
                        )
                    }
                }
            },
        )

    private fun getDownloadUpdates(addInitial: Boolean = false): DownloadUpdates =
        DownloadUpdates(
            if (downloaderList.values.any { it.isActive }) {
                Status.Started
            } else {
                Status.Stopped
            },
            downloadUpdates.toList(),
            if (addInitial) downloadQueue.toList() else null,
        )

    private val downloaderWatch = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun refreshDownloaders() {
        scope.launch {
            downloaderWatch.emit(Unit)
        }
    }

    private fun getDownloader(id: String) =
        downloaderList.getOrPut(id) {
            Downloader(
                scope = scope,
                id = id,
                downloadQueue = downloadQueue,
                notifier = ::handleDownloadUpdate,
                onComplete = ::refreshDownloaders,
                onDownloadFinished = ::onDownloadFinished,
            )
        }

    fun enqueueWithChapterIndex(
        mangaId: Int,
        chapterIndex: Int,
    ) {
        val chapter =
            transaction {
                ChapterTable
                    .select(ChapterTable.id)
                    .where { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
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

        val inputPairs =
            transaction {
                val chapters =
                    (ChapterTable innerJoin MangaTable)
                        .selectAll()
                        .where { ChapterTable.id inList input.chapterIds }
                        .orderBy(ChapterTable.manga)
                        .orderBy(ChapterTable.sourceOrder)
                        .toList()

                val mangasMap =
                    chapters
                        .distinctBy { it[MangaTable.id] }
                        .associate { it[MangaTable.id].value to MangaTable.toDataClass(it) }

                chapters.map {
                    mangasMap[it[ChapterTable.manga].value]!! to ChapterTable.toDataClass(it)
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
            notifyAllClients(false, addedChapters.map { DownloadUpdate(DownloadUpdateType.QUEUED, it) })
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
    ): DownloadQueueItem? {
        val downloadChapter = downloadQueue.firstOrNull { it.chapterId == chapter.id }

        val addToQueue = downloadChapter == null
        if (addToQueue) {
            val newDownloadChapter =
                DownloadQueueItem(
                    chapter.id,
                    chapter.index,
                    manga.id,
                    manga.sourceId.toLong(),
                    downloadQueue.size,
                    0,
                )
            downloadQueue.add(newDownloadChapter)
            triggerSaveDownloadQueue()
            logger.debug { "Added chapter ${chapter.id} to download queue ($newDownloadChapter)" }
            return newDownloadChapter
        }

        val retryDownload = downloadChapter.state == Error
        if (retryDownload) {
            logger.debug { "Chapter ${chapter.id} download failed, retry download ($downloadChapter)" }

            downloadChapter.state = Queued
            downloadChapter.progress = 0f

            return downloadChapter
        }

        logger.debug { "Chapter ${chapter.id} already present in queue ($downloadChapter)" }
        return null
    }

    fun dequeue(input: EnqueueInput) {
        if (input.chapterIds.isNullOrEmpty()) return
        dequeue(downloadQueue.filter { it.chapterId in input.chapterIds }.toSet())
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
        dequeue(downloadQueue.filter { it.mangaId in mangaIds && it.chapterId !in chaptersToIgnore }.toSet())
    }

    private fun dequeue(chapterDownloads: Set<DownloadQueueItem>) {
        logger.debug { "dequeue ${chapterDownloads.size} chapters [${chapterDownloads.joinToString(separator = ", ") { "$it" }}]" }

        downloadQueue.removeAll(chapterDownloads)
        triggerSaveDownloadQueue()

        notifyAllClients(false, chapterDownloads.toList().map { DownloadUpdate(DownloadUpdateType.DEQUEUED, it) })
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
            downloadQueue.find { it.chapterId == chapterId }
                ?: return

        reorder(download, to)
    }

    private fun reorder(
        download: DownloadQueueItem,
        to: Int,
    ) {
        require(to >= 0) { "'to' must be over or equal to 0" }

        logger.debug { "reorder download $download from ${downloadQueue.indexOf(download)} to $to" }

        downloadQueue -= download
        downloadQueue.add(to, download)
        download.position = to
        notifyAllClients(false, listOf(DownloadUpdate(DownloadUpdateType.POSITION, download)))
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
            downloaderList
                .map { (_, downloader) ->
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
        val removedDownloads = downloadQueue.toList().map { DownloadUpdate(DownloadUpdateType.DEQUEUED, it) }
        downloadQueue.clear()
        triggerSaveDownloadQueue()
        notifyAllClients(false, removedDownloads)
    }
}
