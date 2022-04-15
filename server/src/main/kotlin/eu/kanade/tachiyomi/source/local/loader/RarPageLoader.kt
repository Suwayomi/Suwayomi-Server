package eu.kanade.tachiyomi.source.local.loader

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.File


/**
 * Loader used to load a chapter from a .rar or .cbr file.
 */
class RarPageLoader(file: File) : PageLoader {

    /**
     * The rar archive to load pages from.
     */
    private val archive = Archive(file)

    /**
     * The fully uncompressed files, to be used in case archive is solid.
     */
    private var archiveMap = mutableMapOf<FileHeader, InputStream>()

    /**
     * Returns an observable containing the pages found on this rar archive ordered with a natural
     * comparator.
     */
    override fun getPages(): List<ReaderPage> {
        if (archive.mainHeader.isSolid) {
            // Solid means that we need to read all the file sequentially
            for (header in archive.fileHeaders) {
                val baos = ByteArrayOutputStream()
                archive.extractFile(header, baos)
                archiveMap[header] = ByteArrayInputStream(baos.toByteArray())
            }
            // After reading the full archive, proceed to filter and transform
            return archive.fileHeaders
                .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { archiveMap.getValue(it) } }
                .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                .mapIndexed { i, header ->
                    val streamFn = { archiveMap.getValue(header) }

                    ReaderPage(i).apply {
                        stream = streamFn
                        status = Page.READY
                    }
                }
        }
        return archive.fileHeaders
            .filter { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
            .mapIndexed { i, header ->
                val streamFn = { archive.getInputStream(header) }

                ReaderPage(i).apply {
                    stream = streamFn
                    status = Page.READY
                }
            }
    }
}
