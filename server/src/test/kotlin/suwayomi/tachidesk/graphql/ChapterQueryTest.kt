package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga

class ChapterQueryTest : GraphQLTest() {
    private val user2: UserType = UserType.Admin(2)

    @Test
    fun chapterReturnsChapterForOwner() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 5, read = true)

        val chapterId =
            org.jetbrains.exposed.v1.jdbc.transactions.transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .first()[ChapterTable.id]
                    .value
            }

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    chapter(id: ${'$'}id) {
                        id
                        name
                        user {
                            isRead
                        }
                    }
                }
                """.trimIndent(),
                mapOf("id" to chapterId),
            )

        response.assertNoErrors()
        assertEquals(chapterId, response.dataPath("chapter", "id"))
        assertEquals(true, response.dataPath("chapter", "user", "isRead"))
    }

    @Test
    fun chapterIsNullForOtherUser() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 5, read = true)

        val chapterId =
            org.jetbrains.exposed.v1.jdbc.transactions.transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .first()[ChapterTable.id]
                    .value
            }

        val response =
            graphql(
                """
                query(${'$'}id: Int!) {
                    chapter(id: ${'$'}id) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("id" to chapterId),
                user = user2,
            )

        response.assertNoErrors()
        // NOTE: expected to be null for a different user; currently the left-join in
        // ChapterTable.getWithUserData returns the chapter with null user data instead.
        org.junit.jupiter.api.Assertions
            .assertNull(response.dataPath("chapter"), "chapter should be null for a different user")
    }

    @Test
    fun chaptersListsOwnChapters() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 5, read = true)

        val response =
            graphql(
                """
                query(${'$'}mangaId: Int!) {
                    chapters(condition: { mangaId: ${'$'}mangaId }) {
                        totalCount
                        nodes {
                            id
                            name
                        }
                    }
                }
                """.trimIndent(),
                mapOf("mangaId" to mangaId),
            )

        response.assertNoErrors()
        assertEquals(5, response.dataPath("chapters", "totalCount"))
    }

    @Test
    fun chaptersFilterByIsRead() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 3, read = true)
        createChapters(mangaId, 2, read = false, start = 4)

        val response =
            graphql(
                """
                query(${'$'}mangaId: Int!) {
                    chapters(condition: { mangaId: ${'$'}mangaId }, filter: { isRead: { equalTo: true } }) {
                        totalCount
                    }
                }
                """.trimIndent(),
                mapOf("mangaId" to mangaId),
            )

        response.assertNoErrors()
        assertEquals(3, response.dataPath("chapters", "totalCount"))
    }

    @Test
    fun chaptersIsIsolatedPerUser() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 5, read = true)

        val response =
            graphql(
                """
                query(${'$'}mangaId: Int!) {
                    chapters(condition: { mangaId: ${'$'}mangaId }) {
                        totalCount
                    }
                }
                """.trimIndent(),
                mapOf("mangaId" to mangaId),
                user = user2,
            )

        response.assertNoErrors()
        // NOTE: expected to be 0 for a different user; currently the left-join in
        // ChapterTable.getWithUserData returns all chapters with null user data instead.
        assertEquals(0, response.dataPath("chapters", "totalCount"), "user 2 should not see user 1's chapters")
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
            CategoryMangaTable,
            CategoryTable,
        )
    }
}
