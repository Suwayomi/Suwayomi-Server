package suwayomi.tachidesk.manga.impl.download

import java.io.InputStream

/*
* Base class for downloaded chapter files provider, example: Folder, Archive
* */
abstract class DownloadedFilesProvider(val mangaId: Int, val chapterId: Int) {
    abstract fun getImage(index: Int): Pair<InputStream, String>

    abstract fun putImage(index: Int, image: InputStream): Boolean

    abstract fun delete(): Boolean

    private fun getPageName(index: Int): String {
        return String.format("%03d", index + 1)
    }
}
