package suwayomi.tachidesk.manga.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ThumbnailFileProvider
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

object ThumbnailDownloadHelper {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // a library update refreshes many manga in a row: only a few covers are checked against their source at once
    private val backgroundChecks = Semaphore(2)

    // one check per manga at a time. A check that finds a dead url refreshes the manga details, which must not start another
    private val checking: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    suspend fun getImage(mangaId: Int): Pair<InputStream, String> = provider(mangaId).getImage().execute()

    fun hasImage(mangaId: Int): Boolean = provider(mangaId).hasImage()

    suspend fun refreshIfStale(
        mangaId: Int,
        staleBefore: Long,
    ) {
        if (!checking.add(mangaId)) {
            return
        }
        try {
            provider(mangaId).refreshIfStale(staleBefore)
        } finally {
            checking.remove(mangaId)
        }
    }

    /**
     * Compare the downloaded cover with the source's right after a details refresh, instead of on the next request. The
     * version clients use to cache covers then changes as soon as the image does, even if no client asks for it.
     */
    fun refreshInBackground(
        mangaId: Int,
        staleBefore: Long,
    ) {
        if (!checking.add(mangaId)) {
            return
        }
        scope.launch {
            try {
                backgroundChecks.withPermit { provider(mangaId).refreshIfStale(staleBefore) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Could not check the cover of manga $mangaId against its source" }
            } finally {
                checking.remove(mangaId)
            }
        }
    }

    suspend fun delete(mangaId: Int): Boolean = provider(mangaId).delete()

    suspend fun download(mangaId: Int): Boolean = provider(mangaId).download().execute()

    // return the appropriate provider based on how the download was saved. For the logic is simple but will evolve when new types of downloads are available
    private fun provider(mangaId: Int): ThumbnailFileProvider = ThumbnailFileProvider(mangaId)
}
