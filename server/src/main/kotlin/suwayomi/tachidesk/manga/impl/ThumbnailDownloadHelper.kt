package suwayomi.tachidesk.manga.impl

import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ThumbnailFileProvider
import java.io.InputStream

object ThumbnailDownloadHelper {
    fun getImage(mangaId: Int): Pair<InputStream, String> {
        return provider(mangaId).getImage().execute()
    }

    fun delete(mangaId: Int): Boolean {
        return provider(mangaId).delete()
    }

    suspend fun download(mangaId: Int): Boolean {
        return provider(mangaId).download().execute()
    }

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private fun provider(mangaId: Int): ThumbnailFileProvider {
        return ThumbnailFileProvider(mangaId)
    }
}
