package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import kotlinx.coroutines.CoroutineScope
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/*
* Provides downloaded files when pages were downloaded into folders
* */
class FolderProvider(mangaId: Int, chapterId: Int) : ChaptersFilesProvider(mangaId, chapterId) {
    override fun getImageImpl(index: Int): Pair<InputStream, String> {
        val chapterDir = getChapterDownloadPath(mangaId, chapterId)
        val folder = File(chapterDir)
        folder.mkdirs()
        val file = folder.listFiles()?.sortedBy { it.name }?.get(index)
        val fileType = file!!.name.substringAfterLast(".")
        return Pair(FileInputStream(file).buffered(), "image/$fileType")
    }

    override suspend fun downloadImpl(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit
    ): Boolean {
        val chapterDir = getChapterDownloadPath(mangaId, chapterId)
        val folder = File(chapterDir)

        val alreadyDownloaded = folder.exists()
        if (alreadyDownloaded) {
            return true
        }

        val downloadSucceeded = super.downloadImpl(download, scope, step)
        if (!downloadSucceeded) {
            return false
        }

        folder.mkdirs()
        val cacheChapterDir = getChapterCachePath(mangaId, chapterId)
        File(cacheChapterDir).renameTo(folder)

        return true
    }

    override fun delete(): Boolean {
        val chapterDir = getChapterDownloadPath(mangaId, chapterId)
        return File(chapterDir).deleteRecursively()
    }

    private fun isExistingFile(folder: File, fileName: String): Boolean {
        val existingFile = folder.listFiles { file ->
            file.isFile && file.name.startsWith(fileName)
        }?.firstOrNull()
        return existingFile?.exists() == true
    }
}
