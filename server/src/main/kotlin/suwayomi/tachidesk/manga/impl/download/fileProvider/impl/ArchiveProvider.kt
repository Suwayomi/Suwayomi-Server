package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileType
import suwayomi.tachidesk.manga.impl.download.fileProvider.TMP_FILE_SUFFIX
import suwayomi.tachidesk.manga.impl.download.fileProvider.moveFileReplacing
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.getMangaDownloadDir
import suwayomi.tachidesk.manga.impl.util.storage.FileDeletionHelper
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.util.zip.Deflater

private val applicationDirs: ApplicationDirs by injectLazy()

class ArchiveProvider(
    mangaId: Int,
    chapterId: Int,
) : ChaptersFilesProvider<FileType.ZipFile>(mangaId, chapterId) {
    override suspend fun getImageFiles(): List<FileType.ZipFile> {
        val cbzPath = getChapterCbzPath(mangaId, chapterId)
        return try {
            ZipFile
                .builder()
                .setFile(cbzPath)
                .get()
                .use { zipFile ->
                    // collect entries while the archive is still open; the returned
                    // ZipArchiveEntry objects stay valid for getImageInputStream, which
                    // opens its own ZipFile instance
                    zipFile.entries
                        .toList()
                        .filter { !it.isDirectory && !it.name.endsWith(TMP_FILE_SUFFIX) }
                        .map { FileType.ZipFile(it) }
                }
        } catch (e: NoSuchFileException) {
            // provider contract: "no download present" surfaces as NoSuchElementException
            // (same as FolderProvider), so callers can tell it apart from a real read
            // failure. Only an absent archive maps here — a check-then-open exists()
            // guard would also report EACCES/dangling-stat results as absent and let
            // callers wipe isDownloaded for a file that exists but cannot be opened;
            // every other IOException keeps its type and is treated as a real failure
            throw NoSuchElementException("CBZ archive does not exist", e)
        }
    }

    override suspend fun getImageInputStream(image: FileType.ZipFile): InputStream {
        val zipFile =
            ZipFile
                .builder()
                .setFile(getChapterCbzPath(mangaId, chapterId))
                .get()
        val entryStream =
            try {
                zipFile.getInputStream(image.entry)
            } catch (e: Exception) {
                zipFile.close()
                throw e
            }
        if (entryStream == null) {
            zipFile.close()
            throw IOException("Entry \"${image.entry.name}\" not found in CBZ for chapter $chapterId")
        }
        // Closing the returned stream has to release the ZipFile (and with it the
        // SeekableByteChannel/file descriptor) as well, otherwise every page read
        // leaks a file descriptor and on Windows the CBZ stays locked for deletion.
        return object : FilterInputStream(entryStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    zipFile.close()
                }
            }
        }
    }

    override suspend fun extractExistingDownload() {
        val outputFile = File(getChapterCbzPath(mangaId, chapterId))
        val chapterDownloadFolder = File(getChapterDownloadPath(mangaId, chapterId))

        if (!outputFile.exists()) {
            return
        }

        extractCbzFile(outputFile, chapterDownloadFolder)
    }

    override suspend fun handleSuccessfulDownload() {
        val mangaDownloadFolder = File(getMangaDownloadDir(mangaId))
        val outputFile = File(getChapterCbzPath(mangaId, chapterId))
        val chapterCacheFolder = File(getChapterCachePath(mangaId, chapterId))

        withContext(Dispatchers.IO) {
            mangaDownloadFolder.mkdirs()
        }

        // write to a sibling tmp file and move it over the destination so a crash or a
        // full disk cannot leave a truncated .cbz behind (which `provider()` would
        // happily adopt as the finished download on every subsequent access)
        val tmpOutputFile = File("${outputFile.path}$TMP_FILE_SUFFIX")

        try {
            ZipArchiveOutputStream(tmpOutputFile.outputStream()).use { zipOut ->
                zipOut.setMethod(ZipArchiveOutputStream.DEFLATED)
                zipOut.setLevel(Deflater.DEFAULT_COMPRESSION)
                if (chapterCacheFolder.isDirectory) {
                    chapterCacheFolder
                        .listFiles()
                        .orEmpty()
                        .filter { it.isFile && !it.name.endsWith(TMP_FILE_SUFFIX) }
                        .sortedBy { it.name }
                        .forEach {
                            val entry = ZipArchiveEntry(it.name)
                            entry.time = 0L
                            try {
                                zipOut.putArchiveEntry(entry)
                                it.inputStream().use { inputStream ->
                                    inputStream.copyTo(zipOut)
                                }
                            } finally {
                                zipOut.closeArchiveEntry()
                            }
                        }
                }
            }
            moveFileReplacing(tmpOutputFile, outputFile)
        } catch (e: Exception) {
            tmpOutputFile.delete()
            throw e
        }

        // the chapter cache folder is cleaned up by the caller (ChaptersFilesProvider#downloadImpl)
    }

    override suspend fun delete(): Boolean {
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        if (!cbzFile.exists()) {
            return true
        }

        val cbzDeleted = cbzFile.delete()
        if (cbzDeleted) {
            transaction {
                ChapterUserTable.update({ ChapterUserTable.chapter eq chapterId }) {
                    it[koreaderHash] = null
                }
            }
        }
        FileDeletionHelper.cleanupParentFoldersFor(cbzFile, applicationDirs.mangaDownloadsRoot)
        return cbzDeleted
    }

    override suspend fun getAsArchiveStream(): Pair<InputStream, Long> {
        val cbzFile =
            File(getChapterCbzPath(mangaId, chapterId))
                .takeIf { it.exists() }
                ?: throw IllegalArgumentException("CBZ file not found for chapter ID: $chapterId (Manga ID: $mangaId)")

        return cbzFile.inputStream() to cbzFile.length()
    }

    override suspend fun getArchiveSize(): Long {
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        return if (cbzFile.exists()) cbzFile.length() else 0L
    }

    private fun extractCbzFile(
        cbzFile: File,
        chapterFolder: File,
    ) {
        try {
            if (!chapterFolder.exists()) chapterFolder.mkdirs()
            val canonicalFolderPath = chapterFolder.canonicalFile.path + File.separator
            // random access validates the central directory — a truncated archive fails
            // right here instead of halfway through streaming extraction
            ZipFile
                .builder()
                .setFile(cbzFile)
                .get()
                .use { zipFile ->
                    val zipEntries = zipFile.entries.toList()

                    // zip-slip: validate every entry first so a crafted CBZ cannot even
                    // partially extract before the escaping entry is hit
                    zipEntries.forEach { zipEntry ->
                        val file = File(chapterFolder, zipEntry.name)
                        if (!file.canonicalFile.path.startsWith(canonicalFolderPath)) {
                            throw IOException("CBZ entry \"${zipEntry.name}\" would escape the chapter folder")
                        }
                    }

                    zipEntries.forEach { zipEntry ->
                        val file = File(chapterFolder, zipEntry.name)
                        if (zipEntry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            // write to a tmp file and rename, so an interrupted extraction does
                            // not leave a truncated page that counts as "already downloaded"
                            val tmpFile = File("${file.path}$TMP_FILE_SUFFIX")
                            try {
                                val entryInputStream =
                                    zipFile.getInputStream(zipEntry)
                                        ?: throw IOException("CBZ entry \"${zipEntry.name}\" cannot be read")
                                val copied =
                                    tmpFile.outputStream().use { outputStream ->
                                        entryInputStream.use { it.copyTo(outputStream) }
                                    }
                                // the central-directory size is authoritative: a cbz cut in
                                // half must not silently produce a truncated "complete" page
                                if (zipEntry.size >= 0 && copied != zipEntry.size) {
                                    throw IOException(
                                        "CBZ entry \"${zipEntry.name}\" is truncated ($copied/${zipEntry.size} bytes)",
                                    )
                                }
                                moveFileReplacing(tmpFile, file)
                            } catch (e: Exception) {
                                tmpFile.delete()
                                throw e
                            }
                        }
                    }
                }
            cbzFile.delete()
        } catch (e: Exception) {
            // isolate the broken CBZ so the next retry doesn't trip over the same file
            // again; left in place it would be adopted as the chapter download forever
            if (cbzFile.delete()) {
                logger.warn { "Deleted unreadable CBZ ${cbzFile.path} after extraction failed" }
            }
            throw e
        }
    }
}
