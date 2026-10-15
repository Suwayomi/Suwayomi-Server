package eu.kanade.tachiyomi.source.local.loader

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLConnection
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Loader used to load a chapter from a .rar or .cbr file.
 */
class RarPageLoader(
    file: File,
) : PageLoader {
    private val rar = Archive(file)

    private val logger = KotlinLogging.logger {}

    /**
     * junrar's [Archive] is not thread-safe: every [Archive.getInputStream] call runs an
     * extractor task on junrar's own executor that reads the archive's shared channel, so
     * two live extractions would interleave reads and corrupt both pages. The permit is
     * held for a page stream's whole lifetime and released when the consumer closes it —
     * a [Semaphore], not a lock, because the thread that opens a stream (e.g. a request
     * handler) differs from the thread that closes it (e.g. Javalin's async writer).
     * Every consumer (Javalin's result streaming included) closes result streams, so
     * concurrent page requests serialize instead of corrupting each other.
     */
    private val extractionGate = Semaphore(1)

    override suspend fun getPages(): List<ReaderPage> =
        rar.fileHeaders
            .asSequence()
            .filter { !it.isDirectory && isImageEntry(it) }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .mapIndexed { i, header ->
                ReaderPage(i).apply {
                    // junrar's Archive#getInputStream runs the extraction on its own executor
                    // behind a pipe; extracting into a hand-rolled PipedOutputStream on the
                    // caller thread instead deadlocks once a page outgrows the pipe buffer.
                    stream = { openPageStream(header) }
                }
            }.toList()

    /**
     * Same tri-state logic as [ImageUtil.isImage]: the file name decides whenever
     * [URLConnection] can guess a content type. Only inconclusive names are content-sniffed,
     * on a single extraction that is drained to EOF afterwards — closing the pipe early
     * would leave its producer thread unwinding while the next [Archive.getInputStream]
     * call was already reading the shared channel.
     */
    private fun isImageEntry(header: FileHeader): Boolean =
        when (val contentType = contentTypeOf(header.fileName)) {
            null -> {
                extractionGate.acquire()
                try {
                    rar.getInputStream(header).buffered().use { stream ->
                        val isImage = ImageUtil.findImageType(stream) != null
                        stream.drain()
                        isImage
                    }
                } finally {
                    extractionGate.release()
                }
            }

            else -> {
                contentType.startsWith("image/")
            }
        }

    private fun openPageStream(header: FileHeader): InputStream {
        extractionGate.acquire()
        val raw =
            try {
                rar.getInputStream(header)
            } catch (t: Throwable) {
                extractionGate.release()
                throw t
            }
        return object : FilterInputStream(raw) {
            private val released = AtomicBoolean()

            // byte[] reads route through the three-arg read, so counting that and the
            // single-byte read covers every path without double counting
            private val bytesRead = AtomicLong()

            override fun read(): Int =
                super.read().also {
                    if (it != -1) bytesRead.incrementAndGet()
                }

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int =
                super.read(b, off, len).also {
                    if (it > 0) bytesRead.addAndGet(it.toLong())
                }

            override fun skip(n: Long): Long = super.skip(n).also { bytesRead.addAndGet(it) }

            override fun close() {
                try {
                    // finish the extraction before releasing the permit: closing the pipe
                    // early only kills the producer asynchronously, so a still-running
                    // extractor could otherwise interleave reads with the next stream
                    drain()
                    // junrar 8.1.1's extractor thread swallows failures (CRC/IO errors)
                    // and only closes the pipe, so a mid-extraction failure looks like a
                    // clean EOF — detect the truncated page by byte count instead
                    if (bytesRead.get() < header.fullUnpackSize) {
                        logger.warn {
                            "RAR page '${header.fileName}' was truncated: " +
                                "$bytesRead of ${header.fullUnpackSize} bytes extracted"
                        }
                    }
                } catch (t: Throwable) {
                    // a dead producer (broken pipe/closed archive) ends the drain early
                } finally {
                    try {
                        super.close()
                    } finally {
                        if (released.compareAndSet(false, true)) {
                            extractionGate.release()
                        }
                    }
                }
            }
        }
    }

    override fun recycle() {
        rar.close()
    }

    companion object {
        private fun contentTypeOf(name: String): String? =
            try {
                URLConnection.guessContentTypeFromName(name)
            } catch (e: Exception) {
                null
            }

        /** Reads the stream to EOF (discarding) so the extractor task producing it finishes. */
        private fun InputStream.drain() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (read(buffer) != -1) {
                // discard
            }
        }
    }
}
