package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.FolderProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.storage.PageCacheCoordinator
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A page a live read left in the chapter's cache was never post-processed, so downloading the
 * chapter must still run it through the download conversions instead of skipping it.
 */
class LiveReadCachedPageDownloadTest : ApplicationTest() {
    /** Serves nothing: the page is already cached, so downloading it must not reach the network. */
    private class CachedPagesSource : HttpSource() {
        override val id = SOURCE_ID
        override val name = "Cached pages"
        override val lang = "en"
        override val baseUrl = "http://localhost"
        override val supportsLatest = false
    }

    private val originalConversions = serverConfig.downloadConversions.value
    private var chapterDirs = emptyList<File>()

    @Test
    fun aPageCachedByALiveReadIsConvertedWhenDownloaded() {
        assertEquals(listOf("001.jpg"), downloadWithCachedPage(alreadyProcessed = false))
    }

    @Test
    fun aPageAlreadyProcessedBeforeARestartIsNotProcessedAgain() {
        // the marker is on disk, so a download resumed after a restart still knows the page is done
        // and doesn't run it through the conversions (e.g. an HTTP upscaler) a second time
        assertEquals(listOf("001.png"), downloadWithCachedPage(alreadyProcessed = true))
    }

    /** Downloads a chapter whose only page is already cached as a PNG, with a PNG to JPEG conversion, and returns its pages */
    private fun downloadWithCachedPage(alreadyProcessed: Boolean): List<String> {
        GetSource.registerSource(SOURCE_ID to CachedPagesSource())
        serverConfig.downloadConversions.value = mapOf("image/png" to DownloadConversion(target = "image/jpeg"))

        val (mangaId, chapterId) = createChapterWithOnePage()
        return runBlocking {
            // both folders are named after the manga and source, so clear what an earlier run left
            val cacheDir = File(getChapterCachePath(mangaId, chapterId)).apply { deleteRecursively() }
            val downloadDir = File(getChapterDownloadPath(mangaId, chapterId)).apply { deleteRecursively() }
            chapterDirs = listOf(cacheDir, downloadDir)

            // what a live read of the not yet downloaded chapter leaves behind
            cacheDir.mkdirs()
            ImageIO.write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", File(cacheDir, "001.png"))
            if (alreadyProcessed) {
                PageCacheCoordinator.markProcessed(cacheDir.path, "001")
            }

            val downloaded =
                FolderProvider(mangaId, chapterId)
                    .download()
                    .execute(DownloadQueueItem(chapterId, 1, mangaId, SOURCE_ID, pageCount = 1), this) { _, _ -> }

            assertEquals(true, downloaded)
            val files = downloadDir.listFiles().orEmpty().map { it.name }
            assertFalse(PageCacheCoordinator.PROCESSED_MARKERS_DIR in files, "the processed markers must not be downloaded")
            files.filter { it.startsWith("001.") }
        }
    }

    private fun createChapterWithOnePage(): Pair<Int, Int> =
        transaction {
            val mangaId =
                MangaTable
                    .insertAndGetId {
                        it[title] = "Cached"
                        it[url] = "cached"
                        it[sourceReference] = SOURCE_ID
                    }.value
            val chapterId =
                ChapterTable
                    .insertAndGetId {
                        it[url] = "cached-1"
                        it[name] = "1"
                        it[sourceOrder] = 1
                        it[manga] = mangaId
                        it[pageCount] = 1
                        it[memo] = JsonObject.EMPTY
                    }.value
            PageTable.insert {
                it[index] = 0
                it[url] = "0"
                it[imageUrl] = "http://localhost/0.png"
                it[chapter] = chapterId
            }
            mangaId to chapterId
        }

    @AfterEach
    internal fun tearDown() {
        chapterDirs.forEach { it.deleteRecursively() }
        serverConfig.downloadConversions.value = originalConversions
        GetSource.unregisterSource(SOURCE_ID)
        clearTables(PageTable, ChapterTable, MangaTable)
    }

    private companion object {
        const val SOURCE_ID = 3_000_000L
    }
}
