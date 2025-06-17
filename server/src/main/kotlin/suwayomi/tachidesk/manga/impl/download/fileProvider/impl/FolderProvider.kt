package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileType.RegularFile
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.storage.FileDeletionHelper
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val applicationDirs: ApplicationDirs by injectLazy()

/*
* Provides downloaded files when pages were downloaded into folders
* */
class FolderProvider(
    path: String,
    mangaId: Int,
    chapterId: Int,
) : ChaptersFilesProvider<RegularFile>(path, mangaId, chapterId) {
    override fun getImageFiles(): List<RegularFile> {
        val chapterFolder = File(path)

        if (!chapterFolder.exists()) {
            throw Exception("download folder does not exist")
        }

        return chapterFolder
            .listFiles()
            .orEmpty()
            .toList()
            .map(::RegularFile)
    }

    override fun getImageInputStream(image: RegularFile): FileInputStream = FileInputStream(image.file)

    override fun extractExistingDownload() {
        // nothing to do
    }

    override suspend fun handleSuccessfulDownload() {
        val folder = File(path)

        val cacheChapterDir = getChapterCachePath(mangaId, chapterId)
        File(cacheChapterDir).copyRecursively(folder, true)
    }

    override fun delete(): Boolean {
        val folder = File(path)
        if (!folder.exists()) {
            return true
        }

        val chapterDirDeleted = folder.deleteRecursively()
        FileDeletionHelper.cleanupParentFoldersFor(folder, applicationDirs.mangaDownloadsRoot)
        return chapterDirDeleted
    }

    override fun getAsArchiveStream(): Pair<InputStream, Long> {
        val chapterDir = File(path)

        if (!chapterDir.exists() || !chapterDir.isDirectory || chapterDir.listFiles().isNullOrEmpty()) {
            throw IllegalArgumentException("Invalid folder to create CBZ for chapter ID: $chapterId")
        }

        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(byteArrayOutputStream)).use { zipOutputStream ->
            chapterDir
                .listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { imageFile ->
                    FileInputStream(imageFile).use { fileInputStream ->
                        val zipEntry = ZipEntry(imageFile.name)
                        zipOutputStream.putNextEntry(zipEntry)
                        fileInputStream.copyTo(zipOutputStream)
                        zipOutputStream.closeEntry()
                    }
                }
        }

        val zipData = byteArrayOutputStream.toByteArray()
        return ByteArrayInputStream(zipData) to zipData.size.toLong()
    }
}
