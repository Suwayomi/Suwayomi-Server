package eu.kanade.tachiyomi.source.local.loader

import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil

/**
 * Loader used to load a chapter from an archive file.
 */
class ArchivePageLoader(private val reader: ArchiveReader) : PageLoader {
    override suspend fun getPages(): List<ReaderPage> = reader.useEntries { entries ->
        println("whgy we here")
        entries
            .filter { it.name.contains(".") && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .mapIndexed { i, entry ->
                ReaderPage(i).apply {
                    stream = { reader.getInputStream(entry.name)!! }
                }
            }
            .toList()
    }

    override fun recycle() {}
}
