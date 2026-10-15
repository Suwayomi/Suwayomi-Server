package suwayomi.tachidesk.manga.impl.update

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Regression tests for the Updater state machine (U5):
 *  - a dying source channel must not fail other sources' jobs (inverted predicate)
 *  - enqueues onto cancelled channels must recreate the channel, not leave PENDING zombies
 *  - a stalled flow subscriber must not freeze _status / the queue
 *  - UPDATING categories must be cleared once the batch finished
 *  - the same manga queued twice (e.g. per-user automated updates) is only fetched once
 */
internal class UpdaterTest : ApplicationTest() {
    private val updater: IUpdater get() = Injekt.get()

    private fun manga(
        id: Int,
        sourceId: String,
    ) = MangaDataClass(
        id = id,
        sourceId = sourceId,
        url = "url-$id",
        title = "manga-$id",
        initialized = true,
    )

    private suspend fun awaitIdle() {
        withTimeout(30.seconds) {
            while (updater.getStatus().isRunning) {
                delay(50)
            }
        }
    }

    private suspend fun awaitJob(mangaId: Int) {
        withTimeout(30.seconds) {
            while (updater.getStatus().mangaUpdates.none { it.manga.id == mangaId }) {
                delay(50)
            }
        }
    }

    private suspend fun awaitStatus(
        mangaId: Int,
        predicate: (JobStatus?) -> Boolean,
    ) {
        withTimeout(30.seconds) {
            while (true) {
                val status =
                    updater
                        .getStatus()
                        .mangaUpdates
                        .firstOrNull { it.manga.id == mangaId }
                        ?.status
                if (predicate(status)) return@withTimeout
                delay(50)
            }
        }
    }

    @AfterEach
    internal fun tearDown() {
        unmockkObject(Manga)
        updater.reset()
        clearTables(
            CategoryMangaTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
        }
    }

    @Test
    fun `a dying source channel only fails its own jobs`() =
        runBlocking {
            mockkObject(Manga)
            // an Error (not Exception) escapes process() and kills the source's channel
            coEvery { Manga.updateMangaAndChapters(1001, any(), any()) } throws NotImplementedError("source blew up")
            coEvery { Manga.updateMangaAndChapters(1002, any(), any()) } returns Unit

            updater.addMangasToQueue(
                listOf(
                    manga(1001, "u5-source-a"),
                    manga(1002, "u5-source-b"),
                ),
                userId = 1,
            )

            awaitIdle()

            val jobs = updater.getStatus().mangaUpdates.associateBy { it.manga.id }
            assertEquals(JobStatus.FAILED, jobs[1001]?.status)
            assertEquals(JobStatus.COMPLETE, jobs[1002]?.status)
        }

    @Test
    fun `enqueue onto a cancelled channel is re-created instead of leaving a PENDING zombie`() =
        runBlocking {
            mockkObject(Manga)
            coEvery { Manga.updateMangaAndChapters(any(), any(), any()) } returns Unit

            // pre-seed the channel map with a cancelled channel — the send must not die silently
            val channelField = Updater::class.java.getDeclaredField("updateChannels")
            channelField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val channels = channelField.get(updater) as ConcurrentHashMap<String, Channel<UpdateJob>>
            val deadChannel = Channel<UpdateJob>(Channel.UNLIMITED)
            deadChannel.cancel()
            channels["u5-source-dead"] = deadChannel

            updater.addMangasToQueue(listOf(manga(2001, "u5-source-dead")), userId = 1)

            awaitJob(2001)
            awaitIdle()

            val status = updater.getStatus()
            assertFalse(status.isRunning)
            // the job must have been processed on the recreated channel, never left as a
            // PENDING zombie pinning isRunning=true
            assertEquals(JobStatus.COMPLETE, status.mangaUpdates.firstOrNull { it.manga.id == 2001 }?.status)
        }

    @Test
    fun `a stalled updates subscriber does not freeze the status state`() =
        runBlocking {
            mockkObject(Manga)
            coEvery { Manga.updateMangaAndChapters(any(), any(), any()) } returns Unit

            // collects the onStart emission and then suspends forever — with a 0-buffer flow
            // the emit inside updateStatus() would suspend and _status would never be updated
            val stuckCollector =
                launch(Dispatchers.Default) {
                    updater.updates.collect { awaitCancellation() }
                }
            try {
                delay(200) // let the collector subscribe and consume the initial emission

                updater.addMangasToQueue(listOf(manga(3001, "u5-source-c")), userId = 1)

                // bound to a val so the runBlocking expression stays Unit — JUnit5 silently
                // skips non-void @Test methods
                val status =
                    withTimeout(30.seconds) {
                        updater.statusDeprecated.first { s ->
                            s.mangaStatusMap[JobStatus.COMPLETE].orEmpty().any { it.id == 3001 }
                        }
                    }
                assertTrue(status.mangaStatusMap[JobStatus.COMPLETE].orEmpty().any { it.id == 3001 })
            } finally {
                stuckCollector.cancel()
            }
        }

    @Test
    fun `UPDATING categories are cleared once the batch finished`() =
        runBlocking {
            mockkObject(Manga)
            coEvery { Manga.updateMangaAndChapters(any(), any(), any()) } returns Unit

            val categoryId = Category.createCategory(1, "u5-updating-cat")
            val mangaId = createLibraryManga("u5-updating-manga", 1)
            CategoryManga.addMangaToCategory(1, mangaId, categoryId)
            val category = Category.getCategoryList(1).first { it.id == categoryId }

            updater.addCategoriesToUpdateQueue(
                1,
                listOf(category),
                clear = true,
                forceAll = true,
            )

            awaitJob(mangaId)
            awaitIdle()
            assertFalse(updater.getStatus().isRunning)

            // the UPDATING clear happens on the debounced (non-immediate) emission after the
            // last job finished — poll until the sampled notification has been collected
            withTimeout(30.seconds) {
                while (updater.getStatus().categoryUpdates.any { it.status == CategoryUpdateStatus.UPDATING }) {
                    delay(100)
                }
            }
            assertTrue(updater.getStatus().categoryUpdates.none { it.status == CategoryUpdateStatus.UPDATING })
        }

    @Test
    fun `the same manga queued by two submissions is only fetched once`() =
        runBlocking {
            mockkObject(Manga)
            val gate = CompletableDeferred<Unit>()
            coEvery { Manga.updateMangaAndChapters(any(), any(), any()) } coAnswers {
                gate.await()
            }

            // second submission lands while the job is still RUNNING: no duplicate fetch
            val shared = manga(4001, "u5-source-e")
            updater.addMangasToQueue(listOf(shared), userId = 1)
            awaitStatus(4001) { it == JobStatus.RUNNING }
            updater.addMangasToQueue(listOf(shared), userId = 2)

            gate.complete(Unit)
            awaitIdle()

            coVerify(exactly = 1) { Manga.updateMangaAndChapters(4001, any(), any()) }
        }
}
