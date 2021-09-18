package eu.kanade.tachiyomi.source.local.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.storage.EpubFile
import java.io.File

/**
 * Loader used to load a chapter from a .epub file.
 */
class EpubPageLoader(file: File) : PageLoader {

    /**
     * The epub file.
     */
    private val epub = EpubFile(file)

    /**
     * Returns an observable containing the pages found on this zip archive ordered with a natural
     * comparator.
     */
    override fun getPages(): List<ReaderPage> {
        return epub.getImagesFromPages()
            .mapIndexed { i, path ->
                val streamFn = { epub.getInputStream(epub.getEntry(path)!!) }
                ReaderPage(i).apply {
                    stream = streamFn
                    status = Page.READY
                }
            }
    }
}
