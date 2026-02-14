package eu.kanade.tachiyomi.source.local.loader

import eu.kanade.tachiyomi.source.local.loader.EpubReader.Companion.epubReader
import eu.kanade.tachiyomi.util.storage.EpubFile
import java.io.File
import java.nio.file.Path

/**
 * Loader used to load a chapter from a .epub file.
 */
class EpubReaderPageLoader(
    file: Path,
) : PageLoader {
    private val epub = file.epubReader()

    override suspend fun getPages(): List<ReaderPage> =
        epub
            .getImagesFromPages()
            .mapIndexed { i, path ->
                val streamFn = { epub.getInputStream(path)!! }
                ReaderPage(i).apply {
                    stream = streamFn
                }
            }

    override fun recycle() {}
}
