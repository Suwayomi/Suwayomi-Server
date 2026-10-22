package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.HostCircuitOpenException
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.network.MAX_CAUSE_DEPTH
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.util.SafePath
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/** Page-image pipeline against a hit-counting server: shared image dispatcher, page-tier
 *  breaker (covers judge separately), disk-hit replay. Rows point at a registered [HttpSource]. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageClientPageTest : ApplicationTest() {
    private lateinit var imageServer: HttpServer
    private val imageServerHits = AtomicInteger()
    private val network: NetworkHelper by lazy { Injekt.get() }
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val testSource = PageImageSource()

    @BeforeEach
    fun startImageServer() {
        // a tripped circuit or a leftover cached page from another run would bleed in — start clean
        network.imageHostBreaker.reset()
        network.thumbnailHostBreaker.reset()
        sourceCacheDir().deleteRecursively()
        GetSource.registerSource(testSource.id to testSource)
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
                    start()
                }
        imageServerHits.set(0)
    }

    @AfterEach
    fun tearDown() {
        imageServer.stop(0)
        sourceCacheDir().deleteRecursively()
        GetSource.unregisterSource(testSource.id)
        clearTables(
            PageTable,
            ChapterTable,
            MangaTable,
        )
    }

    private fun imageUrl(path: String) = "http://127.0.0.1:${imageServer.address.port}$path"

    // the chapter cache nests under manga-cache/<source dir>/<manga>/<chapter>
    private fun sourceCacheDir() =
        File(
            applicationDirs.tempMangaCacheRoot,
            SafePath.buildValidFilename(testSource.toString()),
        )

    private fun createPage(imageUrl: String): Pair<Int, Int> {
        val mangaId =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[title] = "PAGE_IMG_TEST"
                        it[url] = "PAGE_IMG_TEST/$imageUrl"
                        it[sourceReference] = testSource.id
                        it[initialized] = true
                    }.value
            }
        val chapterId =
            transaction {
                ChapterTable
                    .insertAndGetId {
                        it[url] = "PAGE_IMG_TEST/$mangaId"
                        it[name] = "PAGE_IMG_TEST/$mangaId"
                        it[sourceOrder] = 1
                        it[manga] = mangaId
                        it[pageCount] = 1
                        it[memo] = JsonObject.EMPTY
                    }.value
            }
        transaction {
            PageTable.insert {
                it[index] = 0
                it[url] = "PAGE_IMG_TEST/$mangaId/0"
                it[this.imageUrl] = imageUrl
                it[chapter] = chapterId
            }
        }
        return mangaId to chapterId
    }

    @Test
    fun pageImageIsFetchedThroughTheImageClientAndServedFromDiskOnReplay() =
        runBlocking {
            val (mangaId, chapterId) = createPage(imageUrl("/ok.png"))

            val (stream, mime) = Page.getPageImage(mangaId = mangaId, chapterId = chapterId, index = 0)
            val bytes = stream.use { it.readBytes() }

            assertEquals("image/png", mime)
            assertContentEquals(PNG_BYTES, bytes)
            assertEquals(1, imageServerHits.get())

            // the fetch landed on disk under the chapter cache dir
            val cacheDir = getChapterCachePath(mangaId, chapterId)
            assertTrue(File(cacheDir, "001.png").isFile, "expected 001.png in $cacheDir")

            // the repeat read serves the cached file — no further request
            Page.getPageImage(mangaId = mangaId, chapterId = chapterId, index = 0).first.close()
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aDeadImageHostOpensTheCircuitForPageFetches() =
        runBlocking {
            // a bound-then-closed socket hands back a port that refuses connections
            val deadPort = ServerSocket(0).use { it.localPort }
            val (mangaId, chapterId) = createPage("http://127.0.0.1:$deadPort/x.png")

            val failures =
                List(3) {
                    assertFailsWith<IOException> {
                        Page.getPageImage(mangaId = mangaId, chapterId = chapterId, index = 0)
                    }
                }
            // Call.await wraps transport failures in an IOException — judge by the cause chain
            failures.forEach { assertFalse(it.hasHostCircuitOpenCause()) }

            // the page tier's breaker now judges the host:port dead — the next
            // page fetch is refused before any socket work
            val (rejection, elapsed) =
                measureTimedValue {
                    assertFailsWith<IOException> {
                        Page.getPageImage(mangaId = mangaId, chapterId = chapterId, index = 0)
                    }
                }
            assertTrue(rejection.hasHostCircuitOpenCause())
            assertTrue(elapsed < 500.milliseconds, "fail-fast took $elapsed")
            assertTrue(rejection.message.orEmpty().contains("127.0.0.1:$deadPort"))

            // tier isolation: the cover tier collected none of this evidence — a cover
            // call to the same dead host still pays a real connection attempt
            val coverFailure =
                assertFailsWith<IOException> {
                    network.thumbnailClient().newCall(GET("http://127.0.0.1:$deadPort/cover.png")).execute()
                }
            assertFalse(coverFailure.hasHostCircuitOpenCause())
        }

    @Test
    fun aDeadCoverHostOpensOnlyTheCoverTierCircuit() =
        runBlocking {
            val deadPort = ServerSocket(0).use { it.localPort }
            val deadCoverUrl = "http://127.0.0.1:$deadPort/cover.png"

            // three real transport failures trip the cover tier's own breaker
            repeat(3) {
                val failure =
                    assertFailsWith<IOException> {
                        network.thumbnailClient().newCall(GET(deadCoverUrl)).execute()
                    }
                assertFalse(failure.hasHostCircuitOpenCause())
            }

            val rejection =
                assertFailsWith<IOException> {
                    network.thumbnailClient().newCall(GET(deadCoverUrl)).execute()
                }
            assertTrue(rejection.hasHostCircuitOpenCause())

            // the page tier judged nothing — a page fetch to the same dead host still pays a real attempt
            val (mangaId, chapterId) = createPage("http://127.0.0.1:$deadPort/page.png")
            val pageFailure =
                assertFailsWith<IOException> {
                    Page.getPageImage(mangaId = mangaId, chapterId = chapterId, index = 0)
                }
            assertFalse(pageFailure.hasHostCircuitOpenCause())
        }

    private fun Throwable.hasHostCircuitOpenCause(): Boolean =
        generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is HostCircuitOpenException }

    private class PageImageSource : HttpSource() {
        override val baseUrl = "http://127.0.0.1"
        override val lang = "en"
        override val name = "PageImageSource"
        override val supportsLatest = false
    }

    private companion object {
        // a real 1x1 PNG: saveImage sniffs the magic bytes for the stored extension
        val PNG_BYTES: ByteArray =
            java.util.Base64
                .getDecoder()
                .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
    }
}
