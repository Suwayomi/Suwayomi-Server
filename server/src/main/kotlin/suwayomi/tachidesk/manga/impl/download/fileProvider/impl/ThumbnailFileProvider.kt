package suwayomi.tachidesk.manga.impl.download.fileProvider.impl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.download.fileProvider.DownloadedFilesProvider
import suwayomi.tachidesk.manga.impl.download.fileProvider.FileDownload0Args
import suwayomi.tachidesk.manga.impl.download.fileProvider.RetrieveFile0Args
import suwayomi.tachidesk.manga.impl.download.fileProvider.TMP_FILE_SUFFIX
import suwayomi.tachidesk.manga.impl.util.getThumbnailDownloadPath
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getCachedImageResponse
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class MissingThumbnailException : Exception("No thumbnail found")

/**
 * Marks a fetch-stage failure — "the request to the source itself failed" — with
 * the original fetch error as the cause. Two callers key off the marker:
 * - negative cache: the same wrapper replays a remembered failure, telling the
 *   "cache this error once" paths apart from "replay it and keep its TTL".
 * - no double fetch: the in-library path skips its direct-fetch fallback for a
 *   marked failure — the fallback would only repeat the same request against
 *   the same dead source. A failure after a successful fetch (e.g. persisting
 *   to the downloads dir — the image is already in the temp cache by then, so
 *   the fallback is a file read, not another request) keeps the fallback.
 */
class ThumbnailFetchFailedException(
    cause: Throwable,
) : Exception(cause)

private val applicationDirs: ApplicationDirs by injectLazy()

class ThumbnailFileProvider(
    val mangaId: Int,
) : DownloadedFilesProvider {
    private fun getFilePath(): String? {
        val thumbnailDir = applicationDirs.thumbnailDownloadsRoot
        val fileName = mangaId.toString()
        // a leftover ".tmp" file belongs to an interrupted download and doesn't
        // count as a saved thumbnail
        return ImageResponse
            .findFileNameStartingWith(thumbnailDir, fileName)
            ?.takeUnless { it.endsWith(TMP_FILE_SUFFIX) }
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

    private suspend fun downloadImpl(): Boolean =
        // concurrent thumbnail downloads for the same manga share the same
        // "<mangaId>.tmp" file inside ImageResponse.saveImage; serialize them so
        // they can't interleave into a corrupt file
        downloadMutexes.getOrPut(mangaId) { Mutex() }.withLock {
            val isExistingFile = getFilePath() != null
            if (isExistingFile) {
                return@withLock true
            }

            val (inputStream, mime) =
                try {
                    Manga.fetchMangaThumbnail(mangaId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ThumbnailFetchFailedException) {
                    throw e // remembered failure replayed inside the fetch — keep the marker
                } catch (e: Exception) {
                    throw ThumbnailFetchFailedException(e)
                }
            inputStream.use { image ->
                makeSureDownloadDirExists()
                val filePath = getThumbnailDownloadPath(mangaId)
                ImageResponse.saveImage(filePath, image, mime)
            }

            return@withLock true
        }

    override fun download(): FileDownload0Args = FileDownload0Args(::downloadImpl)

    override suspend fun delete(): Boolean {
        val filePath = getFilePath()
        if (filePath.isNullOrEmpty()) {
            return true
        }

        return File(filePath).delete()
    }

    private fun makeSureDownloadDirExists() {
        val downloadDirPath = applicationDirs.thumbnailDownloadsRoot
        val downloadDir = File(downloadDirPath)

        if (!downloadDir.exists() && !downloadDir.mkdirs() && !downloadDir.exists()) {
            throw IOException("Could not create thumbnail download directory \"$downloadDirPath\"")
        }
    }

    companion object {
        // bounded by the number of manga with thumbnails; entries stay resident
        private val downloadMutexes = ConcurrentHashMap<Int, Mutex>()
    }
}
