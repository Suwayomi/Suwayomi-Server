package suwayomi.tachidesk.manga.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ArchiveProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.FolderProvider
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipOutputStream

object ChapterDownloadHelper {
    fun getImage(
        mangaId: Int,
        chapterId: Int,
        index: Int,
    ): Pair<InputStream, String> = provider(mangaId, chapterId).getImage().execute(index)

    fun delete(
        mangaId: Int,
        chapterId: Int,
    ): Boolean = provider(mangaId, chapterId).delete()

    suspend fun download(
        mangaId: Int,
        chapterId: Int,
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit,
    ): Boolean = provider(mangaId, chapterId).download().execute(download, scope, step)

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private fun provider(
        mangaId: Int,
        chapterId: Int,
    ): ChaptersFilesProvider<*> {
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        if (cbzFile.exists()) return ArchiveProvider(mangaId, chapterId)
        if (!chapterFolder.exists() && serverConfig.downloadAsCbz.value) return ArchiveProvider(mangaId, chapterId)
        return FolderProvider(mangaId, chapterId)
    }

    fun getCbzForDownload(chapterId: Int): Triple<InputStream, String, String> {
        val (chapterData, mangaTitle) =
            transaction {
                val row =
                    (ChapterTable innerJoin MangaTable)
                        .select(ChapterTable.columns + MangaTable.columns)
                        .where { ChapterTable.id eq chapterId }
                        .firstOrNull() ?: throw Exception("Chapter not found")
                val chapter = ChapterTable.toDataClass(row)
                val title = row[MangaTable.title]
                Pair(chapter, title)
            }

        val fileName = "$mangaTitle - [${chapterData.scanlator}] ${chapterData.name}.cbz"

        val cbzFile =
            getCbzForDownloadHelper(chapterData.mangaId, chapterData.id)
                ?: throw IllegalStateException("CBZ could not be created for chapter ${chapterData.id}")

        return Triple(cbzFile.inputStream(), "application/vnd.comicbook+zip", fileName)
    }

    fun getCbzForDownloadHelper(
        mangaId: Int,
        chapterId: Int,
    ): File? {
        val provider = provider(mangaId, chapterId)
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        return when (provider) {
            is ArchiveProvider -> cbzFile

            is FolderProvider -> {
                val folderPath = File(getChapterDownloadPath(mangaId, chapterId))
                createCbzFromFolder(folderPath, cbzFile)
                cbzFile
            }
            else -> null
        }
    }

    private fun createCbzFromFolder(
        imageFolder: File,
        cbzFile: File,
    ) {
        val log = KotlinLogging.logger { "${this::class.simpleName}::createCbzFromFolder" }

        if (!imageFolder.exists() || !imageFolder.isDirectory) {
            throw IllegalArgumentException("Invalid Folder to create cbz")
        }

        log.debug { "cbz process: started (Files: ${imageFolder.listFiles()?.size ?: 0})" }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(cbzFile))).use { zipOutputStream ->
            imageFolder
                .listFiles()
                ?.filter {
                    it.isFile
                }?.sortedBy { it.name }
                ?.forEach { imageFile ->

                    FileInputStream(imageFile).use { fileInputStream ->
                        val zipEntry = java.util.zip.ZipEntry(imageFile.name)
                        zipOutputStream.putNextEntry(zipEntry)
                        fileInputStream.copyTo(zipOutputStream)
                        zipOutputStream.closeEntry()
                    }
                }
        }

        log.debug { "cbz process: finished (${cbzFile.absolutePath})" }
    }
}
