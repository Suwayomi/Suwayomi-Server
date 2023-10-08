package eu.kanade.tachiyomi.source.local.loader

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Loader used to load a chapter from a .rar or .cbr file.
 */
class RarPageLoader(file: File) : PageLoader {
    private val rar = Archive(file)

    override suspend fun getPages(): List<ReaderPage> {
        return rar.fileHeaders.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { rar.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .mapIndexed { i, header ->
                ReaderPage(i).apply {
                    stream = { getStream(rar, header) }
                }
            }
            .toList()
    }

    override fun recycle() {
        rar.close()
    }

    /**
     * Returns an input stream for the given [header].
     */
    private fun getStream(
        rar: Archive,
        header: FileHeader,
    ): InputStream {
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)
        synchronized(this) {
            try {
                pipeOut.use {
                    rar.extractFile(header, it)
                }
            } catch (_: Exception) {
            }
        }
        return pipeIn
    }
}
