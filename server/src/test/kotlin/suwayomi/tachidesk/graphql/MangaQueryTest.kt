package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

class MangaQueryTest : GraphQLTest() {
    private val user2: UserType = UserType.Admin(2)

    @Test
    fun mangaReturnsMangaForOwner() {
        val mangaId = createLibraryManga("Owner Manga")

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    manga(id: ${'$'}id) {
                        id
                        title
                        user {
                            inLibrary
                        }
                    }
                }
                """.trimIndent(),
                mapOf("id" to mangaId),
            )

        response.assertNoErrors()
        assertEquals(mangaId, response.dataPath("manga", "id"))
        assertEquals("Owner Manga", response.dataPath("manga", "title"))
        assertEquals(true, response.dataPath("manga", "user", "inLibrary"))
    }

    @Test
    fun mangaIsNotInLibraryForOtherUser() {
        val mangaId = createLibraryManga("Isolated Manga")

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    manga(id: ${'$'}id) {
                        id
                        title
                        user {
                            inLibrary
                        }
                    }
                }
                """.trimIndent(),
                mapOf("id" to mangaId),
                user = user2,
            )

        response.assertNoErrors()
        assertEquals(mangaId, response.dataPath("manga", "id"))
        assertEquals("Isolated Manga", response.dataPath("manga", "title"))
        assertEquals(null, response.dataPath("manga", "user", "inLibrary"))
    }

    @Test
    fun mangasListsOnlyOwnLibrary() {
        createLibraryManga("Manga A")
        createLibraryManga("Manga B")

        val response =
            graphql(
                """
                query {
                    mangas {
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
        assertEquals(2, response.dataPath("mangas", "totalCount"))
    }

    @Test
    fun mangasInLibraryIsIsolatedPerUser() {
        createLibraryManga("Manga A")
        createLibraryManga("Manga B")

        val response =
            graphql(
                """
                query {
                    mangas(condition: { inLibrary: true }) {
                        totalCount
                    }
                }
                """.trimIndent(),
                user = user2,
            )

        response.assertNoErrors()
        assertEquals(0, response.dataPath("mangas", "totalCount"), "user 2 should not see user 1's mangas")
    }

    @Test
    fun mangasFilterByTitle() {
        createLibraryManga("One Piece")
        createLibraryManga("Naruto")

        val response =
            graphql(
                """
                query {
                    mangas(filter: { title: { startsWith: "One" } }) {
                        totalCount
                        nodes {
                            title
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(1, response.dataPath("mangas", "totalCount"))
        assertEquals("One Piece", response.dataPath("mangas", "nodes", "0", "title"))
    }

    @Test
    fun mangasFilterByInLibrary() {
        createLibraryManga("In Library 1")
        createLibraryManga("In Library 2")
        // create a non-library manga for user 1
        val notLibrary =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[MangaTable.title] = "Not In Library"
                        it[MangaTable.url] = "Not In Library"
                        it[MangaTable.sourceReference] = 1
                    }.value
            }
        transaction {
            MangaUserTable.insert {
                it[MangaUserTable.manga] = notLibrary
                it[MangaUserTable.user] = 1
                it[MangaUserTable.inLibrary] = false
            }
        }

        val response =
            graphql(
                """
                query {
                    mangas(filter: { inLibrary: { equalTo: true } }) {
                        totalCount
                        nodes {
                            id
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(2, response.dataPath("mangas", "totalCount"))
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }
}
