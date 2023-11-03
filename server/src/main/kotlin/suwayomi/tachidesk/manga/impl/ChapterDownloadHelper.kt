package suwayomi.tachidesk.manga.impl

import kotlinx.coroutines.CoroutineScope
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ArchiveProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.FolderProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPaths
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPaths
import suwayomi.tachidesk.server.serverConfig
import java.io.File
import java.io.InputStream

object ChapterDownloadHelper {
    fun getImage(
        mangaId: Int,
        chapterId: Int,
        index: Int,
    ): Pair<InputStream, String> {
        return provider(mangaId, chapterId).getImage().execute(index)
    }

    fun delete(
        mangaId: Int,
        chapterId: Int,
    ): Boolean {
        return provider(mangaId, chapterId).delete()
    }

    suspend fun download(
        mangaId: Int,
        chapterId: Int,
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit,
    ): Boolean {
        return provider(mangaId, chapterId).download().execute(download, scope, step)
    }

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private fun provider(
        mangaId: Int,
        chapterId: Int,
    ): ChaptersFilesProvider {
        val chapterFolders = getChapterDownloadPaths(mangaId, chapterId).map { File(it) }
        val cbzFiles = getChapterCbzPaths(mangaId, chapterId).map { File(it) }
        if (cbzFiles.any { it.exists() }) return ArchiveProvider(mangaId, chapterId)
        if (chapterFolders.none { it.exists() } && serverConfig.downloadAsCbz.value) return ArchiveProvider(mangaId, chapterId)
        return FolderProvider(mangaId, chapterId)
    }
}
