package eu.kanade.tachiyomi.source.local.loader

import eu.kanade.tachiyomi.util.storage.EpubFile
import java.io.File

/**
 * Loader used to load a chapter from a .epub file.
 */
class EpubPageLoader(file: File) : PageLoader {
    private val epub = EpubFile(file)

    override suspend fun getPages(): List<ReaderPage> {
        return epub.getImagesFromPages()
            .mapIndexed { i, path ->
                val streamFn = { epub.getInputStream(epub.getEntry(path)!!) }
                ReaderPage(i).apply {
                    stream = streamFn
                }
            }
    }

    override fun recycle() {
        epub.close()
    }
}
