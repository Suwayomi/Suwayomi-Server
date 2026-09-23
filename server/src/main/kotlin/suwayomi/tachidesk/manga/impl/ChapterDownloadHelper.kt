package suwayomi.tachidesk.manga.impl

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReady
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ArchiveProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.EpubArchiveProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.FolderProvider
import suwayomi.tachidesk.manga.impl.download.lnreader.LnEpubStore
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import xyz.nulldev.androidcompat.util.SafePath
import java.io.File
import java.io.InputStream

object ChapterDownloadHelper {
    suspend fun getImage(
        mangaId: Int,
        chapterId: Int,
        index: Int,
    ): Pair<InputStream, String> = provider(mangaId, chapterId).getImage().execute(index)

    suspend fun getImageCount(
        mangaId: Int,
        chapterId: Int,
    ): Int = provider(mangaId, chapterId).getImageCount()

    suspend fun delete(
        mangaId: Int,
        chapterId: Int,
    ): Boolean {
        var deletedAny = false
        val epubFile = LnEpubStore.resolveFile(mangaId, chapterId)
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))
        val hadArtifact = epubFile.exists() || cbzFile.exists() || chapterFolder.exists()
        if (epubFile.exists()) {
            deletedAny = LnEpubStore.delete(mangaId, chapterId) || deletedAny
        }
        if (cbzFile.exists()) {
            deletedAny = ArchiveProvider(mangaId, chapterId).delete() || deletedAny
        }
        if (chapterFolder.exists()) {
            deletedAny = FolderProvider(mangaId, chapterId).delete() || deletedAny
        }
        LnEpubStore.healChapterDownloadState(mangaId, chapterId)
        if (hadArtifact) return deletedAny
        return transaction {
            MangaTable
                .selectAll()
                .where { MangaTable.id eq mangaId }
                .firstOrNull()
                ?.get(MangaTable.contentType) !=
                SourceContentType.LIGHT_NOVEL
        }
    }

    /**
     * This function should never be called without calling [getChapterDownloadReady] beforehand.
     */
    suspend fun download(
        mangaId: Int,
        chapterId: Int,
        download: DownloadQueueItem,
        scope: CoroutineScope,
        step: suspend (DownloadQueueItem?, Boolean) -> Unit,
    ): Boolean = provider(mangaId, chapterId).download().execute(download, scope, step)

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private suspend fun provider(
        mangaId: Int,
        chapterId: Int,
    ): ChaptersFilesProvider<*> {
        val chapterFolder = File(getChapterDownloadPath(mangaId, chapterId))
        val cbzFile = File(getChapterCbzPath(mangaId, chapterId))
        if (cbzFile.exists()) return ArchiveProvider(mangaId, chapterId)
        if (!chapterFolder.exists() && serverConfig.downloadAsCbz.value) return ArchiveProvider(mangaId, chapterId)
        return FolderProvider(mangaId, chapterId)
    }

    private suspend fun archiveProvider(
        mangaId: Int,
        chapterId: Int,
    ): ChaptersFilesProvider<*> {
        val isNovel =
            transaction {
                MangaTable
                    .selectAll()
                    .where { MangaTable.id eq mangaId }
                    .firstOrNull()
                    ?.get(MangaTable.contentType) == SourceContentType.LIGHT_NOVEL
            }
        return if (isNovel) EpubArchiveProvider(mangaId, chapterId) else provider(mangaId, chapterId)
    }

    suspend fun getArchiveStreamWithSize(
        mangaId: Int,
        chapterId: Int,
    ): Pair<InputStream, Long> = archiveProvider(mangaId, chapterId).getAsArchiveStream()

    suspend fun getChapterArchiveSize(
        mangaId: Int,
        chapterId: Int,
    ): Long = archiveProvider(mangaId, chapterId).getArchiveSize()

    private fun getChapterWithFileName(
        chapterId: Int,
        extension: String = "cbz",
    ): Pair<ChapterDataClass, String> =
        transaction {
            val row =
                (ChapterTable innerJoin MangaTable)
                    .select(ChapterTable.columns + MangaTable.columns)
                    .where { ChapterTable.id eq chapterId }
                    .firstOrNull() ?: throw IllegalArgumentException("ChapterId $chapterId not found")

            val chapter = ChapterTable.toDataClass(row)
            val mangaTitle = row[MangaTable.title].trim()

            val scanlatorName = chapter.scanlator?.trim()?.takeIf { it.isNotEmpty() }
            val chapterName = chapter.name.trim().takeIf { it.isNotEmpty() }

            val fileName =
                buildString {
                    append(mangaTitle)
                    append(" - ")

                    if (chapterName != null) {
                        append(chapterName)
                    } else if (chapter.chapterNumber >= 0f) {
                        // chapterNumber is stored as Float, drop .0 for whole numbers.
                        val formatNumber =
                            if (chapter.chapterNumber % 1 == 0f) {
                                chapter.chapterNumber.toInt().toString()
                            } else {
                                chapter.chapterNumber.toString()
                            }
                        append("#$formatNumber")
                    } else {
                        // Fallback when neither name nor valid chapter number exists
                        append("#${chapter.index}")
                    }

                    if (scanlatorName != null) {
                        append(" [")
                        append(scanlatorName)
                        append("]")
                    }
                    append(".$extension")
                }

            // Sanitize filename for OS compatibility
            val safeFileName = SafePath.buildValidFilename(fileName)

            Pair(chapter, safeFileName)
        }

    suspend fun getCbzForDownload(
        chapterId: Int,
        markAsRead: Boolean?,
    ): Triple<InputStream, String, Long> {
        val (chapterData, _) = getChapterWithFileName(chapterId)
        val prov = archiveProvider(chapterData.mangaId, chapterData.id)
        val ext = if (prov is EpubArchiveProvider) "epub" else "cbz"
        val (_, fileName) = getChapterWithFileName(chapterId, ext)
        val (stream, length) = prov.getAsArchiveStream()

        if (markAsRead == true) {
            Chapter.modifyChapter(
                chapterData.mangaId,
                chapterData.index,
                isRead = true,
                isBookmarked = null,
                markPrevRead = null,
                lastPageRead = null,
            )
        }

        return Triple(stream, fileName, length)
    }

    suspend fun getCbzMetadataForDownload(chapterId: Int): Pair<String, Long> { // fileName, fileSize
        val (chapterData, _) = getChapterWithFileName(chapterId)
        val prov = archiveProvider(chapterData.mangaId, chapterData.id)
        val ext = if (prov is EpubArchiveProvider) "epub" else "cbz"
        val (_, fileName) = getChapterWithFileName(chapterId, ext)
        val fileSize = prov.getArchiveSize()
        if (prov is EpubArchiveProvider && fileSize <= 0L) {
            throw NoSuchElementException("Chapter download not found for chapter ID: $chapterId")
        }
        return Pair(fileName, fileSize)
    }
}
