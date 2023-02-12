package suwayomi.tachidesk.manga.impl.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveProvider(mangaId: Int, chapterId: Int) : DownloadedFilesProvider(mangaId, chapterId) {
    override fun getImage(index: Int): Pair<InputStream, String> {
        val cbzPath = getChapterCbzPath(mangaId, chapterId)
        val zipFile = ZipFile(cbzPath)
        val zipEntry = zipFile.entries().toList().sortedWith(compareBy({ it.name }, { it.name }))[index]
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

        withContext(Dispatchers.IO) {
            outputFile.createNewFile()
        }

        FolderProvider(mangaId, chapterId).download(download, scope, step)

        ZipOutputStream(outputFile.outputStream()).use { zipOut ->
            if (chapterFolder.isDirectory) {
                chapterFolder.listFiles()?.sortedBy { it.name }?.forEach {
                    val entry = ZipEntry(it.name)
                    try {
                        zipOut.putNextEntry(entry)
                        it.inputStream().use { inputStream ->
                            inputStream.copyTo(zipOut)
                        }
                    } finally {
                        zipOut.closeEntry()
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
        ZipInputStream(cbzFile.inputStream()).use { zipInputStream ->
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
