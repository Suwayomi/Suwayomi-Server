package suwayomi.tachidesk.manga.impl

import suwayomi.tachidesk.manga.impl.download.DownloadedFilesProvider
import suwayomi.tachidesk.manga.impl.download.FolderProvider
import java.io.InputStream

object ChapterDownloadHelper {
    fun getImage(mangaId: Int, chapterId: Int, index: Int): Pair<InputStream, String> {
        return provider(mangaId, chapterId).getImage(index)
    }

    fun delete(mangaId: Int, chapterId: Int): Boolean {
        return provider(mangaId, chapterId).delete()
    }

    fun putImage(mangaId: Int, chapterId: Int, index: Int, image: InputStream): Boolean {
        return provider(mangaId, chapterId).putImage(index, image)
    }

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private fun provider(mangaId: Int, chapterId: Int): DownloadedFilesProvider {
        return FolderProvider(mangaId, chapterId)
    }
}
