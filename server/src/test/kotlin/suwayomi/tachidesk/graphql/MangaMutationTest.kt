package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MangaMutationTest : GraphQLTest() {
    @Test
    fun updateManga() {
        val mangaId = createLibraryManga("Manga")

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateMangaInput!) {
                    updateManga(input: ${'$'}input) {
                        manga {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to mangaId, "patch" to mapOf("inLibrary" to true))),
            )

        response.assertNoErrors()
        assertEquals(mangaId, response.dataPath("updateManga", "manga", "id"))
    }

    @Test
    fun updateMangas() {
        val mangaId1 = createLibraryManga("Manga 1")
        val mangaId2 = createLibraryManga("Manga 2")

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateMangasInput!) {
                    updateMangas(input: ${'$'}input) {
                        mangas {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to listOf(mangaId1, mangaId2), "patch" to mapOf("inLibrary" to true))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("updateMangas", "mangas") as List<*>).size)
    }

    @Test
    fun updateMangaInLibraryDoesNotAffectOtherUsers() {
        val mangaId = createLibraryManga("Manga")
        val userId2 = createTestUser("isolationuser")
        val user2 = UserType.User(userId2, listOf(UserPermission.DOWNLOAD_CHAPTERS))

        // user 2 adds the same manga to their own library
        graphql(
            """
            mutation(${'$'}input: UpdateMangaInput!) {
                updateManga(input: ${'$'}input) {
                    manga {
                        id
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("id" to mangaId, "patch" to mapOf("inLibrary" to true))),
            user = user2,
        ).assertNoErrors()

        // user 1 removes it from their own library
        graphql(
            """
            mutation(${'$'}input: UpdateMangaInput!) {
                updateManga(input: ${'$'}input) {
                    manga {
                        id
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("id" to mangaId, "patch" to mapOf("inLibrary" to false))),
        ).assertNoErrors()

        // user 1's row is out of the library, user 2's row is untouched
        val states =
            transaction {
                MangaUserTable
                    .selectAll()
                    .where { (MangaUserTable.manga eq mangaId) and (MangaUserTable.user inList listOf(1, userId2)) }
                    .associate { it[MangaUserTable.user].value to it }
            }
        assertEquals(false, states.getValue(1)[MangaUserTable.inLibrary])
        assertTrue(states.getValue(userId2)[MangaUserTable.inLibrary])
    }

    @Test
    fun updateMangasInLibraryDoesNotAffectOtherUsers() {
        val mangaId1 = createLibraryManga("Manga 1")
        val mangaId2 = createLibraryManga("Manga 2")
        val userId2 = createTestUser("isolationuser2")
        val user2 = UserType.User(userId2, listOf(UserPermission.DOWNLOAD_CHAPTERS))

        // user 2 adds both mangas to their own library
        graphql(
            """
            mutation(${'$'}input: UpdateMangasInput!) {
                updateMangas(input: ${'$'}input) {
                    mangas {
                        id
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("ids" to listOf(mangaId1, mangaId2), "patch" to mapOf("inLibrary" to true))),
            user = user2,
        ).assertNoErrors()

        // user 1 removes both from their own library
        graphql(
            """
            mutation(${'$'}input: UpdateMangasInput!) {
                updateMangas(input: ${'$'}input) {
                    mangas {
                        id
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("ids" to listOf(mangaId1, mangaId2), "patch" to mapOf("inLibrary" to false))),
        ).assertNoErrors()

        // user 1's rows are out of the library, user 2's rows are untouched
        val states =
            transaction {
                MangaUserTable
                    .selectAll()
                    .where { (MangaUserTable.manga inList listOf(mangaId1, mangaId2)) and (MangaUserTable.user inList listOf(1, userId2)) }
                    .associate { it[MangaUserTable.user].value to it[MangaUserTable.manga].value to it }
            }
        listOf(mangaId1, mangaId2).forEach { mangaId ->
            assertEquals(false, states.getValue(1 to mangaId)[MangaUserTable.inLibrary])
            assertTrue(states.getValue(userId2 to mangaId)[MangaUserTable.inLibrary])
        }
    }

    @Test
    fun setMangaMeta() {
        val mangaId = createLibraryManga("Manga")

        val response =
            graphql(
                """
                mutation(${'$'}input: SetMangaMetaInput!) {
                    setMangaMeta(input: ${'$'}input) {
                        meta {
                            key
                            value
                            mangaId
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("meta" to mapOf("key" to "mKey", "value" to "mValue", "mangaId" to mangaId))),
            )

        response.assertNoErrors()
        assertEquals("mKey", response.dataPath("setMangaMeta", "meta", "key"))
        assertEquals(mangaId, response.dataPath("setMangaMeta", "meta", "mangaId"))
    }

    @Test
    fun deleteMangaMeta() {
        val mangaId = createLibraryManga("Manga")
        graphql(
            """
            mutation(${'$'}input: SetMangaMetaInput!) {
                setMangaMeta(input: ${'$'}input) {
                    meta {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("meta" to mapOf("key" to "mKey", "value" to "mValue", "mangaId" to mangaId))),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteMangaMetaInput!) {
                    deleteMangaMeta(input: ${'$'}input) {
                        meta {
                            key
                        }
                        manga {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("mangaId" to mangaId, "key" to "mKey")),
            )

        response.assertNoErrors()
        assertEquals("mKey", response.dataPath("deleteMangaMeta", "meta", "key"))
        assertEquals(mangaId, response.dataPath("deleteMangaMeta", "manga", "id"))
    }

    @Test
    fun setMangaMetas() {
        val mangaId = createLibraryManga("Manga")

        val response =
            graphql(
                """
                mutation(${'$'}input: SetMangaMetasInput!) {
                    setMangaMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                        mangas {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to
                        mapOf(
                            "items" to
                                listOf(
                                    mapOf(
                                        "mangaIds" to listOf(mangaId),
                                        "metas" to
                                            listOf(
                                                mapOf("key" to "mk1", "value" to "mv1"),
                                                mapOf("key" to "mk2", "value" to "mv2"),
                                            ),
                                    ),
                                ),
                        ),
                ),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("setMangaMetas", "metas") as List<*>).size)
        assertEquals(1, (response.dataPath("setMangaMetas", "mangas") as List<*>).size)
    }

    @Test
    fun deleteMangaMetas() {
        val mangaId = createLibraryManga("Manga")
        graphql(
            """
            mutation(${'$'}input: SetMangaMetasInput!) {
                setMangaMetas(input: ${'$'}input) {
                    metas {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "input" to
                    mapOf(
                        "items" to
                            listOf(
                                mapOf(
                                    "mangaIds" to listOf(mangaId),
                                    "metas" to
                                        listOf(
                                            mapOf("key" to "mk1", "value" to "mv1"),
                                            mapOf("key" to "mk2", "value" to "mv2"),
                                        ),
                                ),
                            ),
                    ),
            ),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteMangaMetasInput!) {
                    deleteMangaMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("items" to listOf(mapOf("mangaIds" to listOf(mangaId), "keys" to listOf("mk1", "mk2"))))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("deleteMangaMetas", "metas") as List<*>).size)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            MangaMetaTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }
}
