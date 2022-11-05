package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Downloading
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

object DownloadManager {
    private val clients = ConcurrentHashMap<String, WsContext>()
    private val downloadQueue = CopyOnWriteArrayList<DownloadChapter>()
    private var downloader: Downloader? = null

    fun addClient(ctx: WsContext) {
        clients[ctx.sessionId] = ctx
    }

    fun removeClient(ctx: WsContext) {
        clients.remove(ctx.sessionId)
    }

    fun notifyClient(ctx: WsContext) {
        ctx.send(
            getStatus()
        )
    }

    fun handleRequest(ctx: WsMessageContext) {
        when (ctx.message()) {
            "STATUS" -> notifyClient(ctx)
            else -> ctx.send(
                """
                        |Invalid command.
                        |Supported commands are:
                        |    - STATUS
                        |       sends the current download status
                        |
                """.trimMargin()
            )
        }
    }

    private fun notifyAllClients() {
        val status = getStatus()
        clients.forEach {
            it.value.send(status)
        }
    }

    private fun getStatus(): DownloadStatus {
        return DownloadStatus(
            if (downloader == null ||
                downloadQueue.none { it.state == Downloading }
            ) {
                "Stopped"
            } else {
                "Started"
            },
            downloadQueue
        )
    }

    @Serializable
    data class EnqueueInput(
        val mangaId: Int,
        val chapterIndex: Int
    )

    fun enqueueMultiple(inputs: List<EnqueueInput>) {
        val mangas = transaction {
            val mangaIds = inputs.map { it.mangaId }.distinct()
            MangaTable.select { MangaTable.id inList mangaIds }
                .map { MangaTable.toDataClass(it) }
        }

        // This list will have unwanted chapters from other manga but there is no simple way
        // to select only wanted ones. It will be mapped to input later so it should not be problem
        val chapters = transaction {
            val chapterIndexes = inputs.map { it.chapterIndex }.distinct()
            ChapterTable.select { ChapterTable.sourceOrder inList chapterIndexes }.toList()
        }

        val mappedInputs = transaction {
            inputs.map {
                Pair(
                    mangas.first { manga -> manga.id == it.mangaId },
                    ChapterTable.toDataClass(
                        chapters.first { chapter ->
                            inputs.find { input ->
                                it.chapterIndex == chapter[ChapterTable.sourceOrder] &&
                                    input.mangaId == chapter[ChapterTable.manga].value
                            } != null
                        }
                    )
                )
            }
        }

        addMultipleToQueue(mappedInputs)
    }

    /**
     * Tries to add multiple inputs to queue
     * If any of inputs was actually added to queue, starts the queue
     */
    private fun addMultipleToQueue(inputs: List<Pair<MangaDataClass, ChapterDataClass>>) {
        val addedChapters = inputs.map { addToQueue(it.first, it.second) }
        val anyAdded = addedChapters.any { it != null }

        if (anyAdded) {
            start()
            notifyAllClients()
        }
    }

    /**
     * Tries to add chapter to queue.
     * If chapter is added, returns the created DownloadChapter, otherwise returns null
     */
    private fun addToQueue(manga: MangaDataClass, chapter: ChapterDataClass): DownloadChapter? {
        if (downloadQueue.none { it.mangaId == manga.id && it.chapterIndex == chapter.index }) {
            val dc = DownloadChapter(
                chapter.index,
                manga.id,
                chapter,
                manga
            )
            downloadQueue.add(dc)
            logger.debug("Added to download queue: ${manga.title} | ${chapter.index}")
            return dc
        }
        logger.debug("Chapter already present in queue: ${manga.title} | ${chapter.index}")
        return null
    }

    fun unqueue(chapterIndex: Int, mangaId: Int) {
        downloadQueue.removeIf { it.mangaId == mangaId && it.chapterIndex == chapterIndex }
        notifyAllClients()
    }

    fun start() {
        if (downloader != null && !downloader?.isAlive!!) {
            // doesn't exist or is dead
            downloader = null
        }

        if (downloader == null) {
            downloader = Downloader(downloadQueue) { notifyAllClients() }
            downloader!!.start()
        }

        notifyAllClients()
    }

    fun stop() {
        downloader?.let {
            synchronized(it.shouldStop) {
                it.shouldStop = true
            }
        }
        downloader = null
        notifyAllClients()
    }

    fun clear() {
        stop()
        downloadQueue.clear()
        notifyAllClients()
    }
}

enum class DownloaderState(val state: Int) {
    Stopped(0),
    Running(1),
    Paused(2)
}
