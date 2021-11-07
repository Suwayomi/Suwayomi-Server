package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.Manga.getManga
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Downloading
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
                        |""".trimMargin()
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
            ) "Stopped" else "Started",
            downloadQueue
        )
    }

    suspend fun enqueue(chapterIndex: Int, mangaId: Int) {
        if (downloadQueue.none { it.mangaId == mangaId && it.chapterIndex == chapterIndex }) {
            downloadQueue.add(
                DownloadChapter(
                    chapterIndex,
                    mangaId,
                    chapter = ChapterTable.toDataClass(
                        transaction {
                            ChapterTable.select { (ChapterTable.manga eq mangaId) and (ChapterTable.sourceOrder eq chapterIndex) }
                                .first()
                        }
                    ),
                    manga = getManga(mangaId)
                )
            )
            start()
        }
        notifyAllClients()
    }

    fun unqueue(chapterIndex: Int, mangaId: Int) {
        downloadQueue.removeIf { it.mangaId == mangaId && it.chapterIndex == chapterIndex }
        notifyAllClients()
    }

    fun start() {
        if (downloader != null && !downloader?.isAlive!!) // doesn't exist or is dead
            downloader = null

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
    Paused(2),
}
