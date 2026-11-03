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
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
            assertEquals("image/jpg", mime)
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

            assertEquals("image/jpg", mime)
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
}
