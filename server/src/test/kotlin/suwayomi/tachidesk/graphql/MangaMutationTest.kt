package suwayomi.tachidesk.graphql

import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals

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
