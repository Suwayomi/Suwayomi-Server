package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThumbnailFileProviderTest : ApplicationTest() {
    private val applicationDirs: ApplicationDirs by injectLazy()

    private val mangaId = 987654
    private val oldCover = "old-cover".toByteArray()
    private val newCover = "new-cover".toByteArray()

    private fun downloadedCover(lastModified: Long): File {
        File(applicationDirs.thumbnailDownloadsRoot).mkdirs()
        return File(applicationDirs.thumbnailDownloadsRoot, "$mangaId.jpg").apply {
            writeBytes(oldCover)
            setLastModified(lastModified)
        }
    }

    private fun sourceResponse(body: ByteArray): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.com/cover.jpg").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", "image/jpeg")
            .body(body.toResponseBody("image/jpeg".toMediaType()))
            .build()

    @Test
    fun refreshIfStaleReplacesTheDownloadedCoverWhenItIsStaleAndTheSourceReturnsANewImage() =
        runBlocking {
            val cover = downloadedCover(lastModified = 1_000L)
            mockkObject(Manga)
            coEvery { Manga.fetchMangaThumbnailResponse(mangaId) } returns sourceResponse(newCover)

            ThumbnailFileProvider(mangaId).refreshIfStale(staleBefore = 5_000L)

            coVerify(exactly = 1) { Manga.fetchMangaThumbnailResponse(mangaId) }
            assertTrue(cover.readBytes().contentEquals(newCover))
        }

    @Test
    fun refreshIfStaleKeepsTheDownloadedCoverWhenTheSourceFails() =
        runBlocking {
            val cover = downloadedCover(lastModified = 1_000L)
            mockkObject(Manga)
            coEvery { Manga.fetchMangaThumbnailResponse(mangaId) } throws IOException("source unavailable")

            ThumbnailFileProvider(mangaId).refreshIfStale(staleBefore = 5_000L)

            coVerify(exactly = 1) { Manga.fetchMangaThumbnailResponse(mangaId) }
            assertTrue(cover.readBytes().contentEquals(oldCover))
            assertEquals(1_000L, cover.lastModified())
        }

    @Test
    fun refreshIfStaleDoesNotFetchWhenTheDownloadedCoverIsNotStale() =
        runBlocking {
            val cover = downloadedCover(lastModified = 9_000L)
            mockkObject(Manga)

            ThumbnailFileProvider(mangaId).refreshIfStale(staleBefore = 5_000L)

            coVerify(exactly = 0) { Manga.fetchMangaThumbnailResponse(any()) }
            assertTrue(cover.readBytes().contentEquals(oldCover))
        }

    @Test
    fun refreshIfStaleDoesNothingWhenThereIsNoDownloadedCover() =
        runBlocking {
            mockkObject(Manga)

            ThumbnailFileProvider(mangaId).refreshIfStale(staleBefore = 5_000L)

            coVerify(exactly = 0) { Manga.fetchMangaThumbnailResponse(any()) }
            assertTrue(File(applicationDirs.thumbnailDownloadsRoot).listFiles().orEmpty().none { it.name.startsWith("$mangaId.") })
        }

    @AfterEach
    internal fun tearDown() {
        unmockkObject(Manga)
        File(applicationDirs.thumbnailDownloadsRoot).deleteRecursively()
    }
}
