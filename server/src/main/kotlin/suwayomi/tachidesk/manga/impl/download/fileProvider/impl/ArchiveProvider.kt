package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import java.io.File
import java.io.InputStream

class ArchiveProvider(mangaId: Int, chapterId: Int) : ChaptersFilesProvider(mangaId, chapterId) {
    override fun getImage(index: Int): Pair<InputStream, String> {
        val cbzPath = getChapterCbzPath(mangaId, chapterId)
        val zipFile = ZipFile(cbzPath)
        val zipEntry = zipFile.entries.toList().sortedWith(compareBy({ it.name }, { it.name }))[index]
        val inputStream = zipFile.getInputStream(zipEntry)
        val fileType = zipEntry.name.substringAfterLast(".")
        return Pair(inputStream.buffered(), "image/$fileType")
    }

    override suspend fun download(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit
    ): Boolean {
        val chapterDir = getChapterDownloadPath(mangaId, chapterId)
        val outputFile = File(getChapterCbzPath(mangaId, chapterId))
        val chapterFolder = File(chapterDir)
        if (outputFile.exists()) handleExistingCbzFile(outputFile, chapterFolder)

        FolderProvider(mangaId, chapterId).download(download, scope, step)

        withContext(Dispatchers.IO) {
            outputFile.createNewFile()
        }

        ZipArchiveOutputStream(outputFile.outputStream()).use { zipOut ->
            if (chapterFolder.isDirectory) {
                chapterFolder.listFiles()?.sortedBy { it.name }?.forEach {
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

        if (chapterFolder.exists() && chapterFolder.isDirectory) {
            chapterFolder.deleteRecursively()
        }

        return true
    }

    override fun delete(): Boolean {
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        if (cbzFile.exists()) return cbzFile.delete()
        return false
    }

    private fun handleExistingCbzFile(cbzFile: File, chapterFolder: File) {
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
