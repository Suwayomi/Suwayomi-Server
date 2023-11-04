package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import kotlinx.coroutines.CoroutineScope
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPaths
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/*
* Provides downloaded files when pages were downloaded into folders
* */
class FolderProvider(mangaId: Int, chapterId: Int) : ChaptersFilesProvider(mangaId, chapterId) {
    override fun getImageImpl(index: Int): Pair<InputStream, String> {
        val chapterDirs = getChapterDownloadPaths(mangaId, chapterId)
        val folder = chapterDirs.firstNotNullOfOrNull { folder -> File(folder).takeIf { it.exists() } }
        val file = folder?.listFiles()?.sortedBy { it.name }?.get(index)
        val fileType = file!!.name.substringAfterLast(".")
        return Pair(FileInputStream(file).buffered(), "image/$fileType")
    }

    override suspend fun downloadImpl(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit,
    ): Boolean {
        val chapterDirs = getChapterDownloadPaths(mangaId, chapterId)
        val folder = File(chapterDirs.first())

        val downloadSucceeded = super.downloadImpl(download, scope, step)
        if (!downloadSucceeded) {
            return false
        }

        val cacheChapterDir = getChapterCachePath(mangaId, chapterId)
        File(cacheChapterDir).copyRecursively(folder, true)

        return true
    }

    override fun delete(): Boolean {
        val chapterDirs = getChapterDownloadPaths(mangaId, chapterId)
        return chapterDirs.map { File(it).deleteRecursively() }.any { it }
    }

    private fun isExistingFile(
        folder: File,
        fileName: String,
    ): Boolean {
        val existingFile =
            folder.listFiles { file ->
                file.isFile && file.name.startsWith(fileName)
            }?.firstOrNull()
        return existingFile?.exists() == true
    }
}
