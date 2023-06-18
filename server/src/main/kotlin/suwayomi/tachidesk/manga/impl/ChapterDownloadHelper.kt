package suwayomi.tachidesk.manga.impl

import kotlinx.coroutines.CoroutineScope
import suwayomi.tachidesk.manga.impl.download.ArchiveProvider
import suwayomi.tachidesk.manga.impl.download.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.FolderProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.server.serverConfig
import java.io.File
import java.io.InputStream

object ChapterDownloadHelper {
    fun getImage(mangaId: Int, chapterId: Int, index: Int): Pair<InputStream, String> {
        return provider(mangaId, chapterId).getImage(index)
    }

    fun delete(mangaId: Int, chapterId: Int): Boolean {
        return provider(mangaId, chapterId).delete()
    }

    suspend fun download(
        mangaId: Int,
        chapterId: Int,
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit
    ): Boolean {
        return provider(mangaId, chapterId).download(download, scope, step)
    }

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private fun provider(mangaId: Int, chapterId: Int): ChaptersFilesProvider {
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        if (cbzFile.exists()) return ArchiveProvider(mangaId, chapterId)
        if (!chapterFolder.exists() && serverConfig.downloadAsCbz) return ArchiveProvider(mangaId, chapterId)
        return FolderProvider(mangaId, chapterId)
    }
}
