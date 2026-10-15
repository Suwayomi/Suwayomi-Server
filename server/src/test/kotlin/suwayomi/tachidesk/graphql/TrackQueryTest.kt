package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.graphql.queries.TrackQuery
import suwayomi.tachidesk.graphql.types.TrackerType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackQueryTest : GraphQLTest() {
    private val user2: UserType = UserType.Admin(2)

    private fun createTrackRecord(
        mangaId: Int,
        user: Int,
    ): Int =
        transaction {
            TrackRecordTable
                .insert {
                    it[TrackRecordTable.mangaId] = mangaId
                    it[TrackRecordTable.trackerId] = 1
                    it[TrackRecordTable.remoteId] = 100L
                    it[TrackRecordTable.title] = "Tracked Manga"
                    it[TrackRecordTable.lastChapterRead] = 5.0
                    it[TrackRecordTable.totalChapters] = 10
                    it[TrackRecordTable.status] = 1
                    it[TrackRecordTable.score] = 0.0
                    it[TrackRecordTable.remoteUrl] = "https://example.com/track"
                    it[TrackRecordTable.startDate] = 0L
                    it[TrackRecordTable.finishDate] = 0L
                    it[TrackRecordTable.user] = user
                }.let {
                    TrackRecordTable
                        .selectAll()
                        .where {
                            TrackRecordTable.mangaId eq mangaId and (TrackRecordTable.user eq user)
                        }.first()[TrackRecordTable.id]
                        .value
                }
        }

    private fun trackerType(
        id: Int,
        name: String,
    ) = TrackerType(
        id = id,
        name = name,
        icon = "",
        isLoggedIn = false,
        authUrl = null,
        supportsTrackDeletion = false,
        supportsReadingDates = false,
        supportsPrivateTracking = false,
    )

    @Test
    fun `tracker NAME cursor carries the id as tie-break`() {
        // trackers with identical names must both stay reachable: the cursor
        // (id-name) disambiguates rows that compare equal on the sort key
        val first = trackerType(1, "Same Name")
        val second = trackerType(2, "Same Name")
        val later = trackerType(3, "ZZZ")

        val cursor = TrackQuery.TrackerOrderBy.NAME.asCursor(first)
        assertEquals("1-Same Name", cursor.value)

        assertTrue(
            TrackQuery.TrackerOrderBy.NAME.greater(second, cursor),
            "same name with a higher id must sort after the cursor row",
        )
        assertFalse(
            TrackQuery.TrackerOrderBy.NAME.greater(first, cursor),
            "the cursor row itself must be excluded",
        )
        assertTrue(TrackQuery.TrackerOrderBy.NAME.greater(later, cursor))

        val secondCursor = TrackQuery.TrackerOrderBy.NAME.asCursor(second)
        assertTrue(TrackQuery.TrackerOrderBy.NAME.less(first, secondCursor))
        assertFalse(TrackQuery.TrackerOrderBy.NAME.less(second, secondCursor))
    }

    @Test
    fun `tracker NAME pagination does not repeat or skip entries`() {
        val seen = mutableListOf<Pair<String, Int>>()
        var after: String? = null
        var hasNextPage = false
        do {
            val response =
                graphql(
                    """
                    query(${'$'}after: Cursor) {
                        trackers(order: [{ by: NAME }], first: 2, after: ${'$'}after) {
                            nodes {
                                id
                                name
                            }
                            pageInfo {
                                endCursor
                                hasNextPage
                            }
                        }
                    }
                    """.trimIndent(),
                    mapOf("after" to after),
                )
            response.assertNoErrors()
            @Suppress("UNCHECKED_CAST")
            val nodes = response.dataPath("trackers", "nodes") as List<Map<String, Any>>
            seen += nodes.map { it["name"] as String to (it["id"] as Number).toInt() }
            after = response.dataPath("trackers", "pageInfo", "endCursor") as? String
            hasNextPage = response.dataPath("trackers", "pageInfo", "hasNextPage") as Boolean
        } while (hasNextPage)

        assertTrue(seen.size > 1, "expected multiple trackers")
        assertEquals(seen.size, seen.map { it.second }.distinct().size, "no tracker may appear twice across pages")
        // sorted by (name, id): equal names must still make progress via the id tie-break
        assertEquals(seen.sortedWith(compareBy({ it.first }, { it.second })), seen, "pages must come back in name order")
    }

    @Test
    fun trackersReturnsTrackerList() {
        val response =
            graphql(
                """
                query {
                    trackers {
                        totalCount
                        nodes {
                            id
                            name
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        val totalCount = response.dataPath("trackers", "totalCount") as Int
        assertTrue(totalCount > 0, "there should be at least one tracker")
    }

    @Test
    fun trackRecordReturnsRecordForOwner() {
        val mangaId = createLibraryManga("Manga")
        val recordId = createTrackRecord(mangaId, 1)

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    trackRecord(id: ${'$'}id) {
                        id
                        title
                        trackerId
                    }
                }
                """.trimIndent(),
                mapOf("id" to recordId),
            )

        response.assertNoErrors()
        assertEquals(recordId, response.dataPath("trackRecord", "id"))
        assertEquals("Tracked Manga", response.dataPath("trackRecord", "title"))
    }

    @Test
    fun trackRecordIsNullForOtherUser() {
        val mangaId = createLibraryManga("Manga")
        val recordId = createTrackRecord(mangaId, 1)

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    trackRecord(id: ${'$'}id) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("id" to recordId),
                user = user2,
            )

        // error is expected since single item queries error when not usable
        response.assertHasError()
    }

    @Test
    fun trackRecordsListsOwnRecords() {
        val mangaId = createLibraryManga("Manga")
        createTrackRecord(mangaId, 1)

        val response =
            graphql(
                """
                query {
                    trackRecords {
                        totalCount
                        nodes {
                            id
                            title
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(1, response.dataPath("trackRecords", "totalCount"))
    }

    @Test
    fun trackRecordsIsIsolatedPerUser() {
        val mangaId = createLibraryManga("Manga")
        createTrackRecord(mangaId, 1)

        val response =
            graphql(
                """
                query {
                    trackRecords {
                        totalCount
                    }
                }
                """.trimIndent(),
                user = user2,
            )

        response.assertNoErrors()
        assertEquals(0, response.dataPath("trackRecords", "totalCount"), "user 2 should not see user 1's track records")
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            TrackRecordTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }
}
