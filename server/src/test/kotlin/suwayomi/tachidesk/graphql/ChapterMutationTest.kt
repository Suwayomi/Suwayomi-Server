package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
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
import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterMutationTest : GraphQLTest() {
    private fun firstChapterId(mangaId: Int): Int =
        transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .first()[ChapterTable.id]
                .value
        }

    private fun lastPageReadOf(chapterId: Int): Int =
        transaction {
            ChapterUserTable
                .selectAll()
                .where { (ChapterUserTable.chapter eq chapterId) and (ChapterUserTable.user eq 1) }
                .first()[ChapterUserTable.lastPageRead]
        }

    private fun setChapterPageCount(
        chapterId: Int,
        pageCount: Int,
    ) {
        transaction {
            ChapterTable
                .update({ ChapterTable.id eq chapterId }) {
                    it[ChapterTable.pageCount] = pageCount
                }
        }
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
    fun `updateChapter does not crash when the chapter page count is unknown`() {
        // Regression test for the coerceIn crash in updateChapters.
        //
        // A chapter's pageCount defaults to -1 ("unknown") until its pages are fetched. The old
        // code clamped lastPageRead with `it.coerceIn(0, pageCount ?: 0)`, which throws
        // IllegalArgumentException when pageCount is -1 (minimum 0 > maximum -1). The fix uses
        // coerceAtMost(...).coerceAtLeast(0), which never throws and clamps to 0 when the page
        // count is unknown.
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 1, read = false) // pageCount stays at its -1 default
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
                mapOf("input" to mapOf("id" to chapterId, "patch" to mapOf("lastPageRead" to 5))),
            )

        response.assertNoErrors()
        assertEquals(chapterId, response.dataPath("updateChapter", "chapter", "id"))
        assertEquals(0, lastPageReadOf(chapterId), "lastPageRead should clamp to 0 when the page count is unknown")
    }

    @Test
    fun `updateChapter clamps lastPageRead to the chapter page count`() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 1, read = false)
        val chapterId = firstChapterId(mangaId)
        setChapterPageCount(chapterId, 10)

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
                mapOf("input" to mapOf("id" to chapterId, "patch" to mapOf("lastPageRead" to 15))),
            )

        response.assertNoErrors()
        assertEquals(10, lastPageReadOf(chapterId), "lastPageRead should clamp to the chapter page count")
    }

    private fun chapterIdsOf(mangaId: Int): List<Int> =
        transaction {
            ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.map { it[ChapterTable.id].value }
        }

    private fun readWithHistory(chapterIds: List<Int>) {
        transaction {
            ChapterUserTable.update({ (ChapterUserTable.chapter inList chapterIds) and (ChapterUserTable.user eq 1) }) {
                it[ChapterUserTable.isRead] = true
                it[ChapterUserTable.lastPageRead] = 7
                it[ChapterUserTable.lastReadAt] = 1_700_000_000
            }
        }
    }

    private fun readWithHistoryAs(
        userId: Int,
        chapterId: Int,
    ) {
        transaction {
            ChapterUserTable.insert {
                it[ChapterUserTable.user] = userId
                it[ChapterUserTable.chapter] = chapterId
                it[ChapterUserTable.isRead] = true
                it[ChapterUserTable.lastPageRead] = 7
                it[ChapterUserTable.lastReadAt] = 1_700_000_000
            }
        }
    }

    private data class UserChapterState(
        val isRead: Boolean,
        val lastPageRead: Int,
        val lastReadAt: Long,
    )

    private fun stateOf(
        chapterId: Int,
        userId: Int = 1,
    ): UserChapterState =
        transaction {
            ChapterUserTable
                .selectAll()
                .where { (ChapterUserTable.chapter eq chapterId) and (ChapterUserTable.user eq userId) }
                .first()
                .let {
                    UserChapterState(it[ChapterUserTable.isRead], it[ChapterUserTable.lastPageRead], it[ChapterUserTable.lastReadAt])
                }
        }

    private fun userChapterRowCount(): Long = transaction { ChapterUserTable.selectAll().count() }

    private val readStateWithoutHistory = UserChapterState(isRead = true, lastPageRead = 7, lastReadAt = 0)
    private val readStateWithHistory = UserChapterState(isRead = true, lastPageRead = 7, lastReadAt = 1_700_000_000)

    @Test
    fun `removeHistory by chapter drops only that chapter's history and keeps its read state`() {
        val mangaId = createLibraryManga("Manga")
        createChapters(mangaId, 3, read = false)
        val (removed, kept, neverRead) = chapterIdsOf(mangaId)
        readWithHistory(listOf(removed, kept))
        val rowsBefore = userChapterRowCount()

        val response =
            graphql(
                """
                mutation(${'$'}input: RemoveHistoryInput!) {
                    removeHistory(input: ${'$'}input) {
                        chapters {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("chapterIds" to listOf(removed, neverRead))),
            )

        response.assertNoErrors()
        assertEquals(listOf(mapOf("id" to removed)), response.dataPath("removeHistory", "chapters"))
        assertEquals(readStateWithoutHistory, stateOf(removed))
        assertEquals(readStateWithHistory, stateOf(kept))
        assertEquals(rowsBefore, userChapterRowCount())
    }

    @Test
    fun `removeHistory by manga drops the history of every chapter of that manga only`() {
        val removedManga = createLibraryManga("Removed")
        val keptManga = createLibraryManga("Kept")
        createChapters(removedManga, 2, read = false)
        createChapters(keptManga, 1, read = false)
        readWithHistory(chapterIdsOf(removedManga) + chapterIdsOf(keptManga))

        val response =
            graphql(
                """
                mutation(${'$'}input: RemoveHistoryInput!) {
                    removeHistory(input: ${'$'}input) {
                        chapters {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("mangaIds" to listOf(removedManga))),
            )

        response.assertNoErrors()
        assertEquals(2, (response.dataPath("removeHistory", "chapters") as List<*>).size)
        chapterIdsOf(removedManga).forEach { assertEquals(readStateWithoutHistory, stateOf(it)) }
        chapterIdsOf(keptManga).forEach { assertEquals(readStateWithHistory, stateOf(it)) }
    }

    @Test
    fun `clearHistory drops all history of the user and keeps read state`() {
        val mangaId = createLibraryManga("Manga")
        val otherMangaId = createLibraryManga("Other")
        createChapters(mangaId, 2, read = false)
        createChapters(otherMangaId, 2, read = false)
        val chapterIds = chapterIdsOf(mangaId) + chapterIdsOf(otherMangaId)
        readWithHistory(chapterIds)
        val otherUser = createTestUser("other")
        readWithHistoryAs(otherUser, chapterIds.first())
        val rowsBefore = userChapterRowCount()

        val response =
            graphql(
                """
                mutation {
                    clearHistory(input: {clientMutationId: "c"}) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals("c", response.dataPath("clearHistory", "clientMutationId"))
        chapterIds.forEach { assertEquals(readStateWithoutHistory, stateOf(it)) }
        assertEquals(readStateWithHistory, stateOf(chapterIds.first(), otherUser))
        assertEquals(rowsBefore, userChapterRowCount())
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
                    "input" to
                        mapOf(
                            "items" to
                                listOf(
                                    mapOf(
                                        "chapterIds" to chapterIds,
                                        "metas" to
                                            listOf(
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
                "input" to
                    mapOf(
                        "items" to
                            listOf(
                                mapOf(
                                    "chapterIds" to listOf(chapterId),
                                    "metas" to
                                        listOf(
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
        )
        transaction {
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
