package suwayomi.tachidesk.opds

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.dto.PrimaryFilterType
import suwayomi.tachidesk.opds.repository.ChapterRepository
import suwayomi.tachidesk.opds.repository.MangaRepository
import suwayomi.tachidesk.opds.repository.NavigationRepository
import suwayomi.tachidesk.opds.util.resolveBasicUsernameToId
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository-level tests for the U6 OPDS fixes: LEFT JOIN nullability, pagination
 * offsets/page clamping, LIKE wildcard escaping, genre separator variants, per-user
 * category isolation and the Basic-auth username resolver.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpdsRepositoryChangesTest : ApplicationTest() {
    companion object {
        private const val TEST_SOURCE_ID = 424355L
        private const val TEST_EXT_NAME = "Opds U6 Test Extension"
    }

    /** Source stub that can serve a single manga on demand for `getMangaBySource`. */
    private class TestSource(
        sourceId: Long,
        private val manga: List<SManga> = emptyList(),
    ) : StubSource(sourceId) {
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(manga, false)

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
    }

    private val userId = 1
    private var otherUserId: Int = 0

    @BeforeEach
    fun setUp() {
        otherUserId =
            transaction {
                UserCodeService.createUser("opds_u6_other", "password", null)
            }

        // a real SOURCE row: MANGA.SOURCE is inner-joined by the library queries
        transaction {
            val extension =
                ExtensionTable.insertAndGetId {
                    it[ExtensionTable.apkName] = "$TEST_EXT_NAME.apk"
                    it[ExtensionTable.name] = TEST_EXT_NAME
                    it[ExtensionTable.pkgName] = TEST_EXT_NAME
                    it[ExtensionTable.versionName] = "1.0"
                    it[ExtensionTable.versionCode] = 1
                    it[ExtensionTable.lang] = "en"
                    it[ExtensionTable.extensionLib] = "1.0"
                    it[ExtensionTable.contentWarning] = ContentWarning.SAFE.ordinal
                    it[ExtensionTable.isInstalled] = true
                }
            SourceTable.insert {
                it[SourceTable.id] = EntityID(TEST_SOURCE_ID, SourceTable)
                it[SourceTable.name] = TEST_EXT_NAME
                it[SourceTable.lang] = "en"
                it[SourceTable.extension] = extension
                it[SourceTable.contentWarning] = ContentWarning.SAFE.ordinal
            }
        }
        GetSource.registerSource(TEST_SOURCE_ID to TestSource(TEST_SOURCE_ID))
    }

    @AfterEach
    fun tearDown() {
        GetSource.unregisterSource(TEST_SOURCE_ID)
        userSettings.resetAll(userId)
        transaction {
            CategoryMangaTable.deleteWhere { CategoryMangaTable.user eq otherUserId }
            CategoryTable.deleteWhere { (CategoryTable.user eq otherUserId) or (CategoryTable.user eq userId) }
            UserAccountTable.deleteWhere { UserAccountTable.id eq otherUserId }
            SourceTable.deleteWhere { SourceTable.id eq TEST_SOURCE_ID }
            ExtensionTable.deleteWhere { ExtensionTable.name eq TEST_EXT_NAME }
        }
        clearTables(ChapterTable, ChapterUserTable, CategoryMangaTable, MangaTable, MangaUserTable)
    }

    private fun createManga(
        title: String,
        genre: String? = null,
    ): Int =
        transaction {
            val mangaId =
                MangaTable
                    .insertAndGetId {
                        it[MangaTable.title] = title
                        it[MangaTable.url] = title
                        it[MangaTable.sourceReference] = TEST_SOURCE_ID
                        it[MangaTable.genre] = genre
                    }.value
            MangaUserTable.insert {
                it[MangaUserTable.manga] = mangaId
                it[MangaUserTable.user] = userId
                it[MangaUserTable.inLibrary] = true
            }
            mangaId
        }

    private fun createChapters(
        mangaId: Int,
        count: Int,
        withUserRow: Boolean,
        pageCount: Int = -1,
        startOrder: Int = 1,
    ): List<Int> =
        transaction {
            val ids =
                ChapterTable
                    .batchInsert(0 until count) {
                        this[ChapterTable.url] = "$mangaId-${it + startOrder}"
                        this[ChapterTable.name] = "Ch ${it + startOrder}"
                        this[ChapterTable.sourceOrder] = it + startOrder
                        this[ChapterTable.manga] = mangaId
                        this[ChapterTable.pageCount] = pageCount
                        this[ChapterTable.memo] = JsonObject.EMPTY
                    }.map { it[ChapterTable.id].value }
            if (withUserRow) {
                ChapterUserTable.batchInsert(ids) {
                    this[ChapterUserTable.chapter] = it
                    this[ChapterUserTable.user] = userId
                }
            }
            ids
        }

    // --- U6-B: LEFT JOIN user columns must not NPE ---

    @Test
    fun chaptersWithoutUserRowDefaultToUnread() {
        val mangaId = createManga("No User Rows")
        createChapters(mangaId, 3, withUserRow = false, pageCount = 5)

        val (chapters, total) =
            runBlocking {
                ChapterRepository.getChaptersForManga(
                    userId,
                    mangaId,
                    ChapterTable.sourceOrder,
                    SortOrder.ASC,
                    "all",
                    pageNum = 1,
                    skipMetadata = false,
                )
            }

        assertEquals(3, chapters.size)
        assertEquals(3L, total)
        assertTrue(chapters.all { !it.read && it.lastPageRead == 0 && it.lastReadAt == 0L && !it.downloaded })
    }

    @Test
    fun exploreMangaWithoutUserRowReadsAsNotInLibrary() {
        val entryTitle = "Never Interacted With"
        GetSource.registerSource(
            TEST_SOURCE_ID to
                TestSource(
                    TEST_SOURCE_ID,
                    listOf(
                        SManga.create().apply {
                            title = entryTitle
                            url = "/never-interacted"
                        },
                    ),
                ),
        )

        val (entries, _) =
            runBlocking { MangaRepository.getMangaBySource(userId, TEST_SOURCE_ID, 1, "popular", includeNsfw = true) }

        // used to NPE on the absent MANGAUSER row; now defaults to not-in-library
        assertEquals(listOf(entryTitle), entries.map { it.title })
        assertFalse(entries.single().inLibrary)
    }

    // --- U6-D/E: findMangaByCriteria pagination & clamping ---

    @Test
    fun searchResultsArePaginatedWithOffset() {
        userSettings.set(userId, userConfig.opdsItemsPerPage, 10)
        repeat(15) { createManga("Paged Manga $it") }

        val (page1, total) = MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "Paged Manga"), 1)
        val (page2, _) = MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "Paged Manga"), 2)

        assertEquals(10, page1.size)
        assertEquals(5, page2.size)
        assertEquals(15L, total)
        assertTrue(page1.map { it.id }.intersect(page2.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun invalidPageNumbersBehaveLikePageOneAndHugePagesReturnEmpty() {
        userSettings.set(userId, userConfig.opdsItemsPerPage, 10)
        repeat(12) { createManga("Clamp Manga $it") }

        val (page1, _) = MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "Clamp Manga"), 1)
        val (page0, _) = MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "Clamp Manga"), 0)
        val (pageNeg, _) = MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "Clamp Manga"), -3)
        val (pageHuge, _) = MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "Clamp Manga"), Int.MAX_VALUE)

        assertEquals(page1.map { it.id }, page0.map { it.id })
        assertEquals(page1.map { it.id }, pageNeg.map { it.id })
        assertTrue(pageHuge.isEmpty())
    }

    // --- U6-L: LIKE wildcard escaping + genre separator variants ---

    @Test
    fun searchDoesNotTreatPercentOrUnderscoreAsWildcards() {
        createManga("100% Match")
        createManga("Regular Manga")

        val (percentResults, percentTotal) =
            MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "100%"), 1)
        assertEquals(1L, percentTotal)
        assertEquals("100% Match", percentResults.single().title)

        // `_` must be a literal, not "any single character"
        val (underscoreResults, underscoreTotal) =
            MangaRepository.findMangaByCriteria(userId, OpdsSearchCriteria(query = "_"), 1)
        assertTrue(underscoreResults.isEmpty())
        assertEquals(0L, underscoreTotal)
    }

    @Test
    fun genreFilterMatchesElementsUnderBothSeparatorFormats() {
        createManga("Comma Space", genre = "Action, Adventure")
        createManga("Comma Only", genre = "Action,Comedy")
        createManga("Wildcardish", genre = "100% Legit")

        fun countGenre(genre: String): Long =
            MangaRepository
                .getLibraryManga(
                    userId,
                    OpdsMangaFilter(genre = genre, primaryFilter = PrimaryFilterType.GENRE),
                    pageNum = 1,
                    sort = null,
                    filter = null,
                ).totalCount

        assertEquals(2L, countGenre("Action"))
        assertEquals(1L, countGenre("Adventure"))
        assertEquals(1L, countGenre("Comedy")) // matches the "Action,Comedy" (no space) row
        assertEquals(1L, countGenre("100% Legit")) // `%` treated literally
        assertEquals(0L, countGenre("Adv")) // list-element match, not substring
        assertEquals(0L, countGenre("%")) // wildcard input matches nothing
    }

    // --- U6-I: category name must not leak across users ---

    @Test
    fun categoryFeedTitleDoesNotResolveOtherUsersCategory() {
        val foreignCategoryId =
            transaction {
                CategoryTable
                    .insertAndGetId {
                        it[CategoryTable.name] = "Foreign Secret Category"
                        it[CategoryTable.user] = otherUserId
                    }.value
            }

        val result =
            MangaRepository.getLibraryManga(
                userId,
                OpdsMangaFilter(categoryId = foreignCategoryId, primaryFilter = PrimaryFilterType.CATEGORY),
                pageNum = 1,
                sort = null,
                filter = null,
            )

        assertNull(result.feedTitleComponent, "foreign category names must not be exposed")
        assertEquals(0L, result.totalCount)
    }

    @Test
    fun categoriesNavFeedOnlyListsOwnCategories() {
        transaction {
            CategoryTable.insertAndGetId {
                it[CategoryTable.name] = "Foreign Secret Category"
                it[CategoryTable.user] = otherUserId
            }
        }
        val mangaId = createManga("In My Category")
        val myCategoryId =
            transaction {
                CategoryTable
                    .insertAndGetId {
                        it[CategoryTable.name] = "Mine"
                        it[CategoryTable.user] = userId
                    }.value
            }
        transaction {
            CategoryMangaTable.insert {
                it[CategoryMangaTable.category] = myCategoryId
                it[CategoryMangaTable.manga] = mangaId
                it[CategoryMangaTable.user] = userId
            }
        }

        val (categories, _) = NavigationRepository.getCategories(userId, pageNum = null)

        assertTrue(categories.any { it.name == "Mine" })
        assertFalse(categories.any { it.name == "Foreign Secret Category" })
    }

    // --- U6-J: skipMetadata filtering happens before pagination ---

    @Test
    fun skipMetadataFillsRequestedPagePastUnreachableChapters() {
        userSettings.set(userId, userConfig.opdsItemsPerPage, 10)
        val mangaId = createManga("Unreachable First")

        // 15 chapters with unknown page count sort first and get filtered out by the
        // skipMetadata enrichment; the 10 reachable ones must still fill page 1.
        createChapters(mangaId, 15, withUserRow = true, pageCount = 0, startOrder = 1)
        val reachableIds = createChapters(mangaId, 10, withUserRow = true, pageCount = 5, startOrder = 16)

        val (chapters, total) =
            runBlocking {
                ChapterRepository.getChaptersForManga(
                    userId,
                    mangaId,
                    ChapterTable.sourceOrder,
                    SortOrder.ASC,
                    "all",
                    pageNum = 1,
                    skipMetadata = true,
                )
            }

        assertEquals(reachableIds.toSet(), chapters.map { it.id }.toSet())
        assertEquals(10, chapters.size)
        // the table scan reached the end, so the reported total is the exact reachable count
        assertEquals(10L, total)
    }

    // --- U6-C: Basic-auth username resolution ---

    @Test
    fun basicUsernameResolvesToRealAccountId() {
        assertEquals(otherUserId, resolveBasicUsernameToId("opds_u6_other"))
        assertNotEquals(1, otherUserId)
    }

    @Test
    fun unknownBasicUsernameFallsBackToBuiltInAdmin() {
        assertEquals(1, resolveBasicUsernameToId("no_such_opds_user"))
    }
}
