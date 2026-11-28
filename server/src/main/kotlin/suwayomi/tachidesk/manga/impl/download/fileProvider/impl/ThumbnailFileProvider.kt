package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.download.fileProvider.DownloadedFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileDownload0Args
import suwayomi.tachidesk.manga.impl.download.fileProvider.RetrieveFile0Args
import suwayomi.tachidesk.manga.impl.util.getThumbnailDownloadPath
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getCachedImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.InputStream

class MissingThumbnailException : Exception("No thumbnail found")

private val applicationDirs: ApplicationDirs by injectLazy()

class ThumbnailFileProvider(
    val mangaId: Int,
) : DownloadedFilesProvider {
    private fun getFilePath(): String? {
        val thumbnailDir = applicationDirs.thumbnailDownloadsRoot
        val fileName = mangaId.toString()
        return ImageResponse.findFileNameStartingWith(thumbnailDir, fileName)
    }

    fun getImageImpl(): Pair<InputStream, String> {
        val filePathWithoutExt = getThumbnailDownloadPath(mangaId)
        val filePath = getFilePath()

        if (filePath.isNullOrEmpty()) {
            throw MissingThumbnailException()
        }

        return getCachedImageResponse(filePath, filePathWithoutExt)
    }

    override suspend fun getImage(): RetrieveFile0Args = RetrieveFile0Args(::getImageImpl)

    // the per-manga fetch lock lives in Manga — existence is rechecked inside it
    private suspend fun downloadImpl(): Boolean = Manga.downloadThumbnail(mangaId)

    override fun download(): FileDownload0Args = FileDownload0Args(::downloadImpl)

    override suspend fun delete(): Boolean = Manga.clearDownloadedThumbnail(mangaId)
}
