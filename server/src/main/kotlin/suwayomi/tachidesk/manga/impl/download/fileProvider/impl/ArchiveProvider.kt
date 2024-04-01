package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getMangaDownloadDir
import suwayomi.tachidesk.manga.impl.util.storage.FileDeletionHelper
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File
import java.io.InputStream

private val applicationDirs by DI.global.instance<ApplicationDirs>()

class ArchiveProvider(mangaId: Int, chapterId: Int) : ChaptersFilesProvider(mangaId, chapterId) {
    override fun getImageImpl(index: Int): Pair<InputStream, String> {
        val cbzPath = getChapterCbzPath(mangaId, chapterId)
        val zipFile = ZipFile(cbzPath)
        val zipEntry = zipFile.entries.toList().sortedWith(compareBy({ it.name }, { it.name }))[index]
        val inputStream = zipFile.getInputStream(zipEntry)
        val fileType = zipEntry.name.substringAfterLast(".")
        return Pair(inputStream.buffered(), "image/$fileType")
    }

    override fun extractExistingDownload() {
        val outputFile = File(getChapterCbzPath(mangaId, chapterId))
        val chapterCacheFolder = File(getChapterCachePath(mangaId, chapterId))

        if (!outputFile.exists()) {
            return
        }

        extractCbzFile(outputFile, chapterCacheFolder)
    }

    override suspend fun handleSuccessfulDownload() {
        val mangaDownloadFolder = File(getMangaDownloadDir(mangaId))
        val outputFile = File(getChapterCbzPath(mangaId, chapterId))
        val chapterCacheFolder = File(getChapterCachePath(mangaId, chapterId))

        withContext(Dispatchers.IO) {
            mangaDownloadFolder.mkdirs()
            outputFile.createNewFile()
        }

        ZipArchiveOutputStream(outputFile.outputStream()).use { zipOut ->
            if (chapterCacheFolder.isDirectory) {
                chapterCacheFolder.listFiles()?.sortedBy { it.name }?.forEach {
                    val entry = ZipArchiveEntry(it.name)
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

        if (chapterCacheFolder.exists() && chapterCacheFolder.isDirectory) {
            chapterCacheFolder.deleteRecursively()
        }
    }

    override fun delete(): Boolean {
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        if (!cbzFile.exists()) {
            return true
        }

        val cbzDeleted = cbzFile.delete()
        FileDeletionHelper.cleanupParentFoldersFor(cbzFile, applicationDirs.mangaDownloadsRoot)
        return cbzDeleted
    }

    private fun extractCbzFile(
        cbzFile: File,
        chapterFolder: File,
    ) {
        if (!chapterFolder.exists()) chapterFolder.mkdirs()
        ZipArchiveInputStream(cbzFile.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                val file = File(chapterFolder, zipEntry.name)
                if (!file.exists()) {
                    file.parentFile.mkdirs()
                    file.createNewFile()
                }
                file.outputStream().use { outputStream ->
                    zipInputStream.copyTo(outputStream)
                }
                zipEntry = zipInputStream.nextEntry
            }
        }
        cbzFile.delete()
    }
}
