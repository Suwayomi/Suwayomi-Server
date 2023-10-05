package eu.kanade.tachiyomi.source.local.loader

import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import org.apache.commons.compress.archivers.zip.ZipFile
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.File

/**
 * Loader used to load a chapter from a .zip or .cbz file.
 */
class ZipPageLoader(file: File) : PageLoader {
    private val zip = ZipFile(file)

    override suspend fun getPages(): List<ReaderPage> {
        return zip.entries.asSequence()
            .filter { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                ReaderPage(i).apply {
                    stream = { zip.getInputStream(entry) }
                }
            }
            .toList()
    }

    override fun recycle() {
        zip.close()
    }
}
