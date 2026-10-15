package eu.kanade.tachiyomi.source.local

import eu.kanade.tachiyomi.source.local.filter.OrderBy
import eu.kanade.tachiyomi.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.local.io.Format
import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.local.loader.RarPageLoader
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalSourceTest : ApplicationTest() {
    private val applicationDirs: ApplicationDirs = Injekt.get()
    private val fileSystem = LocalSourceFileSystem(applicationDirs)
    private val source = LocalSource(fileSystem, LocalCoverManager(fileSystem))

    private val createdFiles = mutableListOf<File>()

    private fun mangaDir(name: String): File =
        File(applicationDirs.localMangaRoot, name)
            .apply { mkdirs() }
            .also { createdFiles += it }

    private fun createCbz(
        dir: File,
        name: String,
        vararg pages: Pair<String, ByteArray>,
    ): File =
        File(dir, name).apply {
            ZipOutputStream(outputStream()).use { zip ->
                pages.forEach { (entryName, data) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(data)
                    zip.closeEntry()
                }
            }
        }

    private fun chapter(url: String): SChapter = SChapter.create().apply { this.url = url }

    @AfterEach
    fun tearDown() {
        createdFiles.forEach { it.deleteRecursively() }
        createdFiles.clear()
        LocalSource.pageCache.clear()
    }

    /**
     * U9-A: previously the loader wrote into a bounded PipedOutputStream on the calling
     * thread, which deadlocked as soon as a page outgrew the ~64KB pipe buffer.
     */
    @Test
    fun rarPageLoaderReadsPagesLargerThanThePipeBuffer() =
        runBlocking {
            // a stored entry larger than PipedInputStream's default 64KB buffer
            val pageData =
                ByteArray(200 * 1024) { (it % 251).toByte() }
                    .also { PNG_MAGIC.copyInto(it) }
            val rar =
                File
                    .createTempFile("local-source-test", ".rar")
                    .apply { writeBytes(createRar4("p1.png", pageData)) }
            try {
                val loader = RarPageLoader(rar)
                val pages = loader.getPages()
                assertEquals(1, pages.size)

                val bytes = pages[0].stream!!.invoke().use { it.readBytes() }
                assertContentEquals(pageData, bytes)

                loader.recycle()
            } finally {
                rar.delete()
            }
        }

    /**
     * U9-B: pageCache is bounded and evicted entries recycle their loader (closing the
     * underlying archive FD). A replaced entry is recycled too.
     */
    @Test
    fun pageCacheIsBoundedAndRecyclesLoaders() =
        runBlocking {
            val manga = mangaDir("u9-cache-manga")
            repeat(PAGE_COUNT) { i ->
                createCbz(manga, "ch$i.cbz", "p1.png" to PNG_DATA)
            }

            // re-loading the same chapter replaces and recycles its previous loader
            val firstChapterUrl = "u9-cache-manga/ch0.cbz"
            source.getPageList(chapter(firstChapterUrl))
            val replacedStream = LocalSource.pageCache[firstChapterUrl]!!.second[0]
            source.getPageList(chapter(firstChapterUrl))
            // the underlying archive of a recycled loader is closed: reads fail
            assertFailsWith<Exception> { replacedStream().use { it.read() } }

            // filling the cache beyond its bound evicts (and recycles) the oldest entries
            val evictedStream = LocalSource.pageCache[firstChapterUrl]!!.second[0]
            for (i in 1 until PAGE_COUNT) {
                source.getPageList(chapter("u9-cache-manga/ch$i.cbz"))
            }

            assertTrue(LocalSource.pageCache.size <= 10)
            assertFailsWith<Exception> { evictedStream().use { it.read() } }

            // the most recently loaded chapter is still readable
            val lastUrl = "u9-cache-manga/ch${PAGE_COUNT - 1}.cbz"
            val liveStream = LocalSource.pageCache[lastUrl]!!.second[0]
            liveStream().use { assertTrue(it.readBytes().isNotEmpty()) }
        }

    /**
     * U9-B: a page stream opened before its cache entry is evicted must keep reading —
     * recycling is deferred until the stream closes instead of pulling the archive out
     * from under the in-flight read (the truncated read used to surface as a 500).
     * A provider invoked after its entry was evicted fails fast instead.
     */
    // the explicit Unit return type matters: JUnit5 silently skips @Test methods whose
    // inferred return type isn't void
    @Test
    fun inFlightPageStreamSurvivesCacheEviction(): Unit =
        runBlocking {
            val manga = mangaDir("u9-inflight-manga")
            val payload =
                ByteArray(200 * 1024) { (it % 251).toByte() }
                    .also { PNG_MAGIC.copyInto(it) }
            File(manga, "ch0.cbr").writeBytes(createRar4("p1.png", payload))
            for (i in 1 until PAGE_COUNT) {
                createCbz(manga, "ch$i.cbz", "p1.png" to PNG_DATA)
            }

            val chapterUrl = "u9-inflight-manga/ch0.cbr"
            source.getPageList(chapter(chapterUrl))
            val provider = LocalSource.getCachedPageStream(chapterUrl, 0)!!
            val stream = provider()

            // keep the stream in-flight while the entry is pushed out of the bounded cache
            val firstByte = stream.read()
            assertTrue(firstByte != -1)
            for (i in 1 until PAGE_COUNT) {
                source.getPageList(chapter("u9-inflight-manga/ch$i.cbz"))
            }
            assertTrue(LocalSource.pageCache.size <= 10)

            // the evicted entry's archive stays open until the stream is closed, so the
            // remaining bytes still read correctly
            val rest = stream.readBytes()
            stream.close()
            assertContentEquals(payload, byteArrayOf(firstByte.toByte()) + rest)

            // opening another stream through the evicted provider fails fast
            val failure = assertFailsWith<Exception> { provider() }
            assertIs<IOException>(failure)
        }

    /**
     * U9-D: chapter urls escaping the local manga root are rejected.
     */
    @Test
    fun getFormatRejectsChapterUrlEscapingMangaRoot() {
        val escapeTarget =
            File(applicationDirs.localMangaRoot)
                .parentFile!!
                .resolve("u9-escape-target")
                .apply { mkdirs() }
                .also { createdFiles += it }
        assertTrue(escapeTarget.exists())

        assertFailsWith<Exception> { source.getFormat(chapter("../u9-escape-target")) }
        assertFailsWith<Exception> { source.getFormat(chapter("")) }

        // a normal chapter directory still resolves
        val manga = mangaDir("u9-format-manga")
        File(manga, "ch1").mkdirs()
        val format = assertIs<Format.Directory>(source.getFormat(chapter("u9-format-manga/ch1")))
        assertEquals("ch1", format.file.name)
    }

    /**
     * U9-C/U9-G: a single broken epub must not break browsing, and the chapter keeps being
     * listed without epub metadata. Directory chapter imageUrls are stored relative to the
     * manga root and resolve inside it.
     */
    @Test
    fun brokenEpubDoesNotBreakBrowse() =
        runBlocking {
            val manga = mangaDir("u9-broken-manga")
            File(manga, "bad.epub").writeText("definitely not a zip")
            createCbz(manga, "ch1.cbz", "p1.png" to PNG_DATA)
            File(manga, "chapters").apply {
                mkdirs()
                File(this, "p1.png").writeBytes(PNG_DATA)
            }

            val update =
                source.getMangaUpdate(
                    SManga.create().apply {
                        url = "u9-broken-manga"
                        title = "u9-broken-manga"
                    },
                    emptyList(),
                    fetchDetails = false,
                    fetchChapters = true,
                )
            // the broken epub is still listed (without metadata) instead of failing everything
            assertEquals(3, update.chapters.size)

            val result = source.getSearchManga(1, "u9-broken-manga", FilterList(OrderBy.Popular()))
            assertEquals(listOf("u9-broken-manga"), result.mangas.map { it.title })

            // directory chapter pages use root-relative urls that resolve inside the root
            val pages = source.getPageList(chapter("u9-broken-manga/chapters"))
            assertEquals(listOf("u9-broken-manga/chapters/p1.png"), pages.map { it.imageUrl })
            assertContentEquals(PNG_DATA, LocalSource.resolvePageFile(pages[0].imageUrl!!).readBytes())
        }

    /**
     * U9-E: sorting dispatches on the filter's state index, so a client-sent "Date" selection
     * on the Popular filter actually sorts by date.
     */
    @Test
    fun sortByDateUsesSelectionIndex() =
        runBlocking {
            mangaDir("u9-sort-old").apply { setLastModified(System.currentTimeMillis() - 60_000) }
            mangaDir("u9-sort-new")

            val byDateDesc =
                FilterList(
                    OrderBy.Popular().apply {
                        state = Filter.Sort.Selection(OrderBy.DATE_INDEX, false)
                    },
                )
            val titles =
                source
                    .getSearchManga(1, "u9-sort-", byDateDesc)
                    .mangas
                    .map { it.title }
            assertEquals(listOf("u9-sort-new", "u9-sort-old"), titles)

            // a null selection no longer throws
            val noState = FilterList(OrderBy.Popular().apply { state = null })
            val byName =
                source
                    .getSearchManga(1, "u9-sort-", noState)
                    .mangas
                    .map { it.title }
            assertEquals(listOf("u9-sort-new", "u9-sort-old"), byName.sorted())
        }

    /**
     * U9-B: a full page-fetch cycle for an archive chapter serves bytes from the cached loader.
     * Exercised through [LocalSource.getCachedPageStream] which Page.kt uses.
     */
    @Test
    fun archivePageStreamReadsFromCache() =
        runBlocking {
            val manga = mangaDir("u9-stream-manga")
            createCbz(manga, "ch1.cbz", "p1.png" to PNG_DATA)

            val chapterUrl = "u9-stream-manga/ch1.cbz"
            val pages = source.getPageList(chapter(chapterUrl))
            assertEquals(1, pages.size)

            val stream = LocalSource.getCachedPageStream(chapterUrl, 0)
            assertContentEquals(PNG_DATA, stream!!.invoke().use { it.readBytes() })
        }

    companion object {
        private const val PAGE_COUNT = 12

        private val PNG_MAGIC =
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        private val PNG_DATA = PNG_MAGIC + ByteArray(64)

        // Minimal RAR4 archive writer (stored/uncompressed entries) for tests; junrar has no
        // writer. Layout: mark block, main header, per-file header + data, end block.
        // All multi-byte fields are little-endian; HEAD_CRC is the low 16 bits of CRC32 over
        // the rest of the header.
        private fun createRar4(
            name: String,
            data: ByteArray,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)) // mark block
            writeBlock(out, type = 0x73, flags = 0x0000, headerData = ByteArray(6)) // main header

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
