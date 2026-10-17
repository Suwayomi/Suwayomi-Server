package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.json.JavalinJackson3
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.download.model.DownloadState
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.model.OldDownloadStatus
import suwayomi.tachidesk.manga.impl.download.model.Status
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DownloadWebSocketStatusTest : ApplicationTest() {
    @Test
    fun initialAndRequestedStatusIncludeLegacyChapterAndManga() {
        val mangaId = createLibraryManga("Legacy WebSocket manga")

        try {
            val chapterId =
                transaction {
                    ChapterTable
                        .insertAndGetId {
                            it[url] = "legacy-websocket-chapter"
                            it[name] = "Chapter 1"
                            it[sourceOrder] = 1
                            it[manga] = mangaId
                            it[pageCount] = 4
                            it[memo] = JsonObject.EMPTY
                        }.value
                }
            val status =
                DownloadStatus(
                    Status.Stopped,
                    listOf(
                        DownloadQueueItem(
                            chapterId = chapterId,
                            chapterIndex = 1,
                            mangaId = mangaId,
                            sourceId = 1L,
                            pageCount = 4,
                            state = DownloadState.Error,
                            progress = 0.5f,
                            tries = 3,
                        ),
                    ),
                )
            val replies = mutableListOf<Any>()
            val initialContext = mockk<WsContext>()
            val requestContext = mockk<WsMessageContext>()
            every { initialContext.send(capture(replies)) } just Runs
            every { requestContext.message() } returns "STATUS"
            every { requestContext.send(capture(replies)) } just Runs

            mockkObject(DownloadManager)
            try {
                every { DownloadManager.getStatus() } returns status

                DownloadManager.notifyClient(initialContext)
                DownloadManager.handleRequest(requestContext)

                assertEquals(2, replies.size)
                for (reply in replies) {
                    val legacyStatus = assertIs<OldDownloadStatus>(reply)
                    val queuedChapter = legacyStatus.queue.single()
                    assertEquals(chapterId, queuedChapter.chapter.id)
                    assertEquals(mangaId, queuedChapter.manga.id)
                    assertEquals(DownloadState.Error, queuedChapter.state)
                    assertEquals(0.5f, queuedChapter.progress)
                    assertEquals(3, queuedChapter.tries)

                    val json = JavalinJackson3().toJsonString(reply, reply.javaClass)
                    val queuedJson =
                        Json
                            .parseToJsonElement(json)
                            .jsonObject
                            .getValue("queue")
                            .jsonArray
                            .single()
                            .jsonObject
                    assertEquals("Chapter 1", queuedJson.getValue("chapter").jsonObject.getValue("name").jsonPrimitive.content)
                    assertEquals(
                        "Legacy WebSocket manga",
                        queuedJson.getValue("manga").jsonObject.getValue("title").jsonPrimitive.content,
                    )
                }
            } finally {
                unmockkObject(DownloadManager)
            }
        } finally {
            transaction {
                MangaTable.deleteWhere { id eq mangaId }
            }
        }
    }
}
