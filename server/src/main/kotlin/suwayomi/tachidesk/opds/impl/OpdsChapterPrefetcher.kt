package suwayomi.tachidesk.opds.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import suwayomi.tachidesk.manga.impl.chapter.refreshChapterPageList
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import kotlin.time.Duration.Companion.minutes

/**
 * Fetches the page lists of listed chapters in the background, so building a
 * feed never waits on the source. The fetched page counts are persisted, so
 * the streaming links appear in the next requests of the feed.
 */
object OpdsChapterPrefetcher {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Few at a time to avoid being rate limited by the sources
    private val fetchSemaphore = Semaphore(3)

    // Keeps feed reloads from requesting the same chapters again
    private val scheduledChapters =
        Cache
            .Builder<Int, Unit>()
            .expireAfterWrite(10.minutes)
            .build()

    /**
     * Schedules the page list fetch of the chapters that are not downloaded
     * and have no known page count.
     */
    fun prefetchPageCounts(chapters: List<OpdsChapterListAcqEntry>) {
        for (chapter in chapters) {
            if (chapter.downloaded || chapter.pageCount > 0 || scheduledChapters.get(chapter.id) != null) continue
            scheduledChapters.put(chapter.id, Unit)

            scope.launch {
                fetchSemaphore.withPermit {
                    try {
                        refreshChapterPageList(chapter.mangaId, chapter.id)
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to fetch page count for chapter ${chapter.id}" }
                    }
                }
            }
        }
    }
}
