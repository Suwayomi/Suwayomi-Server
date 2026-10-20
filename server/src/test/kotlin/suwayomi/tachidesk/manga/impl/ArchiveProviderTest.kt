package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ArchiveProvider
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchiveProviderTest : ApplicationTest() {
    private val createdMangaDirs = mutableListOf<File>()

    @Test
    fun getImageCountThrowsNoSuchElementWhenCbzIsMissing() =
        runTest {
            val (provider, cbzFile) = createFixture("ARCHIVE_MISSING_TEST", "1")
            assertFalse(cbzFile.exists())

            assertFailsWith<NoSuchElementException> { provider.getImageCount() }
        }

    @Test
    fun getImageCountPropagatesIoExceptionWhenCbzPathIsDirectory() =
        runTest {
            val (provider, cbzFile) = createFixture("ARCHIVE_DIR_TEST", "2")
            cbzFile.mkdirs()

            assertFailsWith<IOException> { provider.getImageCount() }
        }

    @Test
    fun getImageCountPropagatesIoExceptionWhenCbzIsNotAZip() =
        runTest {
            val (provider, cbzFile) = createFixture("ARCHIVE_NOT_ZIP_TEST", "3")
            cbzFile.parentFile.mkdirs()
            cbzFile.writeText("this file is not a zip archive, it only pretends to be one")

            assertFailsWith<IOException> { provider.getImageCount() }
        }

    @Test
    fun getImageCountCountsFileEntriesOnly() =
        runTest {
            val (provider, cbzFile) = createFixture("ARCHIVE_VALID_TEST", "4")
            writeCbz(
                cbzFile,
                files = mapOf("001.png" to byteArrayOf(1, 2, 3), "nested/002.png" to byteArrayOf(4, 5)),
                directories = listOf("nested/"),
            )

            assertEquals(2, provider.getImageCount())
        }

    @Test
    fun getImageReturnsEntryBytesInNameOrder() =
        runTest {
            val (provider, cbzFile) = createFixture("ARCHIVE_STREAM_TEST", "5")
            writeCbz(
                cbzFile,
                files = mapOf("001.png" to byteArrayOf(1, 2, 3), "nested/002.png" to byteArrayOf(4, 5)),
                directories = listOf("nested/"),
            )

            val (firstStream, firstMime) = provider.getImage().executeGetImage(0)
            firstStream.use { assertContentEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
            assertEquals("image/png", firstMime)

            val (secondStream, _) = provider.getImage().executeGetImage(1)
            secondStream.use { assertContentEquals(byteArrayOf(4, 5), it.readBytes()) }
        }

    private suspend fun createFixture(
        mangaTitle: String,
        chapterName: String,
    ): Pair<ArchiveProvider, File> {
        val mangaId = createLibraryManga(mangaTitle)
        val chapterId =
            transaction {
                ChapterTable
                    .insertAndGetId {
                        it[url] = chapterName
                        it[name] = chapterName
                        it[sourceOrder] = 1
                        it[manga] = mangaId
                        it[memo] = JsonObject.EMPTY
                    }.value
            }
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        cbzFile.parentFile?.let { createdMangaDirs += it }
        return ArchiveProvider(mangaId, chapterId) to cbzFile
    }

    private fun writeCbz(
        cbzFile: File,
        files: Map<String, ByteArray>,
        directories: List<String> = emptyList(),
    ) {
        cbzFile.parentFile.mkdirs()
        ZipArchiveOutputStream(cbzFile.outputStream()).use { zipOut ->
            directories.forEach { name ->
                zipOut.putArchiveEntry(ZipArchiveEntry(name))
                zipOut.closeArchiveEntry()
            }
            files.forEach { (name, content) ->
                zipOut.putArchiveEntry(ZipArchiveEntry(name))
                zipOut.write(content)
                zipOut.closeArchiveEntry()
            }
        }
    }

    @AfterEach
    internal fun tearDown() {
        createdMangaDirs.forEach { mangaDir ->
            mangaDir.deleteRecursively()
            mangaDir.parentFile?.delete()
        }
        createdMangaDirs.clear()
        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }
}
