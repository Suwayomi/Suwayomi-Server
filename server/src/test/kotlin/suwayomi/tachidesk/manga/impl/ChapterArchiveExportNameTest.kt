package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.createLibraryManga
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChapterArchiveExportNameTest : ApplicationTest() {
    @Test
    fun existingCbzSuffixIsNotRepeatedInExportName() =
        runTest {
            assertExportName("Volume 1.cbz", expectedChapterName = "Volume 1")
        }

    @Test
    fun existingCbzSuffixIsRecognizedWithoutCaseSensitivity() =
        runTest {
            assertExportName("Volume 2.CBZ", expectedChapterName = "Volume 2")
        }

    @Test
    fun ordinaryChapterNameKeepsItsText() =
        runTest {
            assertExportName("Chapter 3", expectedChapterName = "Chapter 3")
        }

    @Test
    fun scanlatorFollowsChapterNameWithoutArchiveSuffix() =
        runTest {
            assertExportName("Volume 4.cbz", scanlator = "Team", expectedChapterName = "Volume 4 [Team]")
        }

    @Test
    fun blankChapterNameFallsBackToChapterNumber() =
        runTest {
            assertExportName(" ", chapterNumber = 5f, expectedChapterName = "#5")
        }

    @Test
    fun blankChapterNameWithoutChapterNumberFallsBackToIndex() =
        runTest {
            assertExportName("", chapterNumber = -1f, expectedChapterName = "#7")
        }

    private suspend fun assertExportName(
        chapterName: String,
        scanlator: String? = null,
        chapterNumber: Float = 1f,
        expectedChapterName: String,
    ) {
        val mangaTitle = "CBZ export naming"
        val mangaId = createLibraryManga(mangaTitle)
        var storedFile: File? = null

        try {
            val chapterId =
                transaction {
                    ChapterTable
                        .insertAndGetId {
                            it[url] = "archive-export"
                            it[name] = chapterName
                            it[ChapterTable.scanlator] = scanlator
                            it[chapter_number] = chapterNumber
                            it[sourceOrder] = 7
                            it[manga] = mangaId
                            it[memo] = JsonObject.EMPTY
                        }.value
                }
            val originalPath = getChapterCbzPath(mangaId, chapterId)
            val archiveFile = File(originalPath)
            check(!archiveFile.exists()) { "Archive fixture already exists: $originalPath" }
            storedFile = archiveFile
            archiveFile.parentFile.mkdirs()
            ZipOutputStream(archiveFile.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("001.jpg"))
                zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
                zip.closeEntry()
            }
            val originalBytes = archiveFile.readBytes()

            val (metadataName, metadataSize) = ChapterDownloadHelper.getCbzMetadataForDownload(chapterId)
            val (stream, downloadName, downloadSize) = ChapterDownloadHelper.getCbzForDownload(1, chapterId, null)
            val downloadedBytes = stream.use { it.readBytes() }

            assertEquals(originalPath, getChapterCbzPath(mangaId, chapterId))
            assertTrue(archiveFile.isFile)
            assertContentEquals(originalBytes, archiveFile.readBytes())
            assertContentEquals(originalBytes, downloadedBytes)
            assertEquals(originalBytes.size.toLong(), metadataSize)
            assertEquals(metadataSize, downloadSize)
            assertEquals("$mangaTitle - $expectedChapterName.cbz", metadataName)
            assertEquals(metadataName, downloadName)
        } finally {
            storedFile?.delete()
            transaction {
                MangaTable.deleteWhere { id eq mangaId }
            }
        }
    }
}
