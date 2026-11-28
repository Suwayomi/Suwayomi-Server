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
import eu.kanade.tachiyomi.network.hasOpenCircuit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.graphql.mutations.ImageMutation
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.MissingThumbnailException
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Thumbnail pipeline against a hit-counting image server. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MangaThumbnailTest : ApplicationTest() {
    private lateinit var imageServer: HttpServer
    private val imageServerHits = AtomicInteger()
    private val createdMangaIds = mutableListOf<Int>()
    private val network: NetworkHelper by lazy { Injekt.get() }
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val urlSource = ThumbnailUrlSource()
    private var slowBodyGate = CountDownLatch(1)
    private var slowBodyStarted = CountDownLatch(1)
    private var gatedGoneStarted = CountDownLatch(1)
    private var gatedGoneGate = CountDownLatch(1)

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
                    // same verdict as slow-gone, but handshake-gated: the request is provably
                    // on the wire before the test moves, and the 404 lands on the gate opening
                    createContext("/gated-gone.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        gatedGoneStarted.countDown()
                        runCatching { gatedGoneGate.await(10, TimeUnit.SECONDS) }
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
                    // flushes headers + half the body, then parks — the fetch is
                    // mid-read when the gate opens
                    createContext("/slow-body.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.sendResponseHeaders(200, SLOW_BODY_BYTES.size.toLong())
                        exchange.responseBody.write(PNG_BYTES)
                        exchange.responseBody.flush()
                        slowBodyStarted.countDown()
                        runCatching { slowBodyGate.await(10, TimeUnit.SECONDS) }
                        exchange.responseBody.use { it.write(SLOW_BODY_TAIL) }
                    }
                    // declares twice the body it delivers — the read dies mid-stream; the flush
                    // lands headers+half the body first, so the death is never a retriable pre-header one
                    createContext("/truncated.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.sendResponseHeaders(200, PNG_BYTES.size.toLong() * 2)
                        exchange.responseBody.use {
                            it.write(PNG_BYTES)
                            it.flush()
                        }
                    }
                    createContext("/error.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.sendResponseHeaders(500, -1)
                        exchange.close()
                    }
                    // "successful" codes that carry no publishable cover
                    createContext("/empty.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.sendResponseHeaders(204, -1)
                        exchange.close()
                    }
                    createContext("/partial.png") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.sendResponseHeaders(206, PNG_BYTES.size.toLong())
                        exchange.responseBody.use { it.write(PNG_BYTES) }
                    }
                    createContext("/cover.jpg") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.responseHeaders.add("Content-Type", "image/jpeg")
                        exchange.sendResponseHeaders(200, JPEG_BYTES.size.toLong())
                        exchange.responseBody.use { it.write(JPEG_BYTES) }
                    }
                    createContext("/cover.svg") { exchange ->
                        imageServerHits.incrementAndGet()
                        exchange.responseHeaders.add("Content-Type", "image/svg+xml")
                        exchange.sendResponseHeaders(200, SVG_BYTES.size.toLong())
                        exchange.responseBody.use { it.write(SVG_BYTES) }
                    }
                    slowBodyGate = CountDownLatch(1)
                    slowBodyStarted = CountDownLatch(1)
                    gatedGoneStarted = CountDownLatch(1)
                    gatedGoneGate = CountDownLatch(1)
                    start()
                }
        imageServerHits.set(0)
    }

    @AfterEach
    fun tearDown() {
        imageServer.stop(0)
        Manga.thumbnailStagedForPublish = null
        Manga.thumbnailReuseDecidedForPublish = null
        Manga.writeThumbnailLastModified = { file, version -> file.setLastModified(version) }
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
        sourceId: Long = 1,
    ): Int {
        val mangaId =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[title] = "THUMB_TEST"
                        it[url] = "THUMB_TEST/$thumbnailUrl/$inLibrary"
                        it[sourceReference] = sourceId
                        it[thumbnail_url] = thumbnailUrl
                        it[thumbnailUrlVersion] = System.currentTimeMillis()
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

            // the queued request serves the landed file under the lock — one fetch, one hit
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun concurrentSuccessfulBrowsedFetchesShareOneSourceHit() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-ok.png"), inLibrary = false)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            val second = async { runCatching { Manga.getMangaThumbnail(mangaId) } }

            val firstResult = first.await().getOrThrow()
            val secondResult = second.await().getOrThrow()
            assertContentEquals(PNG_BYTES, firstResult.first.use { it.readBytes() })
            assertContentEquals(PNG_BYTES, secondResult.first.use { it.readBytes() })
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aCoverDownloadAndABrowseRaceTheSameFetch() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-ok.png"), inLibrary = true)

            // the library download and the REST read serialize on one fetch lock
            val download = async { ThumbnailDownloadHelper.download(mangaId) }
            val browse = async { runCatching { Manga.getMangaThumbnail(mangaId) } }

            assertTrue(download.await())
            val (stream, _) = browse.await().getOrThrow()
            assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aSlowBodyIsFetchedOnceForConcurrentReaders() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-body.png"), inLibrary = false)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(slowBodyStarted), "first fetch never reached mid-body")
            val second = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            awaitLockRefCount(mangaId, 2) // the second reader is queued on the same lock entry
            slowBodyGate.countDown()

            val firstResult = first.await().getOrThrow()
            val secondResult = second.await().getOrThrow()
            assertContentEquals(SLOW_BODY_BYTES, firstResult.first.use { it.readBytes() })
            assertContentEquals(SLOW_BODY_BYTES, secondResult.first.use { it.readBytes() })
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aTruncatedBodyCountsLikeAFailedFetchAndLeavesNoStaging() =
        runBlocking {
            val mangaId = createManga(imageUrl("/truncated.png"), inLibrary = false)

            // the original network error surfaces — not the fetch-failure wrapper
            val first = assertFailsWith<IOException> { Manga.getMangaThumbnail(mangaId) }

            val followerA = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            val followerB = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertSame(first, followerA.await().exceptionOrNull())
            assertSame(first, followerB.await().exceptionOrNull())

            assertEquals(1, imageServerHits.get())
            assertNoStagingLeft()
        }

    @Test
    fun aTruncatedLibraryCoverBodyIsRememberedAndStillServesTheOldCover() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.jpg"), inLibrary = true)
            Manga.getMangaThumbnail(mangaId).first.close() // publishes the downloaded cover
            val cover = File(downloadedCoverPath(mangaId)!!)
            transaction {
                MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                    it[thumbnail_url] = imageUrl("/truncated.png")
                    it[thumbnailUrlLastFetched] = Instant.now().epochSecond
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }
            cover.setLastModified(1_000L) // the refresh owes the newer version a fetch

            repeat(2) {
                val (stream, mime) = Manga.getMangaThumbnail(mangaId)
                assertContentEquals(JPEG_BYTES, stream.use { it.readBytes() })
                assertEquals("image/jpeg", mime)
            }

            // the body read died inside the locked consume — the replayed verdict costs no second hit
            assertEquals(2, imageServerHits.get())
            assertNoStagingLeft()
        }

    @Test
    fun aLateOldCoverBodyCannotDisplaceANewerCover() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.jpg"), inLibrary = true)
            Manga.getMangaThumbnail(mangaId).first.close()
            val cover = File(downloadedCoverPath(mangaId)!!)
            transaction {
                MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                    it[thumbnail_url] = imageUrl("/slow-body.png")
                    it[thumbnailUrlLastFetched] = Instant.now().epochSecond
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }
            cover.setLastModified(1_000L)

            val staged = CountDownLatch(1)
            val publish = CountDownLatch(1)
            val hookUsed = AtomicBoolean(false)
            Manga.thumbnailStagedForPublish = { id ->
                if (id == mangaId && hookUsed.compareAndSet(false, true)) {
                    staged.countDown()
                    publish.await(5, TimeUnit.SECONDS)
                }
            }
            try {
                val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
                assertTrue(awaitLatch(slowBodyStarted), "the refresh fetch never reached mid-body")
                slowBodyGate.countDown()
                assertTrue(awaitLatch(staged), "the old body never finished staging")
                transaction {
                    MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                        it[thumbnail_url] = imageUrl("/ok.png")
                        it[thumbnailUrlLastFetched] = Instant.now().epochSecond
                        it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                    }
                }
                val second = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
                awaitLockRefCount(mangaId, 2)
                publish.countDown()

                // the late body answered a dead URL: it serves stale and publishes nothing
                assertContentEquals(
                    JPEG_BYTES,
                    first
                        .await()
                        .getOrThrow()
                        .first
                        .use { it.readBytes() },
                )
                // the queued refresh fetched and published the new cover under the same lock
                assertContentEquals(
                    PNG_BYTES,
                    second
                        .await()
                        .getOrThrow()
                        .first
                        .use { it.readBytes() },
                )
                assertContentEquals(PNG_BYTES, File(downloadedCoverPath(mangaId)!!).readBytes())

                // the discard never faked freshness — the next request serves the cover without a fetch
                Manga.getMangaThumbnail(mangaId).first.close()
                assertEquals(3, imageServerHits.get())
            } finally {
                publish.countDown()
                Manga.thumbnailStagedForPublish = null
            }
        }

    @Test
    fun aMetadataVersionBumpRearmsARememberedFailure() =
        runBlocking {
            val mangaId = createManga(imageUrl("/gone.png"), inLibrary = false)

            val first = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
            assertSame(first, assertFailsWith { Manga.getMangaThumbnail(mangaId) })

            // same-second re-attestation: only the strict version moves
            transaction {
                MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }
            val third = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
            assertNotSame(first, third)
            assertSame(third, assertFailsWith { Manga.getMangaThumbnail(mangaId) })
            assertEquals(2, imageServerHits.get())
            // the retry did not need the old cover deleted — nothing was ever published
            assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId"))
        }

    @Test
    fun aStaleInFlightFailureIsNotWrittenBackAfterACleanup() =
        runBlocking {
            val mangaId = createManga(imageUrl("/gated-gone.png"), inLibrary = false)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(gatedGoneStarted), "the GET never reached the wire")
            // the cleanup lands while the response is parked — before its verdict writes back
            Manga.clearThumbnail(mangaId)
            gatedGoneGate.countDown()
            assertIs<HttpException>(first.await().exceptionOrNull())

            // the pre-cleanup verdict was dropped: this request pays a real fetch
            assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun aCleanupMidFetchLetsTheNextRequestServeTheFreshUrl() =
        runBlocking {
            val mangaId = createManga(imageUrl("/gated-gone.png"), inLibrary = false)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(gatedGoneStarted), "the GET never reached the wire")
            // a metadata refresh commits a new URL and invalidates the old cover
            transaction {
                MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                    it[thumbnail_url] = imageUrl("/ok.png")
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }
            Manga.clearThumbnail(mangaId)
            gatedGoneGate.countDown()
            assertIs<HttpException>(first.await().exceptionOrNull())

            // the old URL's late verdict is stale and unbound to the new URL — the
            // next request actually fetches it and publishes the cover
            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/png", mime)
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun aClearBeforeTheFinalMoveReapsTheInFlightPublish() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            val staged = CountDownLatch(1)
            val release = CountDownLatch(1)
            Manga.thumbnailStagedForPublish = { id ->
                if (id == mangaId) {
                    staged.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(staged), "the fetch never reached the publish barrier")
            Manga.clearThumbnail(mangaId)
            release.countDown()
            assertNotNull(first.await().exceptionOrNull())

            assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId"))
            assertNoStagingLeft()

            val (stream, _) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun aBulkImageClearBeforeTheFinalMoveReapsTheInFlightPublish() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            val staged = CountDownLatch(1)
            val release = CountDownLatch(1)
            Manga.thumbnailStagedForPublish = { id ->
                if (id == mangaId) {
                    staged.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(staged), "the fetch never reached the publish barrier")
            ImageMutation().clearCachedImages(ImageMutation.ClearCachedImagesInput(cachedThumbnails = true))
            release.countDown()
            assertNotNull(first.await().exceptionOrNull())

            assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId"))
            assertNoStagingLeft()
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aDeleteBeforeTheFinalMoveReapsTheInFlightDownloadedPublish() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.jpg"), inLibrary = true)
            Manga.getMangaThumbnail(mangaId).first.close()
            assertTrue(downloadedCoverPath(mangaId) != null)
            transaction {
                MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                    it[thumbnail_url] = imageUrl("/ok.png")
                    it[thumbnailUrlLastFetched] = Instant.now().epochSecond
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }
            val staged = CountDownLatch(1)
            val release = CountDownLatch(1)
            Manga.thumbnailStagedForPublish = { id ->
                if (id == mangaId) {
                    staged.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(staged), "the refresh never reached the publish barrier")
            ThumbnailDownloadHelper.delete(mangaId)
            release.countDown()
            assertNotNull(first.await().exceptionOrNull())

            assertNull(downloadedCoverPath(mangaId))
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun clearingDownloadsDoesNotReapATempPublish() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            val staged = CountDownLatch(1)
            val release = CountDownLatch(1)
            Manga.thumbnailStagedForPublish = { id ->
                if (id == mangaId) {
                    staged.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(staged), "the fetch never reached the publish barrier")
            ImageMutation().clearCachedImages(
                ImageMutation.ClearCachedImagesInput(downloadedThumbnails = true, cachedThumbnails = false),
            )
            release.countDown()

            val (stream, mime) = first.await().getOrThrow()
            assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/png", mime)
            assertEquals(1, imageServerHits.get())
            assertTrue(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId") != null)
        }

    @Test
    fun clearingTempDoesNotReapADownloadsPublish() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.jpg"), inLibrary = true)
            Manga.getMangaThumbnail(mangaId).first.close()
            transaction {
                MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                    it[thumbnail_url] = imageUrl("/ok.png")
                    it[thumbnailUrlLastFetched] = Instant.now().epochSecond
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }
            val staged = CountDownLatch(1)
            val release = CountDownLatch(1)
            Manga.thumbnailStagedForPublish = { id ->
                if (id == mangaId) {
                    staged.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }

            try {
                val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
                assertTrue(awaitLatch(staged), "the refresh never reached the publish barrier")
                ImageMutation().clearCachedImages(
                    ImageMutation.ClearCachedImagesInput(downloadedThumbnails = false, cachedThumbnails = true),
                )
                release.countDown()

                val (stream, mime) = first.await().getOrThrow()
                assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
                assertEquals("image/png", mime)
                assertContentEquals(PNG_BYTES, File(downloadedCoverPath(mangaId)!!).readBytes())
            } finally {
                release.countDown()
                Manga.thumbnailStagedForPublish = null
            }
        }

    @Test
    fun clearingTempDoesNotStaleAnExistingDownload() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = true)
            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(1, imageServerHits.get())

            ImageMutation().clearCachedImages(
                ImageMutation.ClearCachedImagesInput(downloadedThumbnails = false, cachedThumbnails = true),
            )
            assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId"))
            assertTrue(downloadedCoverPath(mangaId) != null)

            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aClearedDownloadIsNotResurrectedFromAnOldTempCover() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.jpg"), inLibrary = true)
            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(1, imageServerHits.get())
            transaction {
                MangaTable.update({ MangaTable.id eq EntityID(mangaId, MangaTable) }) {
                    it[thumbnail_url] = imageUrl("/ok.png")
                    it[thumbnailUrlLastFetched] = Instant.now().epochSecond
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }

            ImageMutation().clearCachedImages(
                ImageMutation.ClearCachedImagesInput(downloadedThumbnails = true, cachedThumbnails = false),
            )
            assertNull(downloadedCoverPath(mangaId))

            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/png", mime)
            assertEquals(2, imageServerHits.get())
            assertTrue(downloadedCoverPath(mangaId) != null)
        }

    @Test
    fun aFailureIsRecordedForTheRefreshedUrlNotTheStaleOne() =
        runBlocking {
            GetSource.registerSource(urlSource.id to urlSource)
            try {
                urlSource.nextThumbnailUrl = imageUrl("/error.png")
                val mangaId = createManga(imageUrl("/gone.png"), inLibrary = false, sourceId = urlSource.id)

                // old URL answers 404 → metadata refresh commits the new URL → its 500 is
                // recorded against it, not the dead URL
                val first = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
                val second = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }

                assertSame(first, second)
                // each URL hit the server exactly once; the replayed verdict costs nothing
                assertEquals(2, imageServerHits.get())
            } finally {
                GetSource.unregisterSource(urlSource.id)
            }
        }

    @Test
    fun aRememberedFailureExpiresWithItsTtl() =
        runBlocking {
            Manga.replaceThumbnailFailureCache(150.milliseconds)
            try {
                val mangaId = createManga(imageUrl("/gone.png"), inLibrary = false)

                val first = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
                assertSame(first, assertFailsWith { Manga.getMangaThumbnail(mangaId) })

                delay(300)
                val later = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
                assertNotSame(first, later)
                assertEquals(2, imageServerHits.get())
            } finally {
                Manga.replaceThumbnailFailureCache(90.seconds)
            }
        }

    @Test
    fun invalidateAllDropsEveryRememberedFailure() =
        runBlocking {
            val mangaId = createManga(imageUrl("/gone.png"), inLibrary = false)
            val first = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }

            Manga.invalidateAllThumbnailFailures()

            val second = assertFailsWith<HttpException> { Manga.getMangaThumbnail(mangaId) }
            assertNotSame(first, second)
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun cancellingABodyConsumerReleasesTheSourceAndTheLock() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-body.png"), inLibrary = false)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            assertTrue(awaitLatch(slowBodyStarted))
            first.cancel() // mid-body cancel must reach Call.cancel()
            first.awaitCancelled()
            slowBodyGate.countDown()

            // the aborted read wrote nothing — the next request fetches for real
            val (stream, _) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(SLOW_BODY_BYTES, stream.use { it.readBytes() })
            assertEquals(2, imageServerHits.get())
            assertEquals(0, Manga.thumbnailLockEntryCount())
            assertNoStagingLeft()
        }

    @Test
    fun cancellingALockWaiterLeavesOneLockAndNoOrphans() =
        runBlocking {
            val mangaId = createManga(imageUrl("/slow-body.png"), inLibrary = false)

            val first = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
            try {
                assertTrue(awaitLatch(slowBodyStarted))
                val second = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
                // the waiter shares the holder's lock entry — never a second mutex
                awaitLockRefCount(mangaId, 2)
                assertEquals(1, Manga.thumbnailLockEntryCount())
                second.cancel()
                slowBodyGate.countDown()

                val firstResult = withTimeout(5_000) { first.await() }.getOrThrow()
                assertContentEquals(SLOW_BODY_BYTES, firstResult.first.use { it.readBytes() })
                second.awaitCancelled()
            } finally {
                slowBodyGate.countDown()
            }

            // the landed file serves the next reader; the lock table drained
            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(1, imageServerHits.get())
            assertEquals(0, Manga.thumbnailLockEntryCount())
        }

    @Test
    fun aLocalDiskWriteFailureIsNotCountedAgainstTheSource() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            val tempRoot = File(applicationDirs.tempThumbnailCacheRoot)
            tempRoot.mkdirs()
            val originalPermissions = Files.getPosixFilePermissions(tempRoot.toPath())
            try {
                Files.setPosixFilePermissions(tempRoot.toPath(), emptySet())
                // the GET succeeds — the publish cannot: a local failure earns no cache entry
                assertFailsWith<IOException> { Manga.getMangaThumbnail(mangaId) }
                assertFailsWith<IOException> { Manga.getMangaThumbnail(mangaId) }
            } finally {
                Files.setPosixFilePermissions(tempRoot.toPath(), originalPermissions)
            }
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun nonOkSuccessCodesAreRememberedAndPublishNothing() =
        runBlocking {
            val emptyId = createManga(imageUrl("/empty.png"), inLibrary = false)
            val partialId = createManga(imageUrl("/partial.png"), inLibrary = false)

            val empty = assertFailsWith<HttpException> { Manga.getMangaThumbnail(emptyId) }
            val partial = assertFailsWith<HttpException> { Manga.getMangaThumbnail(partialId) }
            assertEquals(204, empty.code)
            assertEquals(206, partial.code)

            // a "successful" code carrying no publishable cover is the source's verdict — the
            // same-generation reread replays it instead of re-hitting the source on every render
            assertSame(empty, assertFailsWith { Manga.getMangaThumbnail(emptyId) })
            assertSame(partial, assertFailsWith { Manga.getMangaThumbnail(partialId) })

            // neither an empty nor a partial body is a cover — nothing published, nothing staged
            assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$emptyId"))
            assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$partialId"))
            assertNoStagingLeft()
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun aNonOkSuccessRefreshStillServesTheOldCover() =
        runBlocking {
            val mangaId = createManga(imageUrl("/empty.png"), inLibrary = false)
            File(applicationDirs.tempThumbnailCacheRoot).mkdirs()
            val staleCover =
                File(applicationDirs.tempThumbnailCacheRoot, "$mangaId.png").apply {
                    writeBytes(PNG_BYTES)
                    setLastModified(1_000L) // untagged for the current version — a refresh is owed
                }

            repeat(2) {
                val (stream, mime) = Manga.getMangaThumbnail(mangaId)
                assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
                assertEquals("image/png", mime)
            }

            // one verdict, replayed for the second read — the stale cover survived both
            assertEquals(1, imageServerHits.get())
            assertTrue(staleCover.isFile)
        }

    @Test
    fun aPublishedCoverCarriesTheMetadataVersionAsItsMtime() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)

            Manga.getMangaThumbnail(mangaId).first.close()

            val version =
                transaction {
                    MangaTable
                        .select(MangaTable.thumbnailUrlVersion)
                        .where { MangaTable.id eq mangaId }
                        .first()[MangaTable.thumbnailUrlVersion]
                }
            val published = ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId")
            assertEquals(version, File(published!!).lastModified())

            // the exact tag is what makes the next read a disk hit — no second fetch
            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aRoundedVersionTagDoesNotLoopOrValidateAStaleCover() =
        runBlocking {
            val version = 1_700_000_000_123L
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[thumbnailUrlVersion] = version
                }
            }
            Manga.writeThumbnailLastModified = { file, tag ->
                file.setLastModified(tag)
                file.setLastModified(tag - tag % 2_000L)
            }

            Manga.getMangaThumbnail(mangaId).first.close()
            val published = ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId")
            assertNotEquals(version, File(published!!).lastModified())
            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(1, imageServerHits.get())

            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[thumbnailUrlVersion] = version + 1
                }
            }
            Manga.getMangaThumbnail(mangaId).first.close()
            Manga.getMangaThumbnail(mangaId).first.close()
            assertEquals(2, imageServerHits.get())
        }

    @Test
    fun aFailedMtimeWriteStillTagsThePublishedCoverInMemory() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            Manga.writeThumbnailLastModified = { _, _ -> false }

            Manga.getMangaThumbnail(mangaId).first.close()
            Manga.getMangaThumbnail(mangaId).first.close()

            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun coverCleanupUsesTheLockedCurrentVersionAfterALateBump() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            val currentVersion =
                transaction {
                    MangaTable
                        .select(MangaTable.thumbnailUrlVersion)
                        .where { MangaTable.id eq mangaId }
                        .first()[MangaTable.thumbnailUrlVersion]
                }
            val bumpedVersion = (currentVersion / 2_000L + 1) * 2_000L
            File(applicationDirs.tempThumbnailCacheRoot).mkdirs()
            val newCover =
                File(applicationDirs.tempThumbnailCacheRoot, "$mangaId.png").apply {
                    writeBytes(PNG_BYTES)
                    assertTrue(setLastModified(bumpedVersion))
                    assertEquals(bumpedVersion, lastModified())
                }
            val rowLocked = CountDownLatch(1)
            val commitBump = CountDownLatch(1)
            val bump =
                async(Dispatchers.IO) {
                    transaction {
                        MangaTable.update({ MangaTable.id eq mangaId }) {
                            it[thumbnailUrlVersion] = bumpedVersion
                        }
                        rowLocked.countDown()
                        check(commitBump.await(5, TimeUnit.SECONDS)) { "the test never released the version bump" }
                    }
                }

            try {
                assertTrue(awaitLatch(rowLocked), "the version bump never locked the manga row")
                val cleanup =
                    async(Dispatchers.IO) {
                        Manga.deleteStaleThumbnailFiles(listOf(Manga.ThumbnailCleanupTarget(mangaId)))
                    }
                try {
                    awaitThumbnailRowLockWaiter(cleanup)
                } finally {
                    commitBump.countDown()
                }

                assertTrue(cleanup.await())
                bump.await()
                assertTrue(newCover.isFile)
            } finally {
                commitBump.countDown()
            }
        }

    @Test
    fun legacyCoverCleanupDoesNotTrustAnUntaggedBareMtime() {
        val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
        val currentVersion =
            transaction {
                MangaTable
                    .select(MangaTable.thumbnailUrlVersion)
                    .where { MangaTable.id eq mangaId }
                    .first()[MangaTable.thumbnailUrlVersion]
            }
        val covers =
            listOf(applicationDirs.tempThumbnailCacheRoot, applicationDirs.thumbnailDownloadsRoot).map { root ->
                File(root).mkdirs()
                File(root, "$mangaId.png").apply {
                    writeBytes(PNG_BYTES)
                    setLastModified(currentVersion)
                }
            }

        assertTrue(Manga.deleteStaleThumbnailFiles(listOf(Manga.ThumbnailCleanupTarget(mangaId, legacyVersionZero = true))))

        covers.forEach { assertFalse(it.exists()) }
    }

    @Test
    fun legacyCoverCleanupKeepsAnExactlyTaggedCurrentCover() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[thumbnailUrlVersion] = 0
                }
            }
            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[thumbnailUrlVersion] = 1_700_000_000_123L
                }
            }
            Manga.getMangaThumbnail(mangaId).first.close()
            val published = ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId")

            assertTrue(Manga.deleteStaleThumbnailFiles(listOf(Manga.ThumbnailCleanupTarget(mangaId, legacyVersionZero = true))))

            assertTrue(File(published!!).isFile)
        }

    @Test
    fun coverCleanupMatchesOnlyTheExactMangaFilePrefix() {
        val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
        val roots = listOf(applicationDirs.tempThumbnailCacheRoot, applicationDirs.thumbnailDownloadsRoot)
        val targets =
            roots.map { root ->
                File(root).mkdirs()
                File(root, "$mangaId.png").apply {
                    writeBytes(PNG_BYTES)
                    setLastModified(1L)
                }
            }
        val neighbors =
            roots.flatMap { root ->
                listOf("${mangaId}7.png", "${mangaId}72.png", "999999.png").map {
                    File(root, it).apply {
                        writeBytes(PNG_BYTES)
                        setLastModified(1L)
                    }
                }
            }
        try {
            assertTrue(Manga.deleteStaleThumbnailFiles(listOf(Manga.ThumbnailCleanupTarget(mangaId))))

            targets.forEach { assertFalse(it.exists()) }
            neighbors.forEach { assertTrue(it.exists(), "${it.name} should be kept") }
        } finally {
            neighbors.forEach { it.delete() }
        }
    }

    @Test
    fun aDeletedReuseCandidateFallsBackToPublishingTheStagedCover() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = true)
            Manga.getMangaThumbnail(mangaId).first.close()
            val originalCover = File(downloadedCoverPath(mangaId)!!)
            transaction {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[thumbnailUrlVersion] = MangaTable.thumbnailUrlVersion + 1
                }
            }
            val reuseDecided = CountDownLatch(1)
            val release = CountDownLatch(1)
            val hookUsed = AtomicBoolean(false)
            Manga.thumbnailReuseDecidedForPublish = { id ->
                if (id == mangaId && hookUsed.compareAndSet(false, true)) {
                    reuseDecided.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }

            try {
                val refresh = async { runCatching { Manga.getMangaThumbnail(mangaId) } }
                assertTrue(awaitLatch(reuseDecided), "the refresh never made its reuse decision")
                assertTrue(Manga.deleteStaleThumbnailFiles(listOf(Manga.ThumbnailCleanupTarget(mangaId))))
                assertFalse(originalCover.exists())
                release.countDown()

                val (stream, mime) = refresh.await().getOrThrow()
                assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
                assertEquals("image/png", mime)
                assertContentEquals(PNG_BYTES, File(downloadedCoverPath(mangaId)!!).readBytes())
                assertEquals(2, imageServerHits.get())
                assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId"))
                assertNoStagingLeft()
            } finally {
                release.countDown()
                Manga.thumbnailReuseDecidedForPublish = null
            }
        }

    @Test
    fun clearingTempReapsACopyPublicationBeforeTheDualLockCommit() {
        copyPublicationReapedWhen(clearTemp = true, clearDownloads = false)
    }

    @Test
    fun clearingDownloadsReapsACopyPublicationBeforeTheDualLockCommit() {
        copyPublicationReapedWhen(clearTemp = false, clearDownloads = true)
    }

    private fun copyPublicationReapedWhen(
        clearTemp: Boolean,
        clearDownloads: Boolean,
    ) {
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)
            Manga.getMangaThumbnail(mangaId).first.close()
            val tempCover =
                File(
                    ImageResponse.findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, "$mangaId")!!,
                )
            val untouchedRoot =
                if (clearTemp) {
                    applicationDirs.thumbnailDownloadsRoot
                } else {
                    applicationDirs.tempThumbnailCacheRoot
                }
            File(untouchedRoot).mkdirs()
            val sentinel =
                File(untouchedRoot, "sentinel-$mangaId.png").apply {
                    writeBytes(byteArrayOf(9))
                }
            val staged = CountDownLatch(1)
            val release = CountDownLatch(1)
            val hookUsed = AtomicBoolean(false)
            Manga.thumbnailStagedForPublish = { id ->
                if (id == mangaId && hookUsed.compareAndSet(false, true)) {
                    staged.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            val tempRoot = File(applicationDirs.tempThumbnailCacheRoot)
            val tempRootPermissions = Files.getPosixFilePermissions(tempRoot.toPath())
            var tempRootReadOnly = false

            try {
                val download = async(Dispatchers.IO) { runCatching { Manga.downloadThumbnail(mangaId) } }
                assertTrue(awaitLatch(staged), "the copy never reached the publish barrier")
                if (clearTemp) {
                    Files.setPosixFilePermissions(
                        tempRoot.toPath(),
                        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
                    )
                    tempRootReadOnly = true
                }
                val cleanupResult =
                    try {
                        ImageMutation().clearCachedImages(
                            ImageMutation.ClearCachedImagesInput(downloadedThumbnails = clearDownloads, cachedThumbnails = clearTemp),
                        )
                    } finally {
                        if (tempRootReadOnly) {
                            Files.setPosixFilePermissions(tempRoot.toPath(), tempRootPermissions)
                            tempRootReadOnly = false
                        }
                    }
                if (clearTemp) {
                    assertEquals(false, cleanupResult.cachedThumbnails)
                    assertTrue(tempCover.isFile, "the temp source should survive its failed deletion")
                }
                assertTrue(sentinel.isFile, "the untouched thumbnail directory was cleared")
                release.countDown()

                assertNotNull(download.await().exceptionOrNull())
                assertNull(downloadedCoverPath(mangaId))
                assertTrue(sentinel.isFile, "the untouched thumbnail directory was cleared")
            } finally {
                if (tempRootReadOnly) {
                    Files.setPosixFilePermissions(tempRoot.toPath(), tempRootPermissions)
                }
                sentinel.delete()
                release.countDown()
                Manga.thumbnailStagedForPublish = null
            }
        }
    }

    @Test
    fun aBreakerRejectionIsNotRememberedAsACoverFailure() =
        runBlocking {
            // trip the cover-tier circuit for this server: three poisoned hops convict it
            // without a request ever reaching the handler
            val poisonedClient =
                network
                    .thumbnailClient()
                    .newBuilder()
                    .addNetworkInterceptor { throw IOException("connection reset") }
                    .build()
            val request = Request.Builder().url(imageUrl("/ok.png")).build()
            repeat(3) { assertFailsWith<IOException> { poisonedClient.newCall(request).execute() } }
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = false)

            val rejected = assertFailsWith<IOException> { Manga.getMangaThumbnail(mangaId) }
            assertTrue(rejected.hasOpenCircuit())
            assertEquals(0, imageServerHits.get())

            // the window over: a remembered failure would replay — instead the cover fetches
            network.thumbnailHostBreaker.reset()
            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/png", mime)
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aMissingTempCacheDirStillLetsTheLibraryCoverLand() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = true)
            File(applicationDirs.tempThumbnailCacheRoot).deleteRecursively()

            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/png", mime)
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun aJpegCoverReportsItsRealMime() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.jpg"), inLibrary = false)

            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(JPEG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/jpeg", mime)

            val cached = Manga.getMangaThumbnail(mangaId)
            cached.first.close()
            assertEquals("image/jpeg", cached.second)
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun anSvgCoverReportsSvgXmlNotASuffixGuess() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.svg"), inLibrary = false)

            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(SVG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/svg+xml", mime)

            val cached = Manga.getMangaThumbnail(mangaId)
            cached.first.close()
            assertEquals("image/svg+xml", cached.second)
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun anSvgLibraryCoverKeepsItsMimeThroughTheDownloadCopy() =
        runBlocking {
            val mangaId = createManga(imageUrl("/cover.svg"), inLibrary = true)

            val (stream, mime) = Manga.getMangaThumbnail(mangaId)
            assertContentEquals(SVG_BYTES, stream.use { it.readBytes() })
            assertEquals("image/svg+xml", mime)
            assertEquals(1, imageServerHits.get())

            // the download copy publishes .svg too — the temp file's own extension carries it
            val published =
                ImageResponse.findFileNameStartingWith(applicationDirs.thumbnailDownloadsRoot, "$mangaId")
            assertTrue(published?.endsWith(".svg") == true, "expected a .svg download copy, got $published")
            val cached = Manga.getMangaThumbnail(mangaId)
            cached.first.close()
            assertEquals("image/svg+xml", cached.second)
            assertEquals(1, imageServerHits.get())
        }

    @Test
    fun strayStagingFilesDoNotCountAsDownloadedCovers() =
        runBlocking {
            val mangaId = createManga(imageUrl("/ok.png"), inLibrary = true)
            File(applicationDirs.thumbnailDownloadsRoot).mkdirs()
            val orphan =
                File(applicationDirs.thumbnailDownloadsRoot, "$mangaId.orphan.tmp")
                    .apply { writeBytes(byteArrayOf(1, 2, 3)) }
            try {
                assertFailsWith<MissingThumbnailException> { ThumbnailDownloadHelper.getImage(mangaId) }

                val (stream, _) = Manga.getMangaThumbnail(mangaId)
                assertContentEquals(PNG_BYTES, stream.use { it.readBytes() })
                assertEquals(1, imageServerHits.get())

                assertTrue(ThumbnailDownloadHelper.delete(mangaId))
                assertNull(ImageResponse.findFileNameStartingWith(applicationDirs.thumbnailDownloadsRoot, "$mangaId"))
                // a staged writer's file is not deleted out from under it
                assertTrue(orphan.exists())
            } finally {
                orphan.delete()
            }
        }

    private suspend fun awaitThumbnailRowLockWaiter(cleanup: Deferred<Boolean>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (thumbnailRowLockWaiterCount() == 0) {
            check(!cleanup.isCompleted) { "thumbnail cleanup completed without taking the manga row lock" }
            check(System.nanoTime() < deadline) { "thumbnail cleanup never blocked on the manga row lock" }
            delay(10)
        }
    }

    private fun thumbnailRowLockWaiterCount(): Int {
        val query =
            when (serverConfig.databaseType.value) {
                DatabaseType.H2 -> {
                    """
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS
                    WHERE BLOCKER_ID IS NOT NULL AND UPPER(EXECUTING_STATEMENT) LIKE '%THUMBNAIL_URL_VERSION%'
                    """.trimIndent()
                }

                DatabaseType.POSTGRESQL -> {
                    """
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock' AND upper(query) LIKE '%THUMBNAIL_URL_VERSION%' AND pid <> pg_backend_pid()
                    """.trimIndent()
                }
            }
        return transaction {
            exec(query) {
                it.next()
                it.getInt(1)
            } ?: 0
        }
    }

    // blocking the latch on the runBlocking loop would starve the coroutines it gates
    private suspend fun awaitLatch(latch: CountDownLatch): Boolean = withContext(Dispatchers.IO) { latch.await(5, TimeUnit.SECONDS) }

    private suspend fun awaitLockRefCount(
        mangaId: Int,
        refs: Int,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (Manga.thumbnailLockEntryRefs(mangaId) != refs) {
            check(System.nanoTime() < deadline) { "manga $mangaId never reached $refs lock refs" }
            delay(10)
        }
    }

    private suspend fun Deferred<Result<Pair<InputStream, String>>>.awaitCancelled() {
        val outcome = runCatching { await() }
        assertTrue(
            outcome.getOrNull()?.isFailure == true || outcome.exceptionOrNull() is CancellationException,
            "expected the cancelled request to fail: $outcome",
        )
    }

    private fun downloadedCoverPath(mangaId: Int): String? =
        ImageResponse.findFileNameStartingWith(applicationDirs.thumbnailDownloadsRoot, "$mangaId")

    private fun assertNoStagingLeft() {
        val leftovers =
            listOf(applicationDirs.tempThumbnailCacheRoot, applicationDirs.thumbnailDownloadsRoot)
                .flatMap { File(it).listFiles().orEmpty().toList() }
                .filter { it.name.endsWith(".tmp") }
        assertTrue(leftovers.isEmpty(), "staging files left behind: $leftovers")
    }

    /** Refreshable thumbnail_url answers come from [nextThumbnailUrl] via metadata. */
    private class ThumbnailUrlSource : HttpSource() {
        override val baseUrl = "http://127.0.0.1"
        override val lang = "en"
        override val name = "ThumbnailUrlSource"
        override val supportsLatest = false

        var nextThumbnailUrl: String? = null

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate =
            SMangaUpdate(
                manga.apply { thumbnail_url = nextThumbnailUrl },
                emptyList(),
            )
    }

    private companion object {
        // a real 1x1 PNG: saveImage sniffs the magic bytes for the stored extension
        val PNG_BYTES: ByteArray =
            java.util.Base64
                .getDecoder()
                .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
        val SLOW_BODY_TAIL: ByteArray = ByteArray(64) { 0x11 }
        val SLOW_BODY_BYTES: ByteArray = PNG_BYTES + SLOW_BODY_TAIL

        // the sniffer only needs the JPEG magic + a JFIF header start
        val JPEG_BYTES: ByteArray =
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46)
        val SVG_BYTES: ByteArray = """<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>""".toByteArray()
    }
}
