package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThumbnailDownloadHelperTest : ApplicationTest() {
    private val applicationDirs: ApplicationDirs by injectLazy()

    private val oldCover = "old-cover".toByteArray()
    private val newCover = "new-cover".toByteArray()

    private fun downloadedCover(mangaId: Int): File {
        File(applicationDirs.thumbnailDownloadsRoot).mkdirs()
        return File(applicationDirs.thumbnailDownloadsRoot, "$mangaId.jpg").apply {
            writeBytes(oldCover)
            setLastModified(1_000L)
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
    fun refreshInBackgroundReplacesAChangedCoverAndBumpsItsVersion() {
        val mangaId = 876541
        val cover = downloadedCover(mangaId)
        mockkObject(Manga)
        coEvery { Manga.fetchMangaThumbnailResponse(mangaId) } returns sourceResponse(newCover)
        every { Manga.markThumbnailChanged(mangaId) } just Runs

        ThumbnailDownloadHelper.refreshInBackground(mangaId, staleBefore = 5_000L)

        verify(timeout = 5_000, exactly = 1) { Manga.markThumbnailChanged(mangaId) }
        assertTrue(cover.readBytes().contentEquals(newCover))
    }

    @Test
    fun refreshInBackgroundKeepsAnIdenticalCoverAndItsVersion() {
        val mangaId = 876542
        val cover = downloadedCover(mangaId)
        mockkObject(Manga)
        coEvery { Manga.fetchMangaThumbnailResponse(mangaId) } returns sourceResponse(oldCover)
        every { Manga.markThumbnailChanged(mangaId) } just Runs

        ThumbnailDownloadHelper.refreshInBackground(mangaId, staleBefore = 5_000L)

        // an identical image is only marked as checked
        waitUntil { cover.lastModified() > 5_000L }
        verify(exactly = 0) { Manga.markThumbnailChanged(any()) }
        assertTrue(cover.readBytes().contentEquals(oldCover))
    }

    @Test
    fun aCheckThatRefreshesTheMangaDetailsDoesNotStartAnother() {
        val mangaId = 876543
        downloadedCover(mangaId)
        mockkObject(Manga)
        // a dead cover url makes the fetch refresh the manga details, which schedules a check of the same cover
        coEvery { Manga.fetchMangaThumbnailResponse(mangaId) } answers {
            ThumbnailDownloadHelper.refreshInBackground(mangaId, staleBefore = 5_000L)
            sourceResponse(newCover)
        }
        every { Manga.markThumbnailChanged(mangaId) } just Runs

        ThumbnailDownloadHelper.refreshInBackground(mangaId, staleBefore = 5_000L)

        verify(timeout = 5_000, exactly = 1) { Manga.markThumbnailChanged(mangaId) }
        Thread.sleep(200)
        coVerify(exactly = 1) { Manga.fetchMangaThumbnailResponse(mangaId) }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(20)
        }
    }

    @AfterEach
    internal fun tearDown() {
        unmockkObject(Manga)
        File(applicationDirs.thumbnailDownloadsRoot).deleteRecursively()
    }
}
