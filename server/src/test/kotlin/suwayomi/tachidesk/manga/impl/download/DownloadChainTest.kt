package suwayomi.tachidesk.manga.impl.download

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.SharedPreferences
import com.sun.management.UnixOperatingSystemMXBean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.download.fileProvider.findDownloadedPageFile
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ArchiveProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.FolderProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.download.model.DownloadState
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.getMangaDownloadDir
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.management.ManagementFactory
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for the download chain fixes (providers + queue state machine):
 * corrupt/zip-slip CBZ handling, fd hygiene, leftover ".tmp" page files,
 * consistent HEAD/GET archive sizes and queue ordering/retries.
 *
 * Some provider entry points are `protected` (they are only meant to be driven
 * through [suwayomi.tachidesk.manga.impl.ChapterDownloadHelper]); the tests invoke
 * them through the [callSuspend] reflection helper instead of widening production
 * visibility.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DownloadChainTest : ApplicationTest() {
    private val createdChapterArtifacts = mutableListOf<Pair<Int, Int>>()

    // region helpers

    private fun createChapterForManga(
        mangaId: Int,
        url: String,
        downloaded: Boolean = false,
    ): Int =
        transaction {
            ChapterTable
                .insertAndGetId {
                    it[ChapterTable.url] = url
                    it[ChapterTable.name] = url
                    it[ChapterTable.chapter_number] = url.toFloat()
                    it[ChapterTable.sourceOrder] = url.toInt()
                    it[ChapterTable.manga] = mangaId
                    it[ChapterTable.isDownloaded] = downloaded
                    it[ChapterTable.pageCount] = if (downloaded) 10 else -1
                    it[ChapterTable.memo] = JsonObject.EMPTY
                }.value
                .also { createdChapterArtifacts += mangaId to it }
        }

    /** Writes a real zip archive at the chapter's .cbz path and returns the file. */
    private suspend fun writeCbz(
        mangaId: Int,
        chapterId: Int,
        entries: List<Pair<String, ByteArray>>,
    ): File {
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        cbzFile.parentFile?.mkdirs()
        ZipArchiveOutputStream(cbzFile.outputStream()).use { zipOut ->
            entries.forEach { (name, content) ->
                zipOut.putArchiveEntry(ZipArchiveEntry(name))
                zipOut.write(content)
                zipOut.closeArchiveEntry()
            }
        }
        return cbzFile
    }

    private suspend fun writeChapterFolder(
        mangaId: Int,
        chapterId: Int,
        files: List<Pair<String, ByteArray>>,
    ): File {
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))
        chapterFolder.mkdirs()
        files.forEach { (name, content) -> File(chapterFolder, name).writeBytes(content) }
        return chapterFolder
    }

    /** Invokes a protected suspend [name] on [provider] via reflection. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> callSuspend(
        provider: Any,
        name: String,
        vararg args: Any?,
    ): T =
        suspendCoroutine { continuation ->
            val method =
                provider.javaClass.declaredMethods
                    .first { it.name == name && it.parameterCount == args.size + 1 }
            method.isAccessible = true
            try {
                val result = method.invoke(provider, *args, continuation)
                // a suspend function that completes without suspending returns the
                // result directly instead of resuming the continuation
                if (result !== COROUTINE_SUSPENDED) {
                    continuation.resumeWith(Result.success(result as T))
                }
            } catch (e: InvocationTargetException) {
                continuation.resumeWith(Result.failure(e.targetException ?: e))
            } catch (e: Throwable) {
                continuation.resumeWith(Result.failure(e))
            }
        }

    private fun openFdCount(): Long? =
        (ManagementFactory.getOperatingSystemMXBean() as? UnixOperatingSystemMXBean)
            ?.openFileDescriptorCount

    private fun zipEntryNames(file: File): List<String> =
        ZipArchiveInputStream(file.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }

    private fun zipEntryNames(bytes: ByteArray): List<String> =
        ZipArchiveInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }

    // endregion

    // region U4-D queue state machine

    @Test
    fun `enqueue ignores chapter ids without a chapter row instead of rolling back the batch`() =
        runTest {
            val mangaId = createLibraryManga("ENQUEUE_FK_TEST")
            val chapterId = createChapterForManga(mangaId, "1")

            // before the fix the batchUpsert violated the chapter FK and the whole
            // batch was rolled back — no chapter got marked as requested at all
            val returned = DownloadManager.enqueue(1, listOf(chapterId, Int.MAX_VALUE))

            assertEquals(listOf(chapterId), returned)
            val requested =
                transaction {
                    ChapterUserTable
                        .selectAll()
                        .where { (ChapterUserTable.user eq 1) and (ChapterUserTable.chapter inList listOf(chapterId)) }
                        .single()[ChapterUserTable.isDownloadRequested]
                }
            assertTrue(requested)

            DownloadManager.dequeue(1, listOf(chapterId))
        }

    @Test
    fun `reorder clamps an out of bounds target instead of throwing`() =
        runTest {
            val mangaId = createLibraryManga("REORDER_TEST")
            val chapterIds = listOf("1", "2", "3").map { createChapterForManga(mangaId, it) }

            DownloadManager.enqueue(1, chapterIds)

            DownloadManager.reorder(chapterIds.first(), Int.MAX_VALUE)
            assertEquals(
                chapterIds.first(),
                DownloadManager
                    .getStatus()
                    .queue
                    .last()
                    .chapterId,
            )

            DownloadManager.reorder(chapterIds.first(), -5)
            assertEquals(
                chapterIds.first(),
                DownloadManager
                    .getStatus()
                    .queue
                    .first()
                    .chapterId,
            )

            DownloadManager.dequeue(1, chapterIds)
        }

    @Test
    fun `re-enqueueing a failed download resets state and retry counter`() {
        val mangaId = createLibraryManga("RETRY_RESET_TEST")
        val chapterId = createChapterForManga(mangaId, "1")
        val (manga, chapter) =
            transaction {
                MangaTable.toDataClass(
                    MangaTable.selectAll().where { MangaTable.id eq mangaId }.first(),
                ) to
                    ChapterTable.toDataClass(
                        ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first(),
                    )
            }

        val addToQueue =
            DownloadManager::class.java
                .getDeclaredMethod("addToQueue", MangaDataClass::class.java, ChapterDataClass::class.java)
                .apply { isAccessible = true }

        val item = addToQueue.invoke(DownloadManager, manga, chapter) as DownloadQueueItem
        item.state = DownloadState.Error
        item.tries = 3

        val reAdded = addToQueue.invoke(DownloadManager, manga, chapter) as DownloadQueueItem
        assertSame(item, reAdded)
        assertEquals(DownloadState.Queued, reAdded.state)
        assertEquals(0f, reAdded.progress)
        assertEquals(0, reAdded.tries, "a re-enqueued failed download must get its full retry budget back")
    }

    @Test
    fun `download queue persists and restores its order`() =
        runTest {
            val mangaId = createLibraryManga("PERSIST_ORDER_TEST")
            val chapterIds = listOf("1", "2", "3").map { createChapterForManga(mangaId, it) }

            DownloadManager.enqueue(1, chapterIds)
            val queuedIds = DownloadManager.getStatus().queue.map { it.chapterId }
            assertEquals(chapterIds.size, queuedIds.size)

            DownloadManager::class.java
                .getDeclaredMethod("saveDownloadQueue")
                .apply { isAccessible = true }
                .invoke(DownloadManager)

            @Suppress("UNCHECKED_CAST")
            val restored =
                DownloadManager::class.java
                    .getDeclaredMethod("loadDownloadQueue")
                    .apply { isAccessible = true }
                    .invoke(DownloadManager) as List<Int>

            // a StringSet-backed persistence loses the queue order on every restart
            assertEquals(queuedIds, restored)

            DownloadManager.dequeue(1, chapterIds)
        }

    @Test
    fun `download queue restore skips corrupted entries`() =
        runTest {
            val sharedPreferences =
                DownloadManager::class.java
                    .getDeclaredField("sharedPreferences")
                    .apply { isAccessible = true }
                    .get(DownloadManager) as SharedPreferences

            val original = sharedPreferences.getString("downloadQueueKey", null)
            try {
                sharedPreferences.edit().putString("downloadQueueKey", "12,bogus,13").commit()

                @Suppress("UNCHECKED_CAST")
                val restored =
                    DownloadManager::class.java
                        .getDeclaredMethod("loadDownloadQueue")
                        .apply { isAccessible = true }
                        .invoke(DownloadManager) as List<Int>

                assertEquals(listOf(12, 13), restored)
            } finally {
                if (original == null) {
                    sharedPreferences.edit().remove("downloadQueueKey").commit()
                } else {
                    sharedPreferences.edit().putString("downloadQueueKey", original).commit()
                }
            }
        }

    // endregion

    // region U4-A archive provider

    @Test
    fun `reading pages from a cbz releases its file descriptors`() =
        runTest {
            val mangaId = createLibraryManga("FD_TEST")
            val chapterId = createChapterForManga(mangaId, "1")
            writeCbz(mangaId, chapterId, listOf("001.jpg" to byteArrayOf(1, 2, 3), "002.jpg" to byteArrayOf(4, 5, 6)))

            val provider = ArchiveProvider(mangaId, chapterId)
            val fdBefore = openFdCount()

            // getImage opens a fresh ZipFile per call; leaving the returned streams
            // unclosed keeps the underlying channels/fds alive
            val openStreams = (0 until 5).map { provider.getImage().execute(0).first }
            val fdWhileOpen = openFdCount()
            if (fdBefore != null && fdWhileOpen != null) {
                assertTrue(
                    fdWhileOpen >= fdBefore + openStreams.size,
                    "expected the open image streams to hold file descriptors ($fdBefore -> $fdWhileOpen)",
                )
            }

            openStreams.forEach { it.close() }
            val fdAfterClose = openFdCount()
            if (fdBefore != null && fdAfterClose != null) {
                assertTrue(
                    fdAfterClose <= fdBefore + 1,
                    "closing an image stream must release the cbz file descriptor ($fdBefore -> $fdAfterClose)",
                )
            }
        }

    @Test
    fun `extractExistingDownload deletes a truncated cbz so the next attempt recovers`() =
        runTest {
            val mangaId = createLibraryManga("CORRUPT_CBZ_TEST")
            val chapterId = createChapterForManga(mangaId, "1")

            // a real archive cut in half: the entry header is readable but the data is not
            val cbzFile =
                writeCbz(mangaId, chapterId, listOf("001.jpg" to ByteArray(1024) { it.toByte() }))
            val truncated = cbzFile.readBytes().copyOf(cbzFile.length().toInt() / 2)
            cbzFile.writeBytes(truncated)

            val provider = ArchiveProvider(mangaId, chapterId)
            runCatching { callSuspend<Unit>(provider, "extractExistingDownload") }

            // whether extraction threw or silently saw no entries, the broken file must
            // not stay in place — otherwise every retry trips over it forever
            assertFalse(cbzFile.exists(), "corrupt cbz must be deleted after a failed extraction")
        }

    @Test
    fun `extractExistingDownload rejects zip entries escaping the chapter folder`() =
        runTest {
            val mangaId = createLibraryManga("ZIP_SLIP_TEST")
            val chapterId = createChapterForManga(mangaId, "1")

            val escapedFileName = "zip-slip-$chapterId.txt"
            val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))
            val escapedFile = File(chapterFolder.parentFile, escapedFileName)
            val cbzFile =
                writeCbz(mangaId, chapterId, listOf("../$escapedFileName" to "slip".toByteArray()))

            val provider = ArchiveProvider(mangaId, chapterId)
            val result = runCatching { callSuspend<Unit>(provider, "extractExistingDownload") }
            assertIs<IOException>(result.exceptionOrNull(), "zip-slip entry must fail the extraction")
            assertFalse(escapedFile.exists(), "zip-slip entry must not be written outside the chapter folder")
            assertFalse(cbzFile.exists(), "the crafted cbz must be isolated")
        }

    @Test
    fun `handleSuccessfulDownload packs an atomic cbz without tmp page leftovers`() =
        runTest {
            val mangaId = createLibraryManga("PACK_CBZ_TEST")
            val chapterId = createChapterForManga(mangaId, "1")

            val cacheFolder = File(getChapterCachePath(mangaId, chapterId))
            cacheFolder.mkdirs()
            File(cacheFolder, "001.jpg").writeBytes(byteArrayOf(1, 2, 3))
            // leftover of a killed page download — must not end up in the archive
            File(cacheFolder, "002.tmp").writeBytes(byteArrayOf(9, 9))

            val provider = ArchiveProvider(mangaId, chapterId)
            callSuspend<Unit>(provider, "handleSuccessfulDownload")

            val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
            assertTrue(cbzFile.exists())
            assertFalse(File("${cbzFile.path}.tmp").exists(), "packing must not leave a tmp file behind")
            assertEquals(listOf("001.jpg"), zipEntryNames(cbzFile))
        }

    // endregion

    // region U4-B tmp leftovers / U4-C folder provider archive stream

    @Test
    fun `findDownloadedPageFile ignores leftover tmp files`() {
        val dir =
            java.nio.file.Files
                .createTempDirectory("page-existence")
                .toFile()
        try {
            File(dir, "001.tmp").writeBytes(byteArrayOf(1))
            assertNull(findDownloadedPageFile(dir.path, "001"))

            val page = File(dir, "001.jpg")
            page.writeBytes(byteArrayOf(2))
            assertEquals(page, findDownloadedPageFile(dir.path, "001"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `folder provider pages exclude tmp files and out of bounds indexes`() =
        runTest {
            val mangaId = createLibraryManga("FOLDER_PAGES_TEST")
            val chapterId = createChapterForManga(mangaId, "1")
            writeChapterFolder(
                mangaId,
                chapterId,
                listOf("001.zzz" to byteArrayOf(1, 2, 3), "002.tmp" to byteArrayOf(9)),
            )

            val provider = FolderProvider(mangaId, chapterId)
            assertEquals(1, provider.getImageCount(), "the leftover tmp file must not count as a page")

            val (stream, mime) = provider.getImage().execute(0)
            stream.close()
            assertEquals("application/octet-stream", mime, "unknown extensions get a real mime instead of image/tmp")

            val outOfBounds = runCatching { provider.getImage().execute(5) }
            assertIs<NoSuchElementException>(outOfBounds.exceptionOrNull())
        }

    @Test
    fun `folder provider archive size matches the streamed archive`() =
        runTest {
            val mangaId = createLibraryManga("FOLDER_CBZ_TEST")
            val chapterId = createChapterForManga(mangaId, "1")
            writeChapterFolder(
                mangaId,
                chapterId,
                listOf("001.jpg" to ByteArray(2048) { it.toByte() }, "002.png" to ByteArray(128) { 7 }),
            )

            val provider = FolderProvider(mangaId, chapterId)

            val reportedSize = callSuspend<Long>(provider, "getArchiveSize")
            val (stream, streamSize) =
                callSuspend<Pair<InputStream, Long>>(provider, "getAsArchiveStream")
            val body = stream.use { it.readBytes() }

            // HEAD (getArchiveSize) and GET (getAsArchiveStream) must agree on Content-Length
            assertEquals(body.size.toLong(), streamSize)
            assertEquals(body.size.toLong(), reportedSize)
            assertEquals(listOf("001.jpg", "002.png"), zipEntryNames(body))
        }

    // endregion

    @AfterEach
    fun tearDown() {
        runBlocking { DownloadManager.clear() }
        createdChapterArtifacts.forEach { (mangaId, chapterId) ->
            runBlocking {
                File(getChapterCbzPath(mangaId, chapterId)).let {
                    it.delete()
                    File("${it.path}.tmp").delete()
                }
                File(getChapterDownloadPath(mangaId, chapterId)).deleteRecursively()
                File(getChapterCachePath(mangaId, chapterId)).let {
                    it.deleteRecursively()
                    File("${it.path}.cbz").delete()
                    File("${it.path}.cbz.sig").delete()
                    File("${it.path}.cbz.tmp").delete()
                }
                File(getMangaDownloadDir(mangaId)).deleteRecursively()
            }
        }
        createdChapterArtifacts.clear()
        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            UserAccountTable.deleteWhere { id neq 1 }
        }
    }
}
