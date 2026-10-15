package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileType.RegularFile
import suwayomi.tachidesk.manga.impl.download.fileProvider.TMP_FILE_SUFFIX
import suwayomi.tachidesk.manga.impl.download.fileProvider.moveFileReplacing
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.storage.FileDeletionHelper
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
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
            .filter { it.isFile && !it.name.endsWith(TMP_FILE_SUFFIX) }
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
        val cacheFolder = File(cacheChapterDir)

        if (!cacheFolder.isDirectory) {
            return
        }

        folder.mkdirs()
        // no recursive copy: leftover ".tmp" files of interrupted page downloads
        // must not be copied into the finished chapter folder
        cacheFolder
            .listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.endsWith(TMP_FILE_SUFFIX) }
            .forEach { it.copyTo(File(folder, it.name), overwrite = true) }
    }

    override suspend fun delete(): Boolean {
        deleteCachedArchive()

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
        val archiveFile =
            getOrCreateArchiveFile()
                ?: throw IllegalArgumentException("Invalid folder to create CBZ for chapter ID: $chapterId")

        return FileInputStream(archiveFile) to archiveFile.length()
    }

    override suspend fun getArchiveSize(): Long {
        val chapterDir = File(getChapterDownloadPath(mangaId, chapterId))
        if (!chapterDir.exists() || !chapterDir.isDirectory) return 0L

        // report the actual zipped size so the Content-Length of a HEAD response matches
        // the body a GET would return; the archive is cached next to the chapter cache
        // folder so this stays cheap for OPDS feeds listing many chapters
        return getOrCreateArchiveFile()?.length() ?: 0L
    }

    /**
     * A cheap fingerprint of the chapter folder content; when it changes a previously
     * generated archive is stale and gets rebuilt.
     */
    private fun archiveFingerprint(chapterDir: File): String =
        chapterDir
            .listFiles()
            .orEmpty()
            .filter { it.isFile && !it.name.endsWith(TMP_FILE_SUFFIX) }
            .sortedBy { it.name }
            .joinToString("\n") { "${it.name}:${it.length()}:${it.lastModified()}" }

    /**
     * Compresses the chapter folder into a zip archive stored next to the chapter cache
     * folder and returns it, reusing a previously generated archive while the folder
     * content is unchanged. The archive is written to a tmp file and moved in place, so
     * readers never observe a half-written file.
     */
    private suspend fun getOrCreateArchiveFile(): File? {
        val chapterDir = File(getChapterDownloadPath(mangaId, chapterId))

        if (!chapterDir.exists() || !chapterDir.isDirectory || chapterDir.listFiles().isNullOrEmpty()) {
            return null
        }

        val archiveFile = File(getChapterCachePath(mangaId, chapterId) + ARCHIVE_FILE_SUFFIX)
        val signatureFile = File(archiveFile.path + SIGNATURE_FILE_SUFFIX)

        return archiveMutex(mangaId, chapterId).withLock {
            val fingerprint = archiveFingerprint(chapterDir)
            if (archiveFile.isFile &&
                signatureFile.isFile &&
                runCatching { signatureFile.readText() }.getOrNull() == fingerprint
            ) {
                return@withLock archiveFile
            }

            archiveFile.parentFile?.mkdirs()
            val tmpFile = File(archiveFile.path + TMP_FILE_SUFFIX)
            try {
                ZipArchiveOutputStream(BufferedOutputStream(tmpFile.outputStream())).use { zipOutputStream ->
                    zipOutputStream.setMethod(ZipArchiveOutputStream.DEFLATED)
                    zipOutputStream.setLevel(Deflater.DEFAULT_COMPRESSION)

                    chapterDir
                        .listFiles()
                        .orEmpty()
                        .filter { it.isFile && !it.name.endsWith(TMP_FILE_SUFFIX) }
                        .sortedBy { it.name }
                        .forEach { imageFile ->
                            FileInputStream(imageFile).use { fileInputStream ->
                                val zipEntry = ZipArchiveEntry(imageFile.name)
                                zipEntry.time = 0L
                                zipOutputStream.putArchiveEntry(zipEntry)
                                fileInputStream.copyTo(zipOutputStream)
                                zipOutputStream.closeArchiveEntry()
                            }
                        }
                }
                moveFileReplacing(tmpFile, archiveFile)
                signatureFile.writeText(fingerprint)
            } catch (e: Exception) {
                tmpFile.delete()
                throw e
            }
            archiveFile
        }
    }

    private suspend fun deleteCachedArchive() {
        val archiveFile = File(getChapterCachePath(mangaId, chapterId) + ARCHIVE_FILE_SUFFIX)
        archiveFile.delete()
        File(archiveFile.path + SIGNATURE_FILE_SUFFIX).delete()
        File(archiveFile.path + TMP_FILE_SUFFIX).delete()
    }

    companion object {
        private const val ARCHIVE_FILE_SUFFIX = ".cbz"
        private const val SIGNATURE_FILE_SUFFIX = ".sig"

        // bounded by the number of downloaded chapters; entries stay resident
        private val archiveMutexes = ConcurrentHashMap<Pair<Int, Int>, Mutex>()

        private fun archiveMutex(
            mangaId: Int,
            chapterId: Int,
        ): Mutex = archiveMutexes.getOrPut(mangaId to chapterId) { Mutex() }
    }
}
