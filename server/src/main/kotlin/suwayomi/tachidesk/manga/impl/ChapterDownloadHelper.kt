package suwayomi.tachidesk.manga.impl

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
import java.io.File
import java.io.IOException
import java.io.InputStream

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

    suspend fun getCbzDownload(chapterId: Int): Triple<InputStream, String, String> {
        val (chapterData, mangaTitle) = transaction {
            val row = (ChapterTable innerJoin MangaTable)
                .select(ChapterTable.columns + MangaTable.columns)
                .where { ChapterTable.id eq chapterId }
                .firstOrNull() ?: throw Exception("Chapter not found")
            val chapter = ChapterTable.toDataClass(row)
            val title = row[MangaTable.title]
            Pair(chapter, title)
        }

        val provider = provider(chapterData.mangaId, chapterData.id)
        return if (provider is ArchiveProvider) {
            val cbzFile = File(getChapterCbzPath(chapterData.mangaId, chapterData.id))
            val fileName = "$mangaTitle - [${chapterData.scanlator}] ${chapterData.name}.cbz"
            Triple(cbzFile.inputStream(), "application/vnd.comicbook+zip", fileName)
        } else {
            throw IOException("Chapter not available as CBZ")
        }
    }
}
