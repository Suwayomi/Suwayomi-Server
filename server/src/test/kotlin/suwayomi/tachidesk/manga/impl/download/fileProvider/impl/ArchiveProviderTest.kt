package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArchiveProviderTest {
    private val temporaryDirectories = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        temporaryDirectories.forEach(File::deleteRecursively)
    }

    @Test
    fun validArchiveIsExtracted() {
        val root = createTemporaryDirectory()
        val cacheFolder = File(root, "cache").apply { mkdirs() }
        File(cacheFolder, "001.jpg").writeText("first page")
        File(cacheFolder, "002.jpg").writeText("second page")
        val cbzFile = File(root, "chapter.cbz")
        createCbzFile(cbzFile, cacheFolder)
        val chapterFolder = File(root, "chapter")
        var validationFailure: IOException? = null

        extractExistingCbzFile(cbzFile, chapterFolder) { validationFailure = it }

        assertEquals(null, validationFailure)
        assertFalse(cbzFile.exists())
        assertEquals("first page", File(chapterFolder, "001.jpg").readText())
        assertEquals("second page", File(chapterFolder, "002.jpg").readText())
    }

    @Test
    fun corruptedArchiveAndPartialExtractionAreDeleted() {
        val root = createTemporaryDirectory()
        val cacheFolder = File(root, "cache").apply { mkdirs() }
        File(cacheFolder, "001.jpg").writeBytes(ByteArray(8192) { it.toByte() })
        val cbzFile = File(root, "chapter.cbz")
        createCbzFile(cbzFile, cacheFolder)
        cbzFile.writeBytes(cbzFile.readBytes().copyOf(cbzFile.length().toInt() / 2))
        val chapterFolder = File(root, "chapter").apply { mkdirs() }
        File(chapterFolder, "partial.jpg").writeText("partial page")
        var validationFailure: IOException? = null

        extractExistingCbzFile(cbzFile, chapterFolder) { validationFailure = it }

        assertNotNull(validationFailure)
        assertFalse(cbzFile.exists())
        assertFalse(chapterFolder.exists())
    }

    @Test
    fun completedArchiveReplacesIncompleteFiles() {
        val root = createTemporaryDirectory()
        val cacheFolder = File(root, "cache").apply { mkdirs() }
        File(cacheFolder, "001.jpg").writeText("complete page")
        val cbzFile = File(root, "chapter.cbz").apply { writeText("broken archive") }
        val incompleteCbzFile = File(root, "chapter.cbz.part").apply { writeText("interrupted write") }

        createCbzFile(cbzFile, cacheFolder)

        assertTrue(cbzFile.exists())
        assertFalse(incompleteCbzFile.exists())

        val chapterFolder = File(root, "chapter")
        extractExistingCbzFile(cbzFile, chapterFolder) { throw AssertionError("Completed archive should be valid", it) }
        assertEquals("complete page", File(chapterFolder, "001.jpg").readText())
    }

    private fun createTemporaryDirectory(): File =
        Files.createTempDirectory("archive-provider-test-").toFile().also(temporaryDirectories::add)
}
