package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.download.fileProvider.ChaptersFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileType
import suwayomi.tachidesk.manga.impl.download.lnreader.LnEpubStore
import suwayomi.tachidesk.manga.impl.util.storage.FileDeletionHelper
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.io.File
import java.io.InputStream

class EpubArchiveProvider(
    mangaId: Int,
    chapterId: Int,
) : ChaptersFilesProvider<FileType.ZipFile>(mangaId, chapterId) {
    val file: File
        get() = LnEpubStore.getFile(mangaId, chapterId)

    override suspend fun getAsArchiveStream(): Pair<InputStream, Long> {
        val f =
            LnEpubStore.resolveFile(mangaId, chapterId).takeIf { LnEpubStore.existsValid(mangaId, chapterId) }
                ?: throw NoSuchElementException("EPUB not found for chapter ID: $chapterId")
        return f.inputStream() to f.length()
    }

    override suspend fun getArchiveSize(): Long =
        LnEpubStore.resolveFile(mangaId, chapterId).takeIf { LnEpubStore.existsValid(mangaId, chapterId) }?.length() ?: 0L

    override suspend fun delete(): Boolean {
        var deleted = true
        if (file.exists()) {
            deleted = file.delete()
        }

        // Clean up any stale temp files for this chapter
        val mangaDir = LnEpubStore.getMangaDir(mangaId)
        mangaDir.listFiles()?.forEach { f ->
            if (f.name.startsWith(".tmp_${chapterId}_") && f.name.endsWith(".epub")) {
                f.delete()
            }
        }

        if (deleted) {
            transaction {
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[isDownloaded] = false
                    it[koreaderHash] = null
                }
            }
            FileDeletionHelper.cleanupParentFoldersFor(file, LnEpubStore.downloadsRoot.absolutePath)
        }
        return deleted
    }

    override suspend fun getImageFiles(): List<FileType.ZipFile> =
        throw UnsupportedOperationException("Novel chapters do not have discrete image pages")

    override suspend fun getImageInputStream(image: FileType.ZipFile): InputStream = throw UnsupportedOperationException()

    override suspend fun extractExistingDownload() {}

    override suspend fun handleSuccessfulDownload() {}
}
