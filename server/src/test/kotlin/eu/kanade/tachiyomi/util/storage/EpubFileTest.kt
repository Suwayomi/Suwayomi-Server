package eu.kanade.tachiyomi.util.storage

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EpubFileTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun resolvesPackagePagesAndImagesWithinTheArchive() {
        val file = createEpub()

        EpubFile(file).use { epub ->
            assertEquals("OPS/book.opf", epub.getPackageHref())
            assertEquals(listOf("OPS/images/page.jpg"), epub.getImagesFromPages())
            val entry = assertNotNull(epub.getEntry("OPS/images/page.jpg"))
            epub.getInputStream(entry).use { assertContentEquals(IMAGE_BYTES, it.readBytes()) }
        }
    }

    private fun createEpub(
        packagePath: String = "OPS/book.opf",
        imageHrefs: List<String> = listOf("../images/page.jpg"),
        imagePaths: List<String> = listOf("OPS/images/page.jpg"),
        separator: String = "/",
        includeContainer: Boolean = true,
    ): File {
        val file = tempDir.resolve("book.epub").toFile()
        val packageDirectory = packagePath.substringBeforeLast('/')
        val entries = linkedMapOf<String, ByteArray>()
        if (includeContainer) {
            entries["META-INF/container.xml"] =
                """
                <container>
                    <rootfiles><rootfile full-path="${packagePath.replace("/", separator)}" /></rootfiles>
                </container>
                """.trimIndent().toByteArray()
        }
        entries[packagePath] =
            """
            <package>
                <manifest>
                    <item id="page" href="${"text/chapter.xhtml".replace("/", separator)}" media-type="application/xhtml+xml" />
                </manifest>
                <spine><itemref idref="page" /></spine>
            </package>
            """.trimIndent().toByteArray()
        entries["$packageDirectory/text/chapter.xhtml"] =
            imageHrefs
                .joinToString("", prefix = "<html><body>", postfix = "</body></html>") {
                    """<img src="${it.replace("/", separator)}" />"""
                }.toByteArray()
        imagePaths.forEach { entries[it] = IMAGE_BYTES }

        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path.replace("/", separator)))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    companion object {
        private val IMAGE_BYTES = byteArrayOf(1, 2, 3)
    }
}
