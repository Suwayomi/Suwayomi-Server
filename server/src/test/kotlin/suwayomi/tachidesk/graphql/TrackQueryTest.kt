package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

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

        response.assertNoErrors()
        assertEquals(null, response.dataPath("trackRecord"), "trackRecord should be null for a different user")
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
