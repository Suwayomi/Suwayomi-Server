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
import org.jetbrains.exposed.sql.and
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

    fun enqueueWithChapterIndex(mangaId: Int, chapterIndex: Int) {
        val chapter = transaction {
            ChapterTable
                .slice(ChapterTable.id)
                .select { ChapterTable.manga.eq(mangaId) and ChapterTable.sourceOrder.eq(chapterIndex) }
                .first()
        }
        enqueue(listOf(EnqueueInput(chapter[ChapterTable.id].value)))
    }

    @Serializable
    data class EnqueueInput(
        val chapterId: Int
    )

    fun enqueue(inputs: List<EnqueueInput>) {
        val chapters = transaction {
            val chapterIds = inputs.map { it.chapterId }.distinct()
            (ChapterTable innerJoin MangaTable)
                .select { ChapterTable.id inList chapterIds }
                .toList()
        }

        val mangas = transaction {
            chapters.distinctBy { chapter -> chapter[MangaTable.id] }
                .map { MangaTable.toDataClass(it) }
                .associateBy { it.id }
        }

        val inputPairs = transaction {
            chapters.map {
                Pair(
                    // this should be safe because mangas is created above from chapters
                    mangas[it[ChapterTable.manga].value]!!,
                    ChapterTable.toDataClass(it)
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
            notifyAllClients()
        }
    }

    /**
     * Tries to add chapter to queue.
     * If chapter is added, returns the created DownloadChapter, otherwise returns null
     */
    private fun addToQueue(manga: MangaDataClass, chapter: ChapterDataClass): DownloadChapter? {
        if (downloadQueue.none { it.mangaId == manga.id && it.chapterIndex == chapter.index }) {
            val downloadChapter = DownloadChapter(
                chapter.index,
                manga.id,
                chapter,
                manga
            )
            downloadQueue.add(downloadChapter)
            logger.debug { "Added chapter ${chapter.id} to download queue (${manga.title} | ${chapter.name})" }
            return downloadChapter
        }
        logger.debug { "Chapter ${chapter.id} already present in queue (${manga.title} | ${chapter.name})" }
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
