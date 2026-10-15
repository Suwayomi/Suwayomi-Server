package suwayomi.tachidesk.opds

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.PrimaryFilterType
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.opds.util.resolveBasicUsernameToId
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.hasPermission
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Feed-level and auth-level regression tests for the P1 review fixes:
 * - genre names are path-segment encoded in nav entries and feed self-links
 * - library facet hrefs query-encode user-controlled sort/filter params
 * - the Basic-auth username resolution matches case-insensitively
 * - the DB `hasPermission(userId, …)` used for NSFW gating reflects the resolved user
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpdsReviewFixTest : ApplicationTest() {
    companion object {
        private const val TEST_SOURCE_ID = 424366L
        private const val TEST_EXT_NAME = "Opds P1 Fix Test Extension"
        private const val BASE_URL = "/api/opds/v1.2"
    }

    private val userId = 1
    private var caseUserId: Int = 0
    private var permissionUserId: Int = 0

    @BeforeEach
    fun setUp() {
        caseUserId = transaction { UserCodeService.createUser("OpdsCaseUser", "password", null) }
        permissionUserId =
            transaction {
                // an empty permission set: grants nothing, not even the defaults
                UserCodeService.createUser("opds_permission_check", "password", emptyList())
            }

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
    }

    @AfterEach
    fun tearDown() {
        transaction {
            CategoryTable.deleteWhere {
                CategoryTable.user inList listOf(caseUserId, permissionUserId)
            }
            UserAccountTable.deleteWhere {
                UserAccountTable.id inList listOf(caseUserId, permissionUserId)
            }
            SourceTable.deleteWhere { SourceTable.id eq TEST_SOURCE_ID }
            ExtensionTable.deleteWhere { ExtensionTable.name eq TEST_EXT_NAME }
        }
        clearTables(MangaTable, MangaUserTable)
    }

    private fun createLibraryManga(
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

    // --- genre path-segment encoding ---

    @Test
    fun genreNavEntriesEncodeGenreNamesInPathSegments() {
        createLibraryManga("Multi Word Genre", genre = "Slice of Life, Action")

        val feed = OpdsFeedBuilder.getGenresFeed(userId, BASE_URL, Locale.ENGLISH, pageNum = 1)

        assertTrue(
            feed.contains("genre/Slice%20of%20Life"),
            "multi-word genre must be %20-encoded in the nav href, got:\n$feed",
        )
        assertFalse(feed.contains("genre/Slice of Life"), "raw spaces must not reach the href")
    }

    @Test
    fun genreFeedSelfLinkEncodesTheGenrePathSegment() {
        createLibraryManga("Multi Word Genre", genre = "Slice of Life")

        val feed =
            OpdsFeedBuilder.getLibraryFeed(
                userId,
                BASE_URL,
                Locale.ENGLISH,
                OpdsMangaFilter(genre = "Slice of Life", primaryFilter = PrimaryFilterType.GENRE),
                isSearch = false,
                pageNum = 1,
                sort = null,
                filter = null,
            )

        assertTrue(
            feed.contains("genre/Slice%20of%20Life"),
            "the feed self/pagination links must keep the genre path segment encoded, got:\n$feed",
        )
        assertFalse(feed.contains("genre/Slice of Life"), "raw spaces must not reach the path")
    }

    @Test
    fun genreNameWithReservedCharactersIsFullyEncoded() {
        createLibraryManga("Reserved Genre", genre = "Sci-Fi & Fantasy")

        val feed = OpdsFeedBuilder.getGenresFeed(userId, BASE_URL, Locale.ENGLISH, pageNum = 1)

        assertTrue(
            feed.contains("genre/Sci-Fi%20%26%20Fantasy"),
            "'&' inside a genre must be encoded, got:\n$feed",
        )
    }

    // --- facet parameter encoding ---

    @Test
    fun libraryFacetLinksQueryEncodeUserControlledSortAndFilter() {
        createLibraryManga("Facet Manga", genre = "Action")

        val feed =
            OpdsFeedBuilder.getLibraryFeed(
                userId,
                BASE_URL,
                Locale.ENGLISH,
                OpdsMangaFilter(sort = "a&inject=1", filter = "f=1"),
                isSearch = false,
                pageNum = 1,
                sort = "a&inject=1",
                filter = "f=1",
            )

        assertTrue(feed.contains("sort=a%26inject%3D1"), "the sort value must be query-encoded, got:\n$feed")
        assertTrue(feed.contains("filter=f%3D1"), "the filter value must be query-encoded, got:\n$feed")
        // the raw forms must not appear inside hrefs (a raw '&' would serialize as '&amp;'
        // and smuggle an extra query param into the facet link; the `<id>` URN is exempt —
        // it carries the values as opaque identifiers, e.g. "sort_a&inject=1")
        assertFalse(feed.contains("sort=a&amp;inject"), "an unencoded & must not appear in an href")
        assertFalse(feed.contains("filter=f=1"), "an unencoded = must not appear in an href")
    }

    // --- Basic-auth username resolution ---

    @Test
    fun basicUsernameResolutionIsCaseInsensitive() {
        assertNotEquals(1, caseUserId)
        assertEquals(caseUserId, resolveBasicUsernameToId("OpdsCaseUser"))
        assertEquals(caseUserId, resolveBasicUsernameToId("opdsCaseuser"))
        assertEquals(caseUserId, resolveBasicUsernameToId("OPDSCASEUSER"))
    }

    @Test
    fun unknownBasicUsernameStillFallsBackToBuiltInAdmin() {
        assertEquals(1, resolveBasicUsernameToId("no_such_user_anywhere"))
    }

    // --- NSFW permission resolution ---

    @Test
    fun dbPermissionCheckReflectsTheResolvedUser() {
        // user without ACCESS_NSFW: the check that used to answer `true` via the pseudo
        // Admin(1) must deny NSFW for the resolved account
        assertFalse(hasPermission(permissionUserId, UserPermission.ACCESS_NSFW))

        transaction {
            UserPermissionsTable.insert {
                it[UserPermissionsTable.user] = permissionUserId
                it[UserPermissionsTable.permission] = UserPermission.ACCESS_NSFW.name
            }
        }
        assertTrue(hasPermission(permissionUserId, UserPermission.ACCESS_NSFW))

        // the built-in admin bypass is preserved
        assertTrue(hasPermission(1, UserPermission.ACCESS_NSFW))
    }
}
