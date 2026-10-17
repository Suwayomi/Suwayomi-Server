package eu.kanade.tachiyomi.util.storage

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
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

    @Test
    fun normalizesDotSegmentsAndRootRelativeImagePaths() {
        val expected = listOf("OPS/images/page.jpg", "images/root.jpg", "outside.jpg")
        val file =
            createEpub(
                imageHrefs = listOf("./../images/page.jpg", "/images/root.jpg", "../../outside.jpg"),
                imagePaths = expected,
            )

        EpubFile(file).use { epub ->
            assertEquals(expected, epub.getImagesFromPages())
            expected.forEach { assertNotNull(epub.getEntry(it)) }
        }
    }

    @Test
    fun preservesBackslashArchiveCompatibility() {
        val file = createEpub(separator = "\\")

        EpubFile(file).use { epub ->
            assertEquals("OPS\\book.opf", epub.getPackageHref())
            assertEquals(listOf("OPS\\images\\page.jpg"), epub.getImagesFromPages())
        }
    }

    @Test
    fun resolvesTheFallbackPackageWithoutContainerMetadata() {
        val file =
            createEpub(
                packagePath = "OEBPS/content.opf",
                imagePaths = listOf("OEBPS/images/page.jpg"),
                includeContainer = false,
            )

        EpubFile(file).use { epub ->
            assertEquals("OEBPS/content.opf", epub.getPackageHref())
            assertEquals(listOf("OEBPS/images/page.jpg"), epub.getImagesFromPages())
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

        ZipArchiveOutputStream(file).use { zip ->
            entries.forEach { (path, bytes) ->
                val entry =
                    object : ZipArchiveEntry(path) {
                        init {
                            // FAT entries have backslashes normalized by Commons Compress when read.
                            setPlatform(PLATFORM_UNIX)
                            setName(path.replace("/", separator))
                        }
                    }
                zip.putArchiveEntry(entry)
                zip.write(bytes)
                zip.closeArchiveEntry()
            }
        }
        return file
    }

    companion object {
        private val IMAGE_BYTES = byteArrayOf(1, 2, 3)
    }
}
