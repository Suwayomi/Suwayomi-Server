package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.websocket.WsContext
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.event.Event
import suwayomi.tachidesk.event.enums.EventType
import suwayomi.tachidesk.manga.impl.Manga.getManga
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Downloading
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import java.util.concurrent.CopyOnWriteArrayList

object DownloadManager {
    val eventDispatcher = DownloadEventDispatcher()
    private val downloadQueue = CopyOnWriteArrayList<DownloadChapter>()
    private var downloader: Downloader? = null

    fun notifyClient(ctx: WsContext) {
        eventDispatcher.notifyClient(ctx, getStatus())
    }

    private fun getStatus(): Event<DownloadStatus> {
        val status = DownloadStatus(
            if (downloader == null ||
                downloadQueue.none { it.state == Downloading }
            ) {
                "Stopped"
            } else {
                "Started"
            },
            downloadQueue
        )
        return Event(
            type = EventType.STATIC,
            entity = status
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
        val status = getStatus()
        eventDispatcher.enqueue(status)
        eventDispatcher.notifyAllClients(status)
    }

    fun unqueue(chapterIndex: Int, mangaId: Int) {
        downloadQueue.removeIf { it.mangaId == mangaId && it.chapterIndex == chapterIndex }
        val status = getStatus()
        eventDispatcher.dequeue(status)
        eventDispatcher.notifyAllClients(status)
    }

    fun start() {
        if (downloader != null && !downloader?.isAlive!!) {
            // doesn't exist or is dead
            downloader = null
        }

        if (downloader == null) {
            downloader = Downloader(downloadQueue) { eventDispatcher.notifyAllClients(getStatus()) }
            downloader!!.start()
        }

        eventDispatcher.notifyAllClients(getStatus())
    }

    fun stop() {
        downloader?.let {
            synchronized(it.shouldStop) {
                it.shouldStop = true
            }
        }
        downloader = null
        eventDispatcher.notifyAllClients(getStatus())
    }

    fun clear() {
        stop()
        downloadQueue.clear()
        eventDispatcher.notifyAllClients(getStatus())
    }
}

enum class DownloaderState(val state: Int) {
    Stopped(0),
    Running(1),
    Paused(2)
}
