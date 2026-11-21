package suwayomi.tachidesk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.PageCacheCoordinator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun locksAreDroppedOnceReleased() {
        runBlocking(Dispatchers.Default) {
            (1..20)
                .map { index ->
                    async {
                        PageCacheCoordinator.withPageLock("dir", "page-${index % 3}") {
                            delay(5)
                        }
                    }
                }.awaitAll()
        }

        assertEquals(0, PageCacheCoordinator.lockCount(), "no lock may outlive its last holder")
    }

    @Test
    fun isProcessedTracksMarkProcessedPerKeyOnDisk() {
        val saveDir = createTempDirectory("page-cache").toFile()
        try {
            val fileName = "001"
            val otherFileName = "002"

            assertFalse(PageCacheCoordinator.isProcessed(saveDir.path, fileName))

            PageCacheCoordinator.markProcessed(saveDir.path, fileName)

            assertTrue(PageCacheCoordinator.isProcessed(saveDir.path, fileName))
            assertFalse(PageCacheCoordinator.isProcessed(saveDir.path, otherFileName))
            // kept out of the page lookups, which only match "<page>.*"
            assertNull(ImageResponse.findFileNameStartingWith(saveDir.path, fileName))

            PageCacheCoordinator.clearProcessedMarkers(saveDir.path)

            assertFalse(PageCacheCoordinator.isProcessed(saveDir.path, fileName))
            assertEquals(emptyList(), saveDir.listFiles().orEmpty().toList())
        } finally {
            saveDir.deleteRecursively()
        }
    }
}
