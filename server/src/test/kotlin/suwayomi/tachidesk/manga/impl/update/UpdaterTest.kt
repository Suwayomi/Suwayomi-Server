package suwayomi.tachidesk.manga.impl.update

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
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
    private var otherUserId: Int? = null
    private val testSourceId = 999_001L
    private val updater get() = Injekt.get<IUpdater>()

    @BeforeEach
    fun setUp() {
        ensureDefaultCategory(1)
        userSettings.set(1, userConfig.excludeUnreadChapters, false)
        userSettings.set(1, userConfig.excludeCompleted, false)
        userSettings.set(1, userConfig.syncYomiEnabled, false)
        userSettings.set(1, userConfig.autoDownloadNewChapters, false)
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
        otherUserId?.let { id -> transaction { UserAccountTable.deleteWhere { UserAccountTable.id eq id } } }
        GetSource.unregisterSource(testSourceId)
    }

    @Test
    fun `manual library update recognizes read flags and timestamps`() {
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
                    listOf(null, 0L, 1L).flatMap { timestamp ->
                        (if (timestamp == null) listOf(0) else listOf(0, 1, 2)).map { readChapters ->
                            val id = createLibraryManga("initialized=$initialized timestamp=$timestamp read=$readChapters")
                            transaction {
                                MangaTable.update({ MangaTable.id eq id }) {
                                    it[MangaTable.initialized] = initialized
                                }
                                if (timestamp == null && initialized) {
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
                                if (readChapters > 0) {
                                    transaction {
                                        val ids = ChapterTable.selectAll().where { ChapterTable.manga eq id }.take(readChapters)
                                        ids.forEach { chapter ->
                                            ChapterUserTable.update({ ChapterUserTable.chapter eq chapter[ChapterTable.id] }) {
                                                it[isRead] = true
                                            }
                                        }
                                    }
                                }
                                transaction {
                                    ChapterUserTable.update({ ChapterUserTable.chapter eq getChapterId(id) }) {
                                        it[isDownloaded] = true
                                    }
                                    if (timestamp > 0) {
                                        // Leave the other chapter at its default zero to exercise MAX.
                                        ChapterUserTable.update({ ChapterUserTable.chapter eq getChapterId(id) }) {
                                            it[lastReadAt] = timestamp
                                            it[lastPageRead] = 1
                                        }
                                    }
                                }
                            }
                            if (namedCategory) CategoryManga.addMangaToCategory(1, id, category.id)
                            Fixture(id, initialized, timestamp, readChapters)
                        }
                    }
                }
            val listed = CategoryManga.getCategoryMangaList(1, category.id).associateBy { it.id }
            fixtures.forEach { (id, initialized, timestamp, readChapters) ->
                assertEquals(timestamp, listed.getValue(id).lastReadAt)
                assertEquals(initialized, listed.getValue(id).initialized)
                assertEquals(listed.getValue(id).chapterCount!! - readChapters, listed.getValue(id).unreadCount)
            }

            for (skipNotStarted in listOf(true, false)) {
                userSettings.set(1, userConfig.excludeNotStarted, skipNotStarted)
                val expectedIds =
                    fixtures
                        .filter { (_, initialized, timestamp, readChapters) ->
                            !skipNotStarted || !initialized || timestamp == 1L || readChapters > 0
                        }.map { it.id }
                        .toSet()
                updater.reset()
                updater.addCategoriesToUpdateQueue(
                    1,
                    listOf(category),
                    clear = true,
                    forceAll = namedCategory,
                    automatedUpdate = automatedUpdate,
                )
                val status = awaitUpdate(fixtures.size)
                assertEquals(expectedIds, status.mangaUpdates.map { it.manga.id }.toSet())
                assertEquals(fixtures.size - expectedIds.size, status.skippedMangasCount)
                assertTrue(status.mangaUpdates.all { it.status == JobStatus.COMPLETE })
                assertTrue(status.mangaUpdates.all { it.userId == if (automatedUpdate) null else 1 })
            }
        } finally {
            if (namedCategory) Category.removeCategory(1, category.id)
        }
    }

    private data class Fixture(
        val id: Int,
        val initialized: Boolean,
        val timestamp: Long?,
        val readChapters: Int,
    )

    private fun awaitUpdate(mangaCount: Int): UpdateUpdates =
        runBlocking {
            withTimeout(10_000) {
                var current: UpdateUpdates
                do {
                    delay(10)
                    current = updater.getStatus()
                } while (current.totalJobs + current.skippedMangasCount != mangaCount || current.finishedJobs != current.totalJobs)
                current
            }
        }

    private fun queueAndWait(
        categories: List<CategoryDataClass>,
        count: Int,
    ): UpdateUpdates {
        updater.reset()
        updater.addCategoriesToUpdateQueue(1, categories, clear = true, forceAll = false, automatedUpdate = false)
        return awaitUpdate(count)
    }

    private fun createStartedManga(
        title: String,
        read: Boolean = true,
    ): Int {
        val id = createLibraryManga(title)
        createChapters(id, 1, read = read, start = 37)
        transaction {
            MangaTable.update({ MangaTable.id eq id }) { it[initialized] = true }
            ChapterTable.update({ ChapterTable.manga eq id }) { it[chapter_number] = 37f }
        }
        return id
    }

    @Test
    fun `another users read flags and timestamps do not start the current users manga`() {
        userSettings.set(1, userConfig.excludeNotStarted, true)
        val started = createStartedManga("Current reader")
        val unread = createStartedManga("Other reader", read = false)
        otherUserId =
            transaction {
                UserAccountTable
                    .insertAndGetId {
                        it[username] = "updater-other-reader"
                        it[password] = "password"
                    }.value
            }
        transaction {
            ChapterUserTable.deleteWhere { ChapterUserTable.chapter eq getChapterId(unread) }
            ChapterUserTable.insert {
                it[user] = otherUserId!!
                it[chapter] = getChapterId(unread)
                it[isRead] = true
                it[lastReadAt] = 1L
            }
        }
        val category = Category.getDefaultCategory(1)!!
        val listed = CategoryManga.getCategoryMangaList(1, category.id).first { it.id == unread }
        assertEquals(null, listed.lastReadAt)
        assertEquals(listed.chapterCount, listed.unreadCount)
        assertEquals(setOf(started), queueAndWait(listOf(category), 2).mangaUpdates.map { it.manga.id }.toSet())
    }

    @Test
    fun `read flags do not bypass category completed unread or source update filters`() {
        userSettings.set(1, userConfig.excludeNotStarted, true)
        val included = Category.createCategory(1, "Included updates")
        val excluded = Category.createCategory(1, "Excluded updates")
        try {
            Category.updateCategory(1, excluded, null, null, IncludeOrExclude.EXCLUDE.value, null)
            val started = createStartedManga("Eligible")
            val completed = createStartedManga("Completed")
            val sourceDisabled = createStartedManga("Source disabled")
            val categoryExcluded = createStartedManga("Category excluded")
            val hasUnread = createStartedManga("Has unread")
            createChapters(hasUnread, 1, read = false, start = 38)
            val ids = listOf(started, completed, sourceDisabled, categoryExcluded, hasUnread)
            CategoryManga.addMangasToCategories(1, ids, listOf(included))
            CategoryManga.addMangaToCategory(1, categoryExcluded, excluded)
            transaction {
                MangaTable.update({ MangaTable.id eq completed }) { it[status] = MangaStatus.COMPLETED.value }
                MangaTable.update({ MangaTable.id eq sourceDisabled }) {
                    it[updateStrategy] = UpdateStrategy.entries.first { strategy -> strategy != UpdateStrategy.ALWAYS_UPDATE }.name
                }
            }
            userSettings.set(1, userConfig.excludeCompleted, true)
            userSettings.set(1, userConfig.excludeUnreadChapters, true)
            for (skip in listOf(true, false)) {
                userSettings.set(1, userConfig.excludeNotStarted, skip)
                val status =
                    queueAndWait(listOf(Category.getCategoryById(1, included)!!, Category.getCategoryById(1, excluded)!!), ids.size)
                assertEquals(setOf(started), status.mangaUpdates.map { it.manga.id }.toSet())
                assertEquals(4, status.skippedMangasCount)
            }
        } finally {
            Category.removeCategory(1, included)
            Category.removeCategory(1, excluded)
        }
    }

    @Test
    fun `library update saves new source chapters for read flags without timestamps and reports source failures`() {
        unmockkObject(Manga)
        userSettings.set(1, userConfig.excludeNotStarted, true)
        userSettings.set(1, userConfig.updateMangas, false)
        val started = createStartedManga("Read flags")
        val startedChapter = getChapterId(started)
        val unread = createStartedManga("Never started", read = false)
        transaction {
            listOf(started, unread).forEach { id ->
                MangaTable.update({ MangaTable.id eq id }) { it[sourceReference] = testSourceId }
            }
        }
        val source = mockk<Source>()
        every { source.id } returns testSourceId
        val fetched =
            listOf(39, 38, 37).map { number ->
                SChapter.create().apply {
                    url = "$number"
                    name = "Chapter $number"
                    chapter_number = number.toFloat()
                }
            }
        val update = mockk<SMangaUpdate>()
        every { update.chapters } returns fetched
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } returns update
        GetSource.registerSource(testSourceId to source)
        val category = Category.getDefaultCategory(1)!!
        val status = queueAndWait(listOf(category), 2)
        assertEquals(listOf(started), status.mangaUpdates.map { it.manga.id })
        assertEquals(JobStatus.COMPLETE, status.mangaUpdates.single().status)
        coVerify(exactly = 1) { source.getMangaUpdate(match { it.url == "Read flags" }, any(), false, true) }
        coVerify(exactly = 0) { source.getMangaUpdate(match { it.url == "Never started" }, any(), any(), any()) }
        transaction {
            assertEquals(
                setOf(37f, 38f, 39f),
                ChapterTable
                    .selectAll()
                    .where {
                        ChapterTable.manga eq started
                    }.map { it[ChapterTable.chapter_number] }
                    .toSet(),
            )
            assertEquals(1L, ChapterTable.selectAll().where { ChapterTable.manga eq unread }.count())
            assertEquals(
                0L,
                ChapterUserTable
                    .selectAll()
                    .where {
                        ChapterUserTable.chapter eq startedChapter
                    }.first()[ChapterUserTable.lastReadAt],
            )
        }
        coEvery { source.getMangaUpdate(any(), any(), any(), any()) } throws IllegalStateException("Comix verification required")
        val failed = queueAndWait(listOf(category), 2)
        assertEquals(listOf(started), failed.mangaUpdates.map { it.manga.id })
        assertEquals(JobStatus.FAILED, failed.mangaUpdates.single().status)
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
