package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Thumbnail fetch pipeline against a hit-counting image server. Manga rows use
 * an unregistered source id so getSourceOrStub returns a StubSource.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MangaThumbnailTest : ApplicationTest() {
    private lateinit var imageServer: HttpServer
    private val imageServerHits = AtomicInteger()
    private val createdMangaIds = mutableListOf<Int>()
    private val network: NetworkHelper by lazy { Injekt.get() }

    @BeforeEach
    fun startImageServer() {
        // a tripped cover-tier circuit from another suite would bleed in — start clean
        network.thumbnailHostBreaker.reset()
        imageServer =
            HttpServer
                .create(InetSocketAddress("127.0.0.1", 0), 0)
                .apply {
                    createContext("/ok.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.sendResponseHeaders(200, PNG_BYTES.size.toLong())
                        exchange.responseBody.use { it.write(PNG_BYTES) }
                    }
                    createContext("/gone.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.sendResponseHeaders(404, -1)
                        exchange.close()
                    }
                    // slow enough that a concurrent request reaches the fetch lock
                    // while this one is still in flight
                    createContext("/slow-gone.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        Thread.sleep(300)
                        exchange.sendResponseHeaders(404, -1)
                        exchange.close()
                    }
                    createContext("/slow-ok.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        Thread.sleep(300)
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.sendResponseHeaders(200, PNG_BYTES.size.toLong())
                        exchange.responseBody.use { it.write(PNG_BYTES) }
                    }
                    start()
                }
        imageServerHits.set(0)
    }

    @AfterEach
    fun tearDown() {
        imageServer.stop(0)
        // clearThumbnail also drops remembered failures; rows go last so nothing outlives shared state
        createdMangaIds.forEach { Manga.clearThumbnail(it) }
        createdMangaIds.clear()
        clearTables(
            MangaUserTable,
            MangaTable,
        )
    }

    private fun imageUrl(path: String) = "http://127.0.0.1:${imageServer.address.port}$path"

    private fun createManga(
        thumbnailUrl: String,
        inLibrary: Boolean,
    ): Int {
        val mangaId =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[title] = "THUMB_TEST"
                        it[url] = "THUMB_TEST/$thumbnailUrl/$inLibrary"
                        it[sourceReference] = 1
                        it[thumbnail_url] = thumbnailUrl
                        it[initialized] = true
                    }.value
            }
        if (inLibrary) {
            transaction {
                MangaUserTable.insert {
                    it[manga] = mangaId
                    it[user] = 1
                    it[MangaUserTable.inLibrary] = true
                }
            }
        }
        createdMangaIds += mangaId
        return mangaId
    }

    @Test
    fun libraryThumbnailIsDownloadedAndServedFromDisk() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = true)

            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            val bytes = stream.use { it.readBytes() }

            assertEquals("image/png", mime)
            assertContentEquals(PNG_BYTES, bytes)
            assertEquals(1, imageServerHits.get())

            // the just-downloaded file now serves the cover — no further requests
            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aFailedLibraryThumbnailFetchIsNotRepeatedWithinARequest() =
        runBlocking {
            val mangaId = createManga(imageUrl("/gone.png"), inLibrary = true)

            assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }

            // one dead cover, one request — the failure is not retried by the caller
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aFailedThumbnailFetchIsReplayedWithoutRefetching() =
        runBlocking {
            val mangaId = createManga(imageUrl("/gone.png"), inLibrary = true)

            val first = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
            val second = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }

            // the remembered failure is replayed — the identical error, zero requests
            assertSame(first, second)
            assertEquals(1, imageServerHits.get())

            Manga.clearThumbnail(mangaId)

            val third = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
            assertNotSame(first, third)
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun aFailedBrowsedThumbnailFetchIsNotRefetched() =
        runBlocking {
            val mangaId = createManga(imageUrl("/gone.png"), inLibrary = false)

            val first = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
            val second = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }

            assertSame(first, second)
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun concurrentFailedFetchesShareOneSourceHit() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-gone.png"), inLibrary = true)

            // both requests race the same dead cover: the loser queues on the
            // per-manga fetch lock and must replay the failure recorded there
            // instead of fetching the source a second time
            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            val second = async { runCatching { Manga.getMangaThumbnail(mangaId) } }

            val firstError = assertIs<HttpException>(first.await().exceptionOrNull())
            assertSame(firstError, second.await().exceptionOrNull())
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun concurrentFailedBrowsedFetchesShareOneSourceHit() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-gone.png"), inLibrary = false)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            val second = async { runCatching { Manga.getMangaThumbnail(mangaId) } }

            val firstError = assertIs<HttpException>(first.await().exceptionOrNull())
            assertSame(firstError, second.await().exceptionOrNull())
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun concurrentSuccessfulFetchesNeverDuplicateOrCorruptTheCover() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-ok.png"), inLibrary = true)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            val second = async { runCatching { Manga.getMangaThumbnail(mangaId) } }

            val firstResult = first.await().getOrThrow()
            val secondResult = second.await().getOrThrow()

            // whichever fetch raced ahead, both callers get the complete file —
            // never a spliced half from a shared .tmp
            val firstBytes = firstResult.first.use { it.readBytes() }
            val secondBytes = secondResult.first.use { it.readBytes() }
            assertContentEquals(PNG_BYTES, firstBytes)
            assertContentEquals(PNG_BYTES, secondBytes)
            assertEquals("image/png", firstResult.second)
            assertEquals("image/png", secondResult.second)

            // the queued request sees the landed file under the lock — at most
            // one redundant fetch can slip through the recheck window
            assertTrue(imageServerHits.get() in 1..2, "hits: ${imageServerHits.get()}")
        }

    private companion object {
        // a real 1x1 PNG: saveImage sniffs the magic bytes for the stored extension
        val PNG_BYTES: ByteArray =
            java.util.Base64
                .getDecoder()
                .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
    }
}
