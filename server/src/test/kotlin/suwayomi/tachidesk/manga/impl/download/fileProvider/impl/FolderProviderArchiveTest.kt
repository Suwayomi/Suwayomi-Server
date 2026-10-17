package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderProviderArchiveTest {
    @TempDir
    lateinit var chapterDir: File

    // how the archive was built in memory before, which KOReader binary checksums of served chapters depend on
    private fun inMemoryArchive(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipArchiveOutputStream(BufferedOutputStream(byteArrayOutputStream)).use { zipOutputStream ->
            zipOutputStream.setMethod(ZipArchiveOutputStream.DEFLATED)
            zipOutputStream.setLevel(Deflater.DEFAULT_COMPRESSION)

            chapterDir
                .listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { imageFile ->
                    imageFile.inputStream().use { fileInputStream ->
                        val zipEntry = ZipArchiveEntry(imageFile.name)
                        zipEntry.time = 0L
                        zipOutputStream.putArchiveEntry(zipEntry)
                        fileInputStream.copyTo(zipOutputStream)
                        zipOutputStream.closeArchiveEntry()
                    }
                }
        }
        return byteArrayOutputStream.toByteArray()
    }

    @Test
    fun tempFileArchiveKeepsTheBytesOfTheInMemoryOneAndIsDeletedOnClose() {
        listOf("002.webp" to 1, "001.jpg" to 300_000, "003 é.png" to 70_000, "ComicInfo.xml" to 0)
            .forEach { (name, size) -> File(chapterDir, name).writeBytes(Random(size).nextBytes(size)) }
        File(chapterDir, "subfolder").mkdir()
        val chapterId = Random.nextInt(1_000_000_000, Int.MAX_VALUE)

        fun tempArchives() =
            File(System.getProperty("java.io.tmpdir")).listFiles { file -> file.name.startsWith("chapter-$chapterId-") }.orEmpty()

        val (inputStream, size) = FolderProvider.archiveToTempFile(chapterDir, chapterId)
        assertEquals(1, tempArchives().size)
        val bytes = inputStream.use { it.readBytes() }

        assertContentEquals(inMemoryArchive(), bytes)
        assertEquals(bytes.size.toLong(), size)
        assertTrue(tempArchives().isEmpty(), "the temp archive must be deleted once read")
    }
}
