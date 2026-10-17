package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileType.RegularFile
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.storage.FileDeletionHelper
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater

private val applicationDirs: ApplicationDirs by injectLazy()

/*
* Provides downloaded files when pages were downloaded into folders
* */
class FolderProvider(
    mangaId: Int,
    chapterId: Int,
) : ChaptersFilesProvider<RegularFile>(mangaId, chapterId) {
    override suspend fun getImageFiles(): List<RegularFile> {
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))

        if (!chapterFolder.exists()) {
            throw NoSuchElementException("download folder does not exist")
        }

        return chapterFolder
            .listFiles()
            .orEmpty()
            .toList()
            .map(::RegularFile)
    }

    override suspend fun getImageInputStream(image: RegularFile): FileInputStream = FileInputStream(image.file)

    override suspend fun extractExistingDownload() {
        // nothing to do
    }

    override suspend fun handleSuccessfulDownload() {
        val chapterDir = getChapterDownloadPath(mangaId, chapterId)
        val folder = File(chapterDir)

        val cacheChapterDir = getChapterCachePath(mangaId, chapterId)
        File(cacheChapterDir).copyRecursively(folder, true)
    }

    override suspend fun delete(): Boolean {
        val chapterDirPath = getChapterDownloadPath(mangaId, chapterId)
        val chapterDir = File(chapterDirPath)
        if (!chapterDir.exists()) {
            return true
        }

        val chapterDirDeleted = chapterDir.deleteRecursively()
        if (chapterDirDeleted) {
            transaction {
                ChapterUserTable.update({ ChapterUserTable.chapter eq chapterId }) {
                    it[koreaderHash] = null
                }
            }
        }
        FileDeletionHelper.cleanupParentFoldersFor(chapterDir, applicationDirs.mangaDownloadsRoot)
        return chapterDirDeleted
    }

    override suspend fun getAsArchiveStream(): Pair<InputStream, Long> {
        val chapterDir = File(getChapterDownloadPath(mangaId, chapterId))

        if (!chapterDir.exists() || !chapterDir.isDirectory || chapterDir.listFiles().isNullOrEmpty()) {
            throw IllegalArgumentException("Invalid folder to create CBZ for chapter ID: $chapterId")
        }

        return archiveToTempFile(chapterDir, chapterId)
    }

    override suspend fun getArchiveSize(): Long {
        val chapterDir = File(getChapterDownloadPath(mangaId, chapterId))
        if (!chapterDir.exists() || !chapterDir.isDirectory) return 0L
        // Approximation: actual CBZ size is slightly larger due to ZIP metadata, but sufficient for Content-Length header.
        return chapterDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    companion object {
        /**
         * Builds the archive in a temp file deleted once the returned stream is closed, so a chapter is never held in memory.
         * KOReader's binary checksum hashes these bytes, so they must stay the same.
         */
        internal fun archiveToTempFile(
            chapterDir: File,
            chapterId: Int,
        ): Pair<InputStream, Long> {
            val archiveFile = File.createTempFile("chapter-$chapterId-", ".cbz")
            try {
                writeArchive(chapterDir, BufferedOutputStream(FileOutputStream(archiveFile)))
            } catch (e: Exception) {
                archiveFile.delete()
                throw e
            }

            val inputStream =
                object : FileInputStream(archiveFile) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            archiveFile.delete()
                        }
                    }
                }
            return inputStream to archiveFile.length()
        }

        // a stream rather than a file so the entries keep their data descriptors, as when the archive was built in memory
        private fun writeArchive(
            chapterDir: File,
            outputStream: OutputStream,
        ) {
            ZipArchiveOutputStream(outputStream).use { zipOutputStream ->
                zipOutputStream.setMethod(ZipArchiveOutputStream.DEFLATED)
                zipOutputStream.setLevel(Deflater.DEFAULT_COMPRESSION)

                chapterDir
                    .listFiles()
                    ?.filter { it.isFile }
                    ?.sortedBy { it.name }
                    ?.forEach { imageFile ->
                        FileInputStream(imageFile).use { fileInputStream ->
                            val zipEntry = ZipArchiveEntry(imageFile.name)
                            zipEntry.time = 0L
                            zipOutputStream.putArchiveEntry(zipEntry)
                            fileInputStream.copyTo(zipOutputStream)
                            zipOutputStream.closeArchiveEntry()
                        }
                    }
            }
        }
    }
}
