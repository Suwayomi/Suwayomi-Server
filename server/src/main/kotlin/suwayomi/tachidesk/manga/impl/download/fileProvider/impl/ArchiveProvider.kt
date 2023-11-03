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
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPaths
import suwayomi.tachidesk.manga.impl.util.getMangaDownloadDir
import java.io.File
import java.io.InputStream
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

class ArchiveProvider(mangaId: Int, chapterId: Int) : ChaptersFilesProvider(mangaId, chapterId) {
    override fun getImageImpl(index: Int): Pair<InputStream, String> {
        val cbzPaths = getChapterCbzPaths(mangaId, chapterId)
        val zipFile =
            cbzPaths.firstNotNullOfOrNull { folder ->
                val file = File(folder).takeIf { it.exists() } ?: return@firstNotNullOfOrNull null
                ZipFile(file)
            }!!
        val zipEntry = zipFile.entries.toList().sortedWith(compareBy({ it.name }, { it.name }))[index]
        val inputStream = zipFile.getInputStream(zipEntry)
        val fileType = zipEntry.name.substringAfterLast(".")
        return Pair(inputStream.buffered(), "image/$fileType")
    }

    override suspend fun downloadImpl(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit,
    ): Boolean {
        val mangaDownloadFolder = File(getMangaDownloadDir(mangaId))
        val outputFiles = getChapterCbzPaths(mangaId, chapterId).map { File(it) }
        val chapterCacheFolder = File(getChapterCachePath(mangaId, chapterId))
        val existingFile = outputFiles.find { it.exists() }
        if (existingFile != null) handleExistingCbzFile(existingFile, chapterCacheFolder)
        val outputFile = outputFiles.first()

        super.downloadImpl(download, scope, step)

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

        return true
    }

    override fun delete(): Boolean {
        val chapterDirs = getChapterCbzPaths(mangaId, chapterId)
        return chapterDirs.map { Path(it).deleteIfExists() }.any { it }
    }

    private fun handleExistingCbzFile(
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
