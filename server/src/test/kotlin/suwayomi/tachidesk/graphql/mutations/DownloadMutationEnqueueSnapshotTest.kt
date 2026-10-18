package suwayomi.tachidesk.graphql.mutations

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.download.model.DownloadState
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.createLibraryManga
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import suwayomi.tachidesk.graphql.types.DownloadState as GraphQLDownloadState

class DownloadMutationEnqueueSnapshotTest : ApplicationTest() {
    @Test
    fun enqueueSingleAlreadyQueuedChapterReturnsTheInitialQueue() =
        withQueuedChapter { chapterId ->
            val initial = runBlocking { DownloadManager.updates.first() }
            assertTrue(initial.initial.orEmpty().any { it.chapterId == chapterId })
            assertFalse(initial.updates.any { it.downloadQueueItem.chapterId == chapterId })

            val future =
                DownloadMutation().enqueueChapterDownload(
                    1,
                    DownloadMutation.EnqueueChapterDownloadInput("single-existing", chapterId),
                )
            try {
                val payload = assertNotNull(future.get(35, TimeUnit.SECONDS))
                assertEquals("single-existing", payload.clientMutationId)
                val queued = payload.downloadStatus.queue.single { it.chapterId == chapterId }
                assertEquals(GraphQLDownloadState.QUEUED, queued.state)
            } finally {
                if (!future.isDone) {
                    future.cancel(true)
                }
                assertDownloadRequested(chapterId)
            }
        }

    @Test
    fun enqueueMultipleAlreadyQueuedChaptersReturnsTheInitialQueue() =
        withQueuedChapter { chapterId ->
            val future =
                DownloadMutation().enqueueChapterDownloads(
                    1,
                    DownloadMutation.EnqueueChapterDownloadsInput("multiple-existing", listOf(chapterId)),
                )
            try {
                val payload = assertNotNull(future.get(35, TimeUnit.SECONDS))
                assertEquals("multiple-existing", payload.clientMutationId)
                val queued = payload.downloadStatus.queue.single { it.chapterId == chapterId }
                assertEquals(GraphQLDownloadState.QUEUED, queued.state)
            } finally {
                if (!future.isDone) {
                    future.cancel(true)
                }
                assertDownloadRequested(chapterId)
            }
        }

    private fun assertDownloadRequested(chapterId: Int) {
        assertTrue(
            transaction {
                ChapterUserTable
                    .selectAll()
                    .where { ChapterUserTable.chapter eq chapterId }
                    .single()[ChapterUserTable.isDownloadRequested]
            },
        )
    }

    private fun withQueuedChapter(block: (Int) -> Unit) {
        val queueField = DownloadManager::class.java.getDeclaredField("downloadQueue")
        queueField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val queue = queueField.get(null) as MutableList<DownloadQueueItem>
        val originalQueue = queue.toList()
        val mangaId = createLibraryManga("Already queued mutation fixture")
        var chapterId: Int? = null
        var item: DownloadQueueItem? = null
        try {
            val id =
                transaction {
                    ChapterTable
                        .insertAndGetId {
                            it[url] = "already-queued-mutation-chapter"
                            it[name] = "Already queued chapter"
                            it[sourceOrder] = 1
                            it[manga] = mangaId
                            it[pageCount] = 4
                            it[memo] = JsonObject.EMPTY
                        }.value
                }
            chapterId = id
            item = DownloadQueueItem(id, 1, mangaId, 1L, 4, DownloadState.Queued)
            // Seed the existing queue entry directly so enqueue() never starts a downloader.
            queue.add(item)
            block(id)
        } finally {
            item?.let { queue.remove(it) }
            transaction {
                chapterId?.let { id ->
                    ChapterUserTable.deleteWhere { ChapterUserTable.chapter eq id }
                    ChapterTable.deleteWhere { ChapterTable.id eq id }
                }
                MangaUserTable.deleteWhere { MangaUserTable.manga eq mangaId }
                MangaTable.deleteWhere { MangaTable.id eq mangaId }
            }
            assertEquals(originalQueue, queue.toList())
        }
    }
}
