package suwayomi.tachidesk.graphql

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.graphql.subscriptions.DownloadSubscription
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdate
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.test.Test
import kotlin.test.assertEquals
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus as DownloadStatusModel
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdates as DownloadUpdatesModel
import suwayomi.tachidesk.manga.impl.download.model.Status as DownloadManagerStatus

/**
 * The download queue is process-global: every subscription event must be filtered down to
 * the subscriber's own chapters (a chapter is visible when the caller has a
 * `ChapterUserTable` row for it), matching `DownloadQuery.downloadStatus`.
 *
 * The queue/updates stores inside [DownloadManager] are private and only writable through
 * download paths with side effects, so tests inject items via reflection — kept to field
 * reads on the singleton, and cleared again in [tearDown].
 */
class DownloadSubscriptionTest : GraphQLTest() {
    private val user2Id: Int by lazy { createTestUser("downloadsub-user2") }

    private fun createChapter(
        mangaId: Int,
        name: String,
        userId: Int?,
    ): Int =
        transaction {
            val chapterId =
                ChapterTable
                    .insertAndGetId {
                        it[ChapterTable.url] = name
                        it[ChapterTable.name] = name
                        it[ChapterTable.sourceOrder] = 1
                        it[ChapterTable.manga] = mangaId
                        it[ChapterTable.memo] = JsonObject.EMPTY
                    }.value
            if (userId != null) {
                ChapterUserTable.insert {
                    it[ChapterUserTable.chapter] = chapterId
                    it[ChapterUserTable.user] = userId
                }
            }
            chapterId
        }

    private fun queueItem(
        chapterId: Int,
        mangaId: Int,
    ) = DownloadQueueItem(
        chapterId = chapterId,
        chapterIndex = 0,
        mangaId = mangaId,
        sourceId = 1L,
        pageCount = 0,
    )

    @Suppress("UNCHECKED_CAST")
    private fun downloadQueue(): MutableList<DownloadQueueItem> =
        DownloadManager::class.java
            .getDeclaredField("downloadQueue")
            .apply { isAccessible = true }
            .get(DownloadManager) as CopyOnWriteArrayList<DownloadQueueItem>

    @Suppress("UNCHECKED_CAST")
    private fun pendingUpdates(): MutableSet<DownloadUpdate> =
        DownloadManager::class.java
            .getDeclaredField("downloadUpdates")
            .apply { isAccessible = true }
            .get(DownloadManager) as CopyOnWriteArraySet<DownloadUpdate>

    @Test
    fun `filterStatusForUser drops queue entries of other users`() {
        val manga1 = createLibraryManga("Own Manga")
        val ownChapter = createChapter(manga1, "own", userId = 1)
        val manga2 = createLibraryManga("Other Manga", userId = user2Id)
        val foreignChapter = createChapter(manga2, "foreign", userId = user2Id)
        val orphanChapter = createChapter(manga1, "orphan", userId = null)

        val status =
            DownloadStatusModel(
                DownloadManagerStatus.Started,
                listOf(
                    queueItem(ownChapter, manga1),
                    queueItem(foreignChapter, manga2),
                    queueItem(orphanChapter, manga1),
                ),
            )

        val forUser1 = DownloadSubscription.filterStatusForUser(1, status)
        assertEquals(listOf(ownChapter), forUser1.queue.map { it.chapterId })
        assertEquals(DownloadManagerStatus.Started, forUser1.status)

        val forUser2 = DownloadSubscription.filterStatusForUser(user2Id, status)
        assertEquals(listOf(foreignChapter), forUser2.queue.map { it.chapterId })
    }

    @Test
    fun `filterUpdatesForUser drops updates and initial entries of other users`() {
        val manga1 = createLibraryManga("Own Manga")
        val ownChapter = createChapter(manga1, "own", userId = 1)
        val manga2 = createLibraryManga("Other Manga", userId = user2Id)
        val foreignChapter = createChapter(manga2, "foreign", userId = user2Id)

        val ownItem = queueItem(ownChapter, manga1)
        val foreignItem = queueItem(foreignChapter, manga2)
        val updates =
            DownloadUpdatesModel(
                DownloadManagerStatus.Started,
                updates =
                    listOf(
                        // global position 1 must be re-based onto the user's filtered queue
                        DownloadUpdate(DownloadUpdateType.POSITION, ownItem, 1),
                        DownloadUpdate(DownloadUpdateType.QUEUED, foreignItem, 0),
                    ),
                initial = listOf(foreignItem, ownItem),
            )

        val filtered = DownloadSubscription.filterUpdatesForUser(1, updates)
        assertEquals(listOf(ownChapter), filtered.updates.map { it.downloadQueueItem.chapterId })
        assertEquals(listOf(0), filtered.updates.map { it.position }, "positions must index into the filtered queue")
        assertEquals(listOf(ownChapter), filtered.initial?.map { it.chapterId })

        val withNullInitial =
            DownloadUpdatesModel(DownloadManagerStatus.Stopped, updates = emptyList(), initial = null)
        val filteredNull = DownloadSubscription.filterUpdatesForUser(1, withNullInitial)
        assertEquals(emptyList(), filteredNull.updates)
        assertEquals(null, filteredNull.initial)
    }

    @Test
    fun `downloadChanged emits a queue filtered to the subscriber's chapters`() =
        runBlocking {
            val manga1 = createLibraryManga("Own Manga")
            val ownChapter = createChapter(manga1, "own", userId = 1)
            val manga2 = createLibraryManga("Other Manga", userId = user2Id)
            val foreignChapter = createChapter(manga2, "foreign", userId = user2Id)

            downloadQueue().add(queueItem(ownChapter, manga1))
            downloadQueue().add(queueItem(foreignChapter, manga2))

            val status = DownloadSubscription().downloadChanged(userId = 1).first()
            assertEquals(listOf(ownChapter), status.queue.map { it.chapterId })
        }

    @Test
    fun `downloadStatusChanged emits filtered updates and initial queue`() =
        runBlocking {
            val manga1 = createLibraryManga("Own Manga")
            val ownChapter = createChapter(manga1, "own", userId = 1)
            val manga2 = createLibraryManga("Other Manga", userId = user2Id)
            val foreignChapter = createChapter(manga2, "foreign", userId = user2Id)

            val ownItem = queueItem(ownChapter, manga1)
            val foreignItem = queueItem(foreignChapter, manga2)
            downloadQueue().add(ownItem)
            downloadQueue().add(foreignItem)
            pendingUpdates().add(DownloadUpdate(DownloadUpdateType.PROGRESS, ownItem, 0))
            pendingUpdates().add(DownloadUpdate(DownloadUpdateType.PROGRESS, foreignItem, 1))

            val updates =
                DownloadSubscription()
                    .downloadStatusChanged(1, DownloadSubscription.DownloadChangedInput(maxUpdates = null))
                    .first()

            assertEquals(listOf(ownChapter), updates.initial?.map { it.chapterId })
            assertEquals(listOf(ownChapter), updates.updates.map { it.download.chapterId })
        }

    @AfterEach
    fun tearDown() {
        downloadQueue().clear()
        pendingUpdates().clear()
        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }
}
