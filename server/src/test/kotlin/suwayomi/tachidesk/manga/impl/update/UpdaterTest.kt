package suwayomi.tachidesk.manga.impl.update

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import suwayomi.tachidesk.test.ensureDefaultCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdaterTest : ApplicationTest() {
    private val updater get() = Injekt.get<IUpdater>()

    @BeforeEach
    fun setUp() {
        ensureDefaultCategory(1)
        userSettings.set(1, userConfig.excludeUnreadChapters, false)
        userSettings.set(1, userConfig.excludeCompleted, false)
        userSettings.set(1, userConfig.syncYomiEnabled, false)
        // Exercise the real category query and queue without fetching from a source.
        mockkObject(Manga)
        coEvery { Manga.updateMangaAndChapters(any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        updater.reset()
        unmockkObject(Manga)
        userSettings.resetAll(1)
        clearTables(ChapterTable, MangaTable)
    }

    @Test
    fun `manual library update skips initialized manga with null or zero timestamps`() {
        checkNotStartedFilter(automatedUpdate = false, namedCategory = false)
    }

    @Test
    fun `explicit category update still applies the not started filter`() {
        checkNotStartedFilter(automatedUpdate = false, namedCategory = true)
    }

    @Test
    fun `automated library update applies the same not started filter`() {
        checkNotStartedFilter(automatedUpdate = true, namedCategory = false)
    }

    private fun checkNotStartedFilter(
        automatedUpdate: Boolean,
        namedCategory: Boolean,
    ) {
        val category =
            if (namedCategory) {
                Category.getCategoryById(1, Category.createCategory(1, "Not started filter"))!!
            } else {
                Category.getDefaultCategory(1)!!
            }
        try {
            val fixtures =
                listOf(false, true).flatMap { initialized ->
                    listOf(null, 0L, 1L).map { timestamp ->
                        val id = createLibraryManga("initialized=$initialized timestamp=$timestamp")
                        transaction {
                            MangaTable.update({ MangaTable.id eq id }) {
                                it[MangaTable.initialized] = initialized
                            }
                            if (timestamp == null) {
                                // A chapter without user data produces a NULL aggregate.
                                ChapterTable.insertAndGetId {
                                    it[manga] = id
                                    it[url] = "unread"
                                    it[name] = "Unread"
                                    it[sourceOrder] = 1
                                }
                            }
                        }
                        if (timestamp != null) {
                            createChapters(id, 2, read = false)
                            transaction {
                                ChapterUserTable.update({ ChapterUserTable.chapter eq getChapterId(id) }) {
                                    it[isDownloaded] = true
                                }
                                if (timestamp > 0) {
                                    // Leave the other chapter at its default zero to exercise MAX.
                                    ChapterUserTable.update({ ChapterUserTable.chapter eq getChapterId(id) }) {
                                        it[lastReadAt] = timestamp
                                    }
                                }
                            }
                        }
                        if (namedCategory) CategoryManga.addMangaToCategory(1, id, category.id)
                        Triple(id, initialized, timestamp)
                    }
                }
            val listed = CategoryManga.getCategoryMangaList(1, category.id).associateBy { it.id }
            fixtures.forEach { (id, initialized, timestamp) ->
                assertEquals(timestamp, listed.getValue(id).lastReadAt)
                assertEquals(initialized, listed.getValue(id).initialized)
                assertEquals(listed.getValue(id).chapterCount, listed.getValue(id).unreadCount)
            }

            for (skipNotStarted in listOf(true, false)) {
                userSettings.set(1, userConfig.excludeNotStarted, skipNotStarted)
                val expectedIds =
                    fixtures
                        .filter { (_, initialized, timestamp) ->
                            !skipNotStarted || !initialized || timestamp == 1L
                        }.map { it.first }
                        .toSet()
                updater.reset()
                updater.addCategoriesToUpdateQueue(
                    1,
                    listOf(category),
                    clear = true,
                    forceAll = namedCategory,
                    automatedUpdate = automatedUpdate,
                )
                val status =
                    runBlocking {
                        withTimeout(10_000) {
                            var current: UpdateUpdates
                            do {
                                delay(10)
                                current = updater.getStatus()
                            } while (current.totalJobs + current.skippedMangasCount != fixtures.size ||
                                current.finishedJobs != current.totalJobs
                            )
                            current
                        }
                    }
                assertEquals(expectedIds, status.mangaUpdates.map { it.manga.id }.toSet())
                assertEquals(fixtures.size - expectedIds.size, status.skippedMangasCount)
                assertTrue(status.mangaUpdates.all { it.status == JobStatus.COMPLETE })
                assertTrue(status.mangaUpdates.all { it.userId == if (automatedUpdate) null else 1 })
            }
        } finally {
            if (namedCategory) Category.removeCategory(1, category.id)
        }
    }

    private fun getChapterId(mangaId: Int): Int =
        transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .first()[ChapterTable.id]
                .value
        }
}
