package eu.kanade.tachiyomi.source.local

import eu.kanade.tachiyomi.source.local.filter.OrderBy
import eu.kanade.tachiyomi.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.local.loader.RarPageLoader
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Regression tests for the junrar single-extraction fixes: an [com.github.junrar.Archive]
 * is not thread-safe, so [RarPageLoader] drains probe streams before the next
 * `getInputStream` and serializes page streams on a gate released on close, and
 * [LocalSource]'s cover update reuses the sniffed stream instead of extracting twice.
 */
class LocalSourceRarExtractionTest : ApplicationTest() {
    private val applicationDirs: ApplicationDirs = Injekt.get()
    private val fileSystem = LocalSourceFileSystem(applicationDirs)
    private val source = LocalSource(fileSystem, LocalCoverManager(fileSystem))

    private val createdFiles = mutableListOf<File>()
    private val createdLoaders = mutableListOf<RarPageLoader>()

    private fun mangaDir(name: String): File =
        File(applicationDirs.localMangaRoot, name)
            .apply { mkdirs() }
            .also { createdFiles += it }

    private fun rarFile(
        name: String,
        vararg entries: Pair<String, ByteArray>,
    ): File =
        File
            .createTempFile(name, ".rar")
            .apply {
                writeBytes(createRar4(*entries))
                createdFiles += this
            }

    @AfterEach
    fun tearDown() {
        createdLoaders.forEach { runCatching { it.recycle() } }
        createdLoaders.clear()
        createdFiles.forEach { it.deleteRecursively() }
        createdFiles.clear()
    }

    /**
     * Entries whose type can't be guessed from the file name are content-sniffed on a
     * single extraction that is drained to EOF, so the probe's producer is done before
     * the next header gets extracted. "aaa" (not an image) sorts first and is probed
     * and drained; "zzz" (image data under an inconclusive name) still becomes a page.
     */
    @Test
    fun inconclusiveEntriesAreProbedAndDrainedBeforeTheNextExtraction() =
        runBlocking {
            val rar =
                rarFile(
                    "probe",
                    "aaa" to "definitely not an image".toByteArray(),
                    "p1.png" to PNG_DATA_1,
                    "zzz" to PNG_DATA_2,
                )
            val loader = RarPageLoader(rar).also { createdLoaders += it }

            val pages = loader.getPages()
            assertEquals(2, pages.size)

            assertContentEquals(PNG_DATA_1, pages[0].stream!!.invoke().use { it.readBytes() })
            assertContentEquals(PNG_DATA_2, pages[1].stream!!.invoke().use { it.readBytes() })
        }

    /**
     * Mirrors the single-stream consumption in `Page.getPageImage`: the provider is
     * invoked once, the buffered stream is sniffed via mark/reset and must still
     * deliver the complete image afterwards.
     */
    @Test
    fun aPageStreamIsSniffableWithoutConsumingBytes() =
        runBlocking {
            val rar = rarFile("sniff", "p1.png" to PNG_DATA_1)
            val loader = RarPageLoader(rar).also { createdLoaders += it }
            val pages = loader.getPages()

            pages.single().stream!!.invoke().buffered().use { stream ->
                assertEquals("image/png", ImageUtil.findImageType(stream)?.mime)
                assertContentEquals(PNG_DATA_1, stream.readBytes())
            }
        }

    /**
     * Two live extractions on the same archive would interleave reads on junrar's
     * shared channel and corrupt both pages, so a second page stream must wait until
     * the first is closed (closing drains the remaining bytes, finishing the producer).
     */
    @Test
    fun concurrentPageStreamsAreSerialized() =
        runBlocking {
            val rar = rarFile("gate", "p1.png" to PNG_DATA_1, "p2.png" to PNG_DATA_2)
            val loader = RarPageLoader(rar).also { createdLoaders += it }
            val pages = loader.getPages()

            val first = pages[0].stream!!.invoke()
            try {
                val second = CompletableFuture.supplyAsync { pages[1].stream!!.invoke() }
                assertFailsWith<TimeoutException> { second.get(500, TimeUnit.MILLISECONDS) }
                first.close()
                second.get(10, TimeUnit.SECONDS).use {
                    assertContentEquals(PNG_DATA_2, it.readBytes())
                }
            } finally {
                runCatching { first.close() }
            }
        }

    /**
     * `updateCover`'s RAR branch extracts the probe stream only once and reuses it for
     * the cover: a non-image entry under an inconclusive name is drained before the
     * image entry that ends up as `cover.png` is extracted.
     */
    @Test
    fun rarCoverExtractionReusesASingleExtraction() =
        runBlocking {
            val manga = mangaDir("rar-cover-manga")
            File(manga, "ch1.cbr").writeBytes(
                createRar4(
                    "aaa" to "not an image".toByteArray(),
                    "zzz.png" to PNG_DATA_1,
                ),
            )

            val result = source.getSearchManga(1, "rar-cover", FilterList(OrderBy.Popular()))

            val thumbnail = result.mangas.single().thumbnail_url
            assertNotNull(thumbnail, "the RAR cover should have been extracted")
            val coverFile = File(thumbnail)
            assertEquals("cover.png", coverFile.name)
            assertContentEquals(PNG_DATA_1, coverFile.readBytes())
        }

    companion object {
        private val PNG_MAGIC =
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        private val PNG_DATA_1 = PNG_MAGIC + ByteArray(64) { (it % 31).toByte() }
        private val PNG_DATA_2 = PNG_MAGIC + ByteArray(96) { (it % 17).toByte() }

        // Minimal RAR4 archive writer (stored/uncompressed entries) for tests; junrar
        // has no writer. Layout: mark block, main header, per-file header + data, end
        // block. All multi-byte fields are little-endian; HEAD_CRC is the low 16 bits
        // of CRC32 over the rest of the header.
        private fun createRar4(vararg entries: Pair<String, ByteArray>): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)) // mark block
            writeBlock(out, type = 0x73, flags = 0x0000, headerData = ByteArray(6)) // main header

            entries.forEach { (name, data) ->
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                val dataCrc = CRC32().apply { update(data) }.value
                val fileHeader =
                    ByteArrayOutputStream()
                        .apply {
                            writeInt(data.size) // UNP_SIZE
                            write(3) // HOST_OS unix
                            writeInt(dataCrc.toInt()) // FILE_CRC
                            writeInt(0) // FTIME
                            write(20) // UNP_VER
                            write(0x30) // METHOD store
                            writeShort(nameBytes.size) // NAME_SIZE
                            writeInt(0x81A4) // ATTR (0100644)
                            write(nameBytes) // FILE_NAME
                        }.toByteArray()
                writeBlock(out, type = 0x74, flags = 0x8000, headerData = fileHeader, packSize = data.size)
                out.write(data)
            }
            writeBlock(out, type = 0x7B, flags = 0x4000, headerData = ByteArray(0)) // end block
            return out.toByteArray()
        }

        private fun writeBlock(
            out: ByteArrayOutputStream,
            type: Int,
            flags: Int,
            headerData: ByteArray,
            packSize: Int = 0,
        ) {
            val body =
                ByteArrayOutputStream()
                    .apply {
                        write(type)
                        writeShort(flags)
                        val hasData = flags and 0x8000 != 0
                        writeShort(7 + (if (hasData) 4 else 0) + headerData.size)
                        if (hasData) writeInt(packSize)
                        write(headerData)
                    }.toByteArray()
            out.writeShort(CRC32().apply { update(body) }.value.toInt() and 0xFFFF)
            out.write(body)
        }

        private fun ByteArrayOutputStream.writeShort(value: Int) {
            write(value and 0xFF)
            write((value shr 8) and 0xFF)
        }

        private fun ByteArrayOutputStream.writeInt(value: Int) {
            write(value and 0xFF)
            write((value shr 8) and 0xFF)
            write((value shr 16) and 0xFF)
            write((value shr 24) and 0xFF)
        }
    }
}
