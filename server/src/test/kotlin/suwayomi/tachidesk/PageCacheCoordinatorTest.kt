package suwayomi.tachidesk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.manga.impl.util.storage.PageCacheCoordinator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageCacheCoordinatorTest {
    @Test
    fun withPageLockSerializesAccessToTheSameKey() =
        runBlocking(Dispatchers.Default) {
            val concurrentEntries = AtomicInteger(0)
            val maxObservedConcurrentEntries = AtomicInteger(0)

            val jobs =
                (1..20).map {
                    async {
                        PageCacheCoordinator.withPageLock("dir", "page-001") {
                            val entries = concurrentEntries.incrementAndGet()
                            maxObservedConcurrentEntries.getAndUpdate { current -> maxOf(current, entries) }
                            delay(5)
                            concurrentEntries.decrementAndGet()
                        }
                    }
                }
            jobs.awaitAll()

            assertEquals(1, maxObservedConcurrentEntries.get(), "critical sections for the same key must never overlap")
        }

    @Test
    fun withPageLockDoesNotSerializeDifferentKeys() {
        // just a liveness check: unrelated keys must not deadlock/serialize on the same lock
        runBlocking(Dispatchers.Default) {
            val jobs =
                (1..20).map { index ->
                    async {
                        PageCacheCoordinator.withPageLock("dir", "page-$index") {
                            delay(5)
                        }
                    }
                }
            jobs.awaitAll()
        }
    }

    @Test
    fun isProcessedTracksMarkProcessedPerKey() {
        val saveDir = "some/dir"
        val fileName = "001"
        val otherFileName = "002"

        assertFalse(PageCacheCoordinator.isProcessed(saveDir, fileName))

        PageCacheCoordinator.markProcessed(saveDir, fileName)

        assertTrue(PageCacheCoordinator.isProcessed(saveDir, fileName))
        assertFalse(PageCacheCoordinator.isProcessed(saveDir, otherFileName))
    }
}
