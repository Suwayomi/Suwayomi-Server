package eu.kanade.tachiyomi.source.local.loader

import eu.kanade.tachiyomi.util.storage.EpubFile
import java.io.File

/**
 * Loader used to load a chapter from a .epub file.
 */
class EpubPageLoader(
    file: File,
) : PageLoader {
    private val epub = EpubFile(file)

    override suspend fun getPages(): List<ReaderPage> =
        epub
            .getImagesFromPages()
            // pages pointing at entries missing from the archive are skipped
            .mapNotNull { path -> epub.getEntry(path) }
            .mapIndexed { i, entry ->
                ReaderPage(i).apply {
                    stream = { epub.getInputStream(entry) }
                }
            }

    override fun recycle() {
        epub.close()
    }
}
