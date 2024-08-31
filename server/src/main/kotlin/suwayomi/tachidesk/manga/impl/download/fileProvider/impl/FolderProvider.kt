package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileType.RegularFile
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.storage.FileDeletionHelper
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File
import java.io.FileInputStream

private val applicationDirs by DI.global.instance<ApplicationDirs>()

/*
* Provides downloaded files when pages were downloaded into folders
* */
class FolderProvider(mangaId: Int, chapterId: Int) : ChaptersFilesProvider<RegularFile>(mangaId, chapterId) {
    override fun getImageFiles(): List<RegularFile> {
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))

        if (!chapterFolder.exists()) {
            throw Exception("download folder does not exist")
        }

        return chapterFolder.listFiles().orEmpty().toList().map(::RegularFile)
    }

    override fun getImageInputStream(image: RegularFile): FileInputStream {
        return FileInputStream(image.file)
    }

    override fun extractExistingDownload() {
        // nothing to do
    }

    override suspend fun handleSuccessfulDownload() {
        val chapterDir = getChapterDownloadPath(mangaId, chapterId)
        val folder = File(chapterDir)

        val cacheChapterDir = getChapterCachePath(mangaId, chapterId)
        File(cacheChapterDir).copyRecursively(folder, true)
    }

    override fun delete(): Boolean {
        val chapterDirPath = getChapterDownloadPath(mangaId, chapterId)
        val chapterDir = File(chapterDirPath)
        if (!chapterDir.exists()) {
            return true
        }

        val chapterDirDeleted = chapterDir.deleteRecursively()
        FileDeletionHelper.cleanupParentFoldersFor(chapterDir, applicationDirs.mangaDownloadsRoot)
        return chapterDirDeleted
    }
}
