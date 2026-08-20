package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga

class ChapterMutationTest : GraphQLTest() {
    private fun firstChapterId(mangaId: Int): Int =
        transaction {
            ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.first()[ChapterTable.id].value
        }

    @Test
    fun updateChapter() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 3, read = false)
        val chapterId = firstChapterId(mangaId)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateChapterInput!) {
                    updateChapter(input: ${'$'}input) {
                        chapter {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to chapterId, "patch" to mapOf("isRead" to true))),
            )

        response.assertNoErrors()
        assertEquals(chapterId, response.dataPath("updateChapter", "chapter", "id"))
    }

    @Test
    fun updateChapters() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 3, read = false)
        val chapterIds =
            transaction {
                ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.map { it[ChapterTable.id].value }
            }

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateChaptersInput!) {
                    updateChapters(input: ${'$'}input) {
                        chapters {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to chapterIds, "patch" to mapOf("isRead" to true))),
            )

        response.assertNoErrors()
        assertEquals(3, (response.dataPath("updateChapters", "chapters") as List<*>).size)
    }

    @Test
    fun setChapterMeta() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 1, read = false)
        val chapterId = firstChapterId(mangaId)

        val response =
            graphql(
                """
                mutation(${'$'}input: SetChapterMetaInput!) {
                    setChapterMeta(input: ${'$'}input) {
                        meta {
                            key
                            value
                            chapterId
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("meta" to mapOf("key" to "cKey", "value" to "cValue", "chapterId" to chapterId))),
            )

        response.assertNoErrors()
        assertEquals("cKey", response.dataPath("setChapterMeta", "meta", "key"))
        assertEquals(chapterId, response.dataPath("setChapterMeta", "meta", "chapterId"))
    }

    @Test
    fun deleteChapterMeta() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 1, read = false)
        val chapterId = firstChapterId(mangaId)
        graphql(
            """
            mutation(${'$'}input: SetChapterMetaInput!) {
                setChapterMeta(input: ${'$'}input) {
                    meta {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf("input" to mapOf("meta" to mapOf("key" to "cKey", "value" to "cValue", "chapterId" to chapterId))),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteChapterMetaInput!) {
                    deleteChapterMeta(input: ${'$'}input) {
                        meta {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("chapterId" to chapterId, "key" to "cKey")),
            )

        response.assertNoErrors()
        assertEquals("cKey", response.dataPath("deleteChapterMeta", "meta", "key"))
    }

    @Test
    fun setChapterMetas() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 2, read = false)
        val chapterIds =
            transaction {
                ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.map { it[ChapterTable.id].value }
            }

        val response =
            graphql(
                """
                mutation(${'$'}input: SetChapterMetasInput!) {
                    setChapterMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to mapOf(
                        "items" to listOf(
                            mapOf(
                                "chapterIds" to chapterIds,
                                "metas" to listOf(
                                    mapOf("key" to "ck1", "value" to "cv1"),
                                    mapOf("key" to "ck2", "value" to "cv2"),
                                ),
                            ),
                        ),
                    ),
                ),
            )

        response.assertNoErrors()
        assertEquals(4, (response.dataPath("setChapterMetas", "metas") as List<*>).size)
    }

    @Test
    fun deleteChapterMetas() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 1, read = false)
        val chapterId = firstChapterId(mangaId)
        graphql(
            """
            mutation(${'$'}input: SetChapterMetasInput!) {
                setChapterMetas(input: ${'$'}input) {
                    metas {
                        key
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "input" to mapOf(
                    "items" to listOf(
                        mapOf(
                            "chapterIds" to listOf(chapterId),
                            "metas" to listOf(
                                mapOf("key" to "ck1", "value" to "cv1"),
                                mapOf("key" to "ck2", "value" to "cv2"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteChapterMetasInput!) {
                    deleteChapterMetas(input: ${'$'}input) {
                        metas {
                            key
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("items" to listOf(mapOf("chapterIds" to listOf(chapterId), "keys" to listOf("ck1", "ck2"))))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("deleteChapterMetas", "metas") as List<*>).size)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterMetaTable,
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
            CategoryMangaTable,
            CategoryTable,
        )
    }
}
