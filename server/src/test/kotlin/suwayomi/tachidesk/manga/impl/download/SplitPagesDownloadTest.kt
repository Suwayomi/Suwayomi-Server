package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.FolderProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.awt.image.BufferedImage
import java.io.File
import java.util.Date
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Downloading a chapter with tall image splitting on gives it more pages than its source, so every
 * page index stored for it has to follow when it switches between the two.
 */
class SplitPagesDownloadTest : ApplicationTest() {
    /** Serves nothing: the pages are already cached, so downloading them must not reach the network. */
    private class CachedPagesSource : HttpSource() {
        override val id = SOURCE_ID
        override val name = "Split pages"
        override val lang = "en"
        override val baseUrl = "http://localhost"
        override val supportsLatest = false
    }

    private val originalSplitTallImages = serverConfig.splitTallImages.value
    private var chapterDirs = emptyList<File>()

    @Test
    fun pageIndicesFollowTheChapterBetweenItsSourceAndDownloadedPages() {
        val (mangaId, chapterId) = createChapter()
        runBlocking {
            val (cacheDir, downloadDir) = clearChapterDirs(mangaId, chapterId)
            // a live read of the first two pages, the first one 10 times taller than wide
            cacheDir.mkdirs()
            writePng(File(cacheDir, "001.png"), height = 1000)
            writePng(File(cacheDir, "002.png"), height = 100)
            // the reader stopped on the second page
            setLastPageRead(chapterId, 1)

            download(mangaId, chapterId)

            assertEquals(
                listOf("001.001", "001.002", "001.003", "002"),
                // without extensions, which download conversions other tests set may change
                downloadDir
                    .listFiles()
                    .orEmpty()
                    .map { it.nameWithoutExtension }
                    .filter { it != "ComicInfo" }
                    .sorted(),
            )
            assertEquals(4, pageCount(chapterId))
            assertEquals(3, lastPageRead(chapterId), "the second page is now the fourth one")

            // backups exchange the source page every device knows, and restore it as the downloaded page
            markDownloaded(chapterId)
            val backupManga = BackupMangaHandler.backup(USER_ID, backupFlags).single()
            assertEquals(1, backupManga.chapters.single().lastPageRead)
            setLastPageRead(chapterId, 0)
            val errors = mutableListOf<Pair<Date, String>>()
            BackupMangaHandler.restore(USER_ID, backupManga, emptyMap(), emptyMap(), errors, backupFlags)
            assertEquals(emptyList(), errors)
            assertEquals(3, lastPageRead(chapterId))

            assertTrue(ChapterDownloadHelper.delete(mangaId, chapterId))

            assertEquals(2, pageCount(chapterId))
            assertEquals(1, lastPageRead(chapterId), "back on the second source page")
        }
    }

    @Test
    fun aSplitPageLeftByAnInterruptedDownloadIsNotProcessedAgain() {
        val (mangaId, chapterId) = createChapter(pageCount = 1)
        runBlocking {
            val (cacheDir, downloadDir) = clearChapterDirs(mangaId, chapterId)
            // split before a restart, without the processed marker
            cacheDir.mkdirs()
            writePng(File(cacheDir, "001.001.png"), height = 100)
            writePng(File(cacheDir, "001.002.png"), height = 100)

            download(mangaId, chapterId, pageCount = 1)

            // processing it again would serve one of its parts as the page, or fetch the page again: both make one page more
            assertEquals(
                listOf("001.001", "001.002"),
                downloadDir
                    .listFiles()
                    .orEmpty()
                    .map { it.nameWithoutExtension }
                    .filter { it.startsWith("001.") }
                    .sorted(),
            )
        }
    }

    private val backupFlags =
        BackupFlags(
            includeManga = true,
            includeCategories = false,
            includeChapters = true,
            includeTracking = false,
            includeHistory = false,
            includeClientData = false,
            includeServerSettings = false,
            includeUserSettings = false,
        )

    private suspend fun clearChapterDirs(
        mangaId: Int,
        chapterId: Int,
    ): Pair<File, File> {
        // both folders are named after the manga and source, so clear what an earlier run left
        val cacheDir = File(getChapterCachePath(mangaId, chapterId)).apply { deleteRecursively() }
        val downloadDir = File(getChapterDownloadPath(mangaId, chapterId)).apply { deleteRecursively() }
        chapterDirs = listOf(cacheDir, downloadDir)
        return cacheDir to downloadDir
    }

    private suspend fun download(
        mangaId: Int,
        chapterId: Int,
        pageCount: Int = 2,
    ) {
        serverConfig.splitTallImages.value = true
        val downloaded =
            coroutineScope {
                FolderProvider(mangaId, chapterId)
                    .download()
                    .execute(DownloadQueueItem(chapterId, 1, mangaId, SOURCE_ID, pageCount = pageCount), this) { _, _ -> }
            }
        assertEquals(true, downloaded)
    }

    private fun writePng(
        file: File,
        height: Int,
    ) {
        ImageIO.write(BufferedImage(100, height, BufferedImage.TYPE_INT_RGB), "png", file)
    }

    private fun createChapter(pageCount: Int = 2): Pair<Int, Int> {
        // the cache and download folders are named after the source
        GetSource.registerSource(SOURCE_ID to CachedPagesSource())
        return transaction {
            val mangaId =
                MangaTable
                    .insertAndGetId {
                        it[title] = "Split"
                        it[url] = "split"
                        it[sourceReference] = SOURCE_ID
                    }.value
            MangaUserTable.insert {
                it[manga] = mangaId
                it[user] = USER_ID
                it[inLibrary] = true
            }
            val chapterId =
                ChapterTable
                    .insertAndGetId {
                        it[url] = "split-1"
                        it[name] = "1"
                        it[sourceOrder] = 1
                        it[manga] = mangaId
                        it[ChapterTable.pageCount] = pageCount
                        it[memo] = JsonObject.EMPTY
                    }.value
            ChapterUserTable.insert {
                it[chapter] = chapterId
                it[user] = USER_ID
            }
            repeat(pageCount) { index ->
                PageTable.insert {
                    it[PageTable.index] = index
                    it[url] = "$index"
                    it[imageUrl] = "http://localhost/$index.png"
                    it[chapter] = chapterId
                }
            }
            mangaId to chapterId
        }
    }

    private fun setLastPageRead(
        chapterId: Int,
        page: Int,
    ) {
        transaction {
            ChapterUserTable.update({ ChapterUserTable.chapter eq chapterId }) { it[lastPageRead] = page }
        }
    }

    /** What the downloader does once the download is finished */
    private fun markDownloaded(chapterId: Int) {
        transaction {
            ChapterTable.update({ ChapterTable.id eq chapterId }) { it[isDownloaded] = true }
        }
    }

    private fun lastPageRead(chapterId: Int): Int =
        transaction {
            ChapterUserTable.selectAll().where { ChapterUserTable.chapter eq chapterId }.single()[ChapterUserTable.lastPageRead]
        }

    private fun pageCount(chapterId: Int): Int =
        transaction {
            ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.single()[ChapterTable.pageCount]
        }

    @AfterEach
    internal fun tearDown() {
        chapterDirs.forEach { it.deleteRecursively() }
        serverConfig.splitTallImages.value = originalSplitTallImages
        GetSource.unregisterSource(SOURCE_ID)
        clearTables(PageTable, ChapterTable, MangaTable)
    }

    private companion object {
        const val SOURCE_ID = 3_000_001L
        const val USER_ID = 1
    }
}
