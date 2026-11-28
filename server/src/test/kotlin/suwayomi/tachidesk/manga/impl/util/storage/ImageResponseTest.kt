package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.io.TempDir
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearImages
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.findFileNameStartingWith
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getCachedImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.saveImage
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageResponseTest {
    @TempDir
    lateinit var tempDir: File

    private val fileName = "1"
    private val oldImage = "old-image".toByteArray()
    private val newImage = "new-image".toByteArray()

    private val saveDir: String
        get() = tempDir.absolutePath

    private fun response(
        code: Int = 200,
        body: ByteArray = newImage,
        contentType: String = "image/jpeg",
    ): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.com/cover").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("message")
            .header("Content-Type", contentType)
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()

    private fun cachedFile(
        extension: String = "jpg",
        content: ByteArray = oldImage,
        lastModified: Long = 1_000L,
    ): File =
        File(tempDir, "$fileName.$extension").apply {
            writeBytes(content)
            setLastModified(lastModified)
        }

    private fun filesInDir(): List<String> =
        tempDir
            .listFiles()
            .orEmpty()
            .map { it.name }
            .sorted()

    @Test
    fun fetchesAndSavesImageWhenNothingIsCached() =
        runBlocking {
            var fetchCount = 0

            val (stream, mime) =
                ImageResponse.getImageResponse(saveDir, fileName) {
                    fetchCount++
                    response()
                }

            assertEquals(1, fetchCount)
            assertEquals("image/jpeg", mime)
            assertTrue(stream.use { it.readBytes() }.contentEquals(newImage))
            assertEquals(listOf("1.jpg"), filesInDir())
        }

    @Test
    fun returnsCachedImageWithoutFetchingWhenItIsNotStale() =
        runBlocking {
            cachedFile(lastModified = 5_000L)
            var fetchCount = 0

            val (stream, mime) =
                ImageResponse.getImageResponse(saveDir, fileName, staleBefore = 5_000L) {
                    fetchCount++
                    response()
                }

            assertEquals(0, fetchCount)
            assertEquals("image/jpeg", mime)
            assertTrue(stream.use { it.readBytes() }.contentEquals(oldImage))
        }

    @Test
    fun returnsCachedImageWithoutFetchingWhenStaleBeforeIsNotProvided() =
        runBlocking {
            cachedFile(lastModified = 1L)
            var fetchCount = 0

            val (stream, _) =
                ImageResponse.getImageResponse(saveDir, fileName) {
                    fetchCount++
                    response()
                }

            assertEquals(0, fetchCount)
            assertTrue(stream.use { it.readBytes() }.contentEquals(oldImage))
        }

    @Test
    fun replacesStaleCachedImageWhenTheNewImageDiffers() =
        runBlocking {
            cachedFile(lastModified = 1_000L)
            var fetchCount = 0

            val (stream, mime) =
                ImageResponse.getImageResponse(saveDir, fileName, staleBefore = 5_000L) {
                    fetchCount++
                    response()
                }

            assertEquals(1, fetchCount)
            assertEquals("image/jpeg", mime)
            assertTrue(stream.use { it.readBytes() }.contentEquals(newImage))
            assertEquals(listOf("1.jpg"), filesInDir())
            assertTrue(File(tempDir, "1.jpg").readBytes().contentEquals(newImage))
            assertTrue(File(tempDir, "1.jpg").lastModified() >= 5_000L)
        }

    @Test
    fun removesTheOldFileWhenTheNewImageHasADifferentExtension() =
        runBlocking {
            cachedFile(extension = "jpg", lastModified = 1_000L)

            val (stream, mime) =
                ImageResponse.getImageResponse(saveDir, fileName, staleBefore = 5_000L) {
                    response(contentType = "image/png")
                }

            assertEquals("image/png", mime)
            assertTrue(stream.use { it.readBytes() }.contentEquals(newImage))
            assertEquals(listOf("1.png"), filesInDir())
        }

    @Test
    fun keepsTheCachedFileAndOnlyRefreshesItsTimestampWhenTheNewImageIsIdentical() =
        runBlocking {
            cachedFile(lastModified = 1_000L)

            val (stream, _) =
                ImageResponse.getImageResponse(saveDir, fileName, staleBefore = 5_000L) {
                    response(body = oldImage)
                }

            assertTrue(stream.use { it.readBytes() }.contentEquals(oldImage))
            assertEquals(listOf("1.jpg"), filesInDir())
            assertTrue(File(tempDir, "1.jpg").lastModified() >= 5_000L)
        }

    @Test
    fun keepsTheStaleCachedImageWhenTheFetcherThrows() =
        runBlocking {
            val cached = cachedFile(lastModified = 1_000L)

            val (stream, mime) =
                ImageResponse.getImageResponse(saveDir, fileName, staleBefore = 5_000L) {
                    throw IOException("source unavailable")
                }

            assertEquals("image/jpeg", mime)
            assertTrue(stream.use { it.readBytes() }.contentEquals(oldImage))
            assertEquals(listOf("1.jpg"), filesInDir())
            assertEquals(1_000L, cached.lastModified())
        }

    @Test
    fun keepsTheStaleCachedImageWhenTheResponseIsNotSuccessful() =
        runBlocking {
            val cached = cachedFile(lastModified = 1_000L)

            val (stream, _) =
                ImageResponse.getImageResponse(saveDir, fileName, staleBefore = 5_000L) {
                    response(code = 404)
                }

            assertTrue(stream.use { it.readBytes() }.contentEquals(oldImage))
            assertEquals(listOf("1.jpg"), filesInDir())
            assertEquals(1_000L, cached.lastModified())
        }

    @Test
    fun propagatesTheFailureWhenNothingIsCached() {
        val exception =
            assertFailsWith<IOException> {
                runBlocking {
                    ImageResponse.getImageResponse(saveDir, fileName, staleBefore = 5_000L) {
                        throw IOException("source unavailable")
                    }
                }
            }

        assertEquals("source unavailable", exception.message)
        assertTrue(filesInDir().isEmpty())
    }

    @Test
    fun propagatesNonSuccessfulResponsesWhenNothingIsCached() {
        val exception =
            assertFailsWith<Exception> {
                runBlocking {
                    ImageResponse.getImageResponse(saveDir, fileName) {
                        response(code = 500)
                    }
                }
            }

        assertEquals("request error! 500", exception.message)
        assertTrue(filesInDir().isEmpty())
    }

    @Test
    fun downloadsAgainWhenOnlyAPartialTmpFileExists() =
        runBlocking {
            File(tempDir, "1.tmp").writeBytes("partial".toByteArray())

            val (stream, _) = ImageResponse.getImageResponse(saveDir, fileName) { response() }

            assertTrue(stream.use { it.readBytes() }.contentEquals(newImage))
            assertFalse(File(tempDir, "1.tmp").exists())
            assertEquals(listOf("1.jpg"), filesInDir())
        }

    @Test
    fun saveImageWritesTheImageWhenNoFileExists() {
        val (savedPath, mime) =
            ImageResponse.saveImage("$saveDir/$fileName", newImage.inputStream(), "image/jpeg")

        assertEquals("$saveDir/$fileName.jpg", savedPath)
        assertEquals("image/jpeg", mime)
        assertTrue(File(savedPath).readBytes().contentEquals(newImage))
        assertEquals(listOf("1.jpg"), filesInDir())
    }

    @Test
    fun saveImageReplacesAnExistingFileWithTheSameExtension() {
        cachedFile()

        val (savedPath, mime) =
            ImageResponse.saveImage("$saveDir/$fileName", newImage.inputStream(), "image/jpeg")

        assertEquals("$saveDir/$fileName.jpg", savedPath)
        assertEquals("image/jpeg", mime)
        assertTrue(File(savedPath).readBytes().contentEquals(newImage))
        assertFalse(File(tempDir, "1.tmp").exists())
        assertEquals(listOf("1.jpg"), filesInDir())
    }

    @Test
    fun anUnsupportedAtomicReplaceKeepsTheOldImageAndReportsFailure() {
        cachedFile()
        val originalMove = ImageResponse.fileMove
        val atomicAttempts = AtomicInteger()
        val fallbackAttempts = AtomicInteger()
        try {
            ImageResponse.fileMove = { source, target, options ->
                if (options.any { it == StandardCopyOption.ATOMIC_MOVE }) {
                    atomicAttempts.incrementAndGet()
                    throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "no atomic replace")
                }
                fallbackAttempts.incrementAndGet()
                Files.move(source, target, *options)
            }

            assertFailsWith<IOException> { saveImage("$tempDir/$fileName", newImage.inputStream(), "image/jpeg") }
        } finally {
            ImageResponse.fileMove = originalMove
        }

        // the existing target forbids the non-atomic fallback — it is never even attempted
        assertEquals(1, atomicAttempts.get())
        assertEquals(0, fallbackAttempts.get())
        assertContentEquals(oldImage, File(tempDir, "1.jpg").readBytes())
        assertEquals(listOf("1.jpg"), filesInDir())
    }

    @Test
    fun aRefusedAtomicReplaceKeepsTheOldImageAndReportsFailure() {
        cachedFile()
        val originalMove = ImageResponse.fileMove
        val atomicAttempts = AtomicInteger()
        val fallbackAttempts = AtomicInteger()
        try {
            ImageResponse.fileMove = { source, target, options ->
                if (options.any { it == StandardCopyOption.ATOMIC_MOVE }) {
                    atomicAttempts.incrementAndGet()
                    throw FileAlreadyExistsException(target.toString())
                }
                fallbackAttempts.incrementAndGet()
                Files.move(source, target, *options)
            }

            assertFailsWith<IOException> { saveImage("$tempDir/$fileName", newImage.inputStream(), "image/jpeg") }
        } finally {
            ImageResponse.fileMove = originalMove
        }

        assertEquals(1, atomicAttempts.get())
        assertEquals(0, fallbackAttempts.get())
        assertContentEquals(oldImage, File(tempDir, "1.jpg").readBytes())
        assertEquals(listOf("1.jpg"), filesInDir())
    }

    @Test
    fun anUnsupportedAtomicMoveStillPublishesWhenThereIsNoOldImage() {
        val originalMove = ImageResponse.fileMove
        val fallbackOptions = mutableListOf<List<CopyOption>>()
        try {
            ImageResponse.fileMove = { source, target, options ->
                if (options.any { it == StandardCopyOption.ATOMIC_MOVE }) {
                    throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "no atomic move")
                }
                fallbackOptions += options.toList()
                Files
                    .move(source, target, *options)
            }

            val (savedPath, _) = saveImage("$tempDir/19", newImage.inputStream(), "image/jpeg")
            assertContentEquals(newImage, File(savedPath).readBytes())
        } finally {
            ImageResponse.fileMove = originalMove
        }

        // the fallback must not carry REPLACE_EXISTING — a competing publish wins, never gets clobbered
        assertEquals(1, fallbackOptions.size)
        assertFalse(fallbackOptions.single().any { it == StandardCopyOption.REPLACE_EXISTING })
    }

    @Test
    fun stagingFilesNeverCountAsSavedImages() {
        File(tempDir, "7.leftover.tmp").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(findFileNameStartingWith(tempDir.path, "7"))
    }

    @Test
    fun aPublishedFileWinsOverStrayStaging() {
        File(tempDir, "7.png").writeBytes(PNG_BYTES)
        File(tempDir, "7.stale.tmp").writeBytes(byteArrayOf(9))
        assertEquals("${tempDir.path}/7.png", findFileNameStartingWith(tempDir.path, "7"))
    }

    @Test
    fun aFailedSaveCleansOnlyItsOwnStaging() {
        val slowGate = CountDownLatch(1)
        val slowStream =
            object : InputStream() {
                private var sent = 0

                override fun read(): Int =
                    if (sent < PNG_BYTES.size) {
                        PNG_BYTES[sent++].toInt() and 0xFF
                    } else {
                        slowGate.await(10, TimeUnit.SECONDS)
                        -1
                    }
            }
        val slowWriter =
            thread {
                runCatching { saveImage("$tempDir/7", slowStream, "image/png") }
            }
        waitUntil { tempDir.list()!!.count { it.endsWith(".tmp") } == 1 }

        assertFailsWith<UpstreamReadException> {
            saveImage("$tempDir/7", failAfter(4), null)
        }

        // the failed save reaped its own file — the slow writer's staging is untouched
        assertEquals(1, tempDir.list()!!.count { it.endsWith(".tmp") })

        slowGate.countDown()
        slowWriter.join(5_000)
        assertContentEquals(PNG_BYTES, File(tempDir, "7.png").readBytes())
        assertEquals(0, tempDir.list()!!.count { it.endsWith(".tmp") })
    }

    @Test
    fun aFailedPublishLeavesNoFileAndNoStaging() {
        // a directory already holds the publish name — the atomic move cannot land
        File(tempDir, "8.png").mkdirs()
        assertFailsWith<IOException> { saveImage("$tempDir/8", PNG_BYTES.inputStream(), "image/png") }
        assertNull(findFileNameStartingWith(tempDir.path, "8"))
        assertEquals(0, tempDir.list()!!.count { it.endsWith(".tmp") })
    }

    @Test
    fun aFailedStagingStillClosesTheSourceStream() {
        val source = CloseCountingStream(PNG_BYTES.inputStream())
        // the staging parent does not exist — createTempFile fails before any copy
        assertFailsWith<IOException> { saveImage("$tempDir/missing/15", source, "image/png") }
        assertEquals(1, source.closes.get())
    }

    @Test
    fun aCloseFailurePropagatesWithoutPublishing() {
        val unclosable =
            object : FilterInputStream(PNG_BYTES.inputStream()) {
                override fun close(): Unit = throw IOException("close blew up")
            }
        val failure = assertFailsWith<IOException> { saveImage("$tempDir/16", unclosable, "image/png") }
        assertEquals("close blew up", failure.message)
        assertNull(findFileNameStartingWith(tempDir.path, "16"))
        assertEquals(0, tempDir.walkTopDown().count { it.name.endsWith(".tmp") })
    }

    @Test
    fun concurrentPublishesNeverSpliceTheFile() {
        val alt = PNG_BYTES + byteArrayOf(0x55)
        val results = java.util.concurrent.ConcurrentLinkedQueue<Result<Pair<String, String>>>()
        val originalMove = ImageResponse.fileMove
        val bothAtMove = CountDownLatch(2)
        val releaseMoves = CountDownLatch(1)
        ImageResponse.fileMove = { source, target, options ->
            bothAtMove.countDown()
            releaseMoves.await(5, TimeUnit.SECONDS)
            Files.move(source, target, *options)
        }
        try {
            val a = thread { results += runCatching { saveImage("$tempDir/9", PNG_BYTES.inputStream(), "image/png") } }
            val b = thread { results += runCatching { saveImage("$tempDir/9", alt.inputStream(), "image/png") } }
            assertTrue(bothAtMove.await(5, TimeUnit.SECONDS), "the publishes never overlapped at the move")
            releaseMoves.countDown()
            a.join()
            b.join()
            results.forEach { it.getOrThrow() }
        } finally {
            ImageResponse.fileMove = originalMove
        }

        val landed = File(tempDir, "9.png").readBytes()
        assertTrue(landed.contentEquals(PNG_BYTES) || landed.contentEquals(alt), "spliced or partial publish")
        assertEquals(0, tempDir.list()!!.count { it.endsWith(".tmp") })
    }

    @Test
    fun bodyReadFailuresAreMarkedAsUpstream() {
        val failure =
            assertFailsWith<IOException> {
                saveImage("$tempDir/10", failAfter(4), null)
            }
        assertIs<UpstreamReadException>(failure)
        assertEquals(0, tempDir.list()!!.count { it.endsWith(".tmp") })
    }

    @Test
    fun aCachedFileDerivesItsMimeFromThePublishedExtension() {
        File(tempDir, "12.jpg").writeBytes(PNG_BYTES)
        File(tempDir, "13.svg").writeBytes("<svg/>".toByteArray())
        File(tempDir, "14.jpeg").writeBytes(PNG_BYTES)

        assertEquals("image/jpeg", cachedMime("$tempDir/12.jpg", "$tempDir/12"))
        assertEquals("image/svg+xml", cachedMime("$tempDir/13.svg", "$tempDir/13"))
        assertEquals("image/jpeg", cachedMime("$tempDir/14.jpeg", "$tempDir/14"))
    }

    @Test
    fun bulkClearSkipsStagingFilesAndDirectories() {
        File(tempDir, "11.png").writeBytes(PNG_BYTES)
        val nested = File(tempDir, "nested").apply { mkdirs() }
        File(nested, "11.jpeg").writeBytes(PNG_BYTES)
        val staging = File(nested, "11.live.tmp").apply { writeBytes(byteArrayOf(7)) }

        assertTrue(clearImages(tempDir.path))
        assertNull(findFileNameStartingWith(tempDir.path, "11"))
        assertTrue(staging.exists())
        assertTrue(nested.isDirectory)
    }

    private fun cachedMime(
        cachedFile: String,
        filePath: String,
    ): String =
        getCachedImageResponse(cachedFile, filePath).let { (stream, mime) ->
            stream.use { mime }
        }

    private class CloseCountingStream(
        source: InputStream,
    ) : FilterInputStream(source) {
        val closes = AtomicInteger()

        override fun close() {
            closes.incrementAndGet()
            super.close()
        }
    }

    private fun failAfter(bytes: Int): InputStream =
        object : InputStream() {
            private var sent = 0

            override fun read(): Int =
                if (sent++ < bytes) {
                    PNG_BYTES[sent - 1].toInt() and 0xFF
                } else {
                    throw IOException("connection reset")
                }
        }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!predicate()) {
            check(System.nanoTime() < deadline) { "timed out waiting for staging file" }
            Thread.sleep(10)
        }
    }

    private companion object {
        val PNG_BYTES: ByteArray =
            java.util.Base64
                .getDecoder()
                .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
    }
}
