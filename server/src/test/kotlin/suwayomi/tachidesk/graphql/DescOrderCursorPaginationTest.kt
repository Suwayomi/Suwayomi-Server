package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.types.GraphQLResponse
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/** Regression tests for DESC-order cursor pagination on non-unique keys:
 *  the id tie-break must follow the primary key's direction or the cursor
 *  deadlocks on an all-tied page. */
class DescOrderCursorPaginationTest : GraphQLTest() {
    /**
     * Insert one chapter per [lastReadAtValues] entry (ids follow insertion order) plus the
     * per-user row carrying `lastReadAt`, and return the chapter ids in insertion order.
     */
    private fun createChaptersWithLastReadAt(
        mangaId: Int,
        lastReadAtValues: List<Long>,
    ): List<Int> =
        transaction {
            val ids =
                ChapterTable
                    .batchInsert(lastReadAtValues.indices.toList()) { index ->
                        this[ChapterTable.url] = "desc-cursor-$mangaId-$index"
                        this[ChapterTable.name] = "desc-cursor-$index"
                        this[ChapterTable.sourceOrder] = index
                        this[ChapterTable.manga] = mangaId
                        this[ChapterTable.memo] = JsonObject.EMPTY
                    }.map { it[ChapterTable.id].value }
            ChapterUserTable.batchInsert(ids.zip(lastReadAtValues)) { (chapterId, lastReadAt) ->
                this[ChapterUserTable.chapter] = chapterId
                this[ChapterUserTable.user] = 1
                this[ChapterUserTable.lastReadAt] = lastReadAt
            }
            ids
        }

    private fun nodeIds(
        response: GraphQLResponse<*>,
        root: String,
    ): List<String> = (response.dataPath(root, "nodes") as List<*>).map { (it as Map<*, *>)["id"].toString() }

    private fun chaptersPage(
        mangaId: Int,
        orderBy: String,
        orderType: String,
        args: String,
        selectTotal: Boolean = false,
    ): GraphQLResponse<*> =
        graphql(
            """
            query {
                chapters(
                    condition: { mangaId: $mangaId }
                    order: [{ by: $orderBy, byType: $orderType }]
                    $args
                ) {
                    ${if (selectTotal) "totalCount" else ""}
                    pageInfo {
                        hasNextPage
                        hasPreviousPage
                        endCursor
                    }
                    nodes { id }
                }
            }
            """.trimIndent(),
        )

    /** Pages forward via endCursor until hasNextPage=false; fails loudly on the
     *  deadlock shapes (empty page with hasNextPage, null endCursor). */
    private fun collectChapterIds(
        mangaId: Int,
        orderBy: String,
        orderType: String,
        pageSize: Int,
        maxPages: Int = 100,
    ): Pair<List<String>, Int> {
        val visited = mutableListOf<String>()
        var after: String? = null
        var total = -1
        var pages = 0
        while (true) {
            val afterArg = after?.let { """, after: "$it"""" } ?: ""
            val response =
                chaptersPage(
                    mangaId,
                    orderBy,
                    orderType,
                    "first: $pageSize$afterArg",
                    selectTotal = true,
                )

            response.assertNoErrors()
            total = response.dataPath("chapters", "totalCount") as Int
            val pageIds = nodeIds(response, "chapters")
            val hasNextPage = response.dataPath("chapters", "pageInfo", "hasNextPage") as Boolean
            val endCursor = response.dataPath("chapters", "pageInfo", "endCursor") as String?

            if (pageIds.isEmpty()) {
                assertFalse(hasNextPage, "an empty page must not report hasNextPage=true (cursor deadlock)")
                return visited to total
            }

            visited += pageIds
            if (!hasNextPage) return visited to total
            after =
                endCursor
                    ?: fail("hasNextPage=true but endCursor is null — the cursor cannot advance (pagination deadlock)")
            if (++pages > maxPages) fail("cursor pagination did not terminate after $maxPages pages")
        }
    }

    // ---------- DESC forward iteration over tied values ----------

    @Test
    fun `descending order by a non-unique key visits every chapter exactly once`() {
        val mangaId = createLibraryManga("DescCursorChapters")
        // heavy ties: blocks of 4/8/6/2/4 so several page boundaries land inside a block
        val values =
            listOf(300L, 300, 300, 300) +
                List(8) { 200L } +
                List(6) { 100L } +
                listOf(50L, 50) +
                List(4) { 0L }
        val ids = createChaptersWithLastReadAt(mangaId, values)
        // the deterministic order a client must observe: value DESC, ties by id DESC
        val expected =
            ids
                .zip(values)
                .sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenByDescending { it.first })
                .map { it.first.toString() }

        val (visited, total) = collectChapterIds(mangaId, "LAST_READ_AT", "DESC", pageSize = 5)

        assertEquals(ids.size, total)
        assertEquals(visited.size, visited.toSet().size, "pages must not repeat nodes")
        assertEquals(expected, visited, "every chapter must be visited exactly once in deterministic order")
    }

    @Test
    fun `descending order over a single tie block does not stall`() {
        val mangaId = createLibraryManga("DescCursorAllTied")
        // one tie block wider than a page: the cursor lands mid-block on every step
        val ids = createChaptersWithLastReadAt(mangaId, List(7) { 0L })
        val expected = ids.sortedDescending().map { it.toString() }

        val (visited, total) = collectChapterIds(mangaId, "LAST_READ_AT", "DESC", pageSize = 3)

        assertEquals(ids.size, total)
        assertEquals(expected, visited)
    }

    // ---------- mangas LAST_FETCHED_AT deadlock (all values default to 0) ----------

    @Test
    fun `descending mangas by lastFetchedAt does not deadlock on tied zero values`() {
        val titles = (0 until 6).map { "DescFetchedAtManga$it" }
        val ids = titles.map { createLibraryManga(it) }
        val titleList = titles.joinToString(", ", "[", "]") { "\"$it\"" }

        val visited = mutableListOf<String>()
        var after: String? = null
        var total = -1
        var pages = 0
        while (true) {
            val afterArg = after?.let { """, after: "$it"""" } ?: ""
            val response =
                graphql(
                    """
                    query {
                        mangas(
                            filter: { title: { in: $titleList } }
                            order: [{ by: LAST_FETCHED_AT, byType: DESC }]
                            first: 2$afterArg
                        ) {
                            totalCount
                            pageInfo {
                                hasNextPage
                                endCursor
                            }
                            nodes { id }
                        }
                    }
                    """.trimIndent(),
                )

            response.assertNoErrors()
            total = response.dataPath("mangas", "totalCount") as Int
            val pageIds = nodeIds(response, "mangas")
            val hasNextPage = response.dataPath("mangas", "pageInfo", "hasNextPage") as Boolean
            val endCursor = response.dataPath("mangas", "pageInfo", "endCursor") as String?

            if (pageIds.isEmpty()) {
                assertFalse(hasNextPage, "an empty page must not report hasNextPage=true (cursor deadlock)")
                break
            }

            visited += pageIds
            if (!hasNextPage) break
            after = endCursor ?: fail("hasNextPage=true but endCursor is null — the cursor cannot advance")
            if (++pages > 20) fail("cursor pagination did not terminate after 20 pages")
        }

        assertEquals(ids.size, total)
        assertEquals(ids.sortedDescending().map { it.toString() }, visited)
    }

    // ---------- ASC regression ----------

    @Test
    fun `ascending order by a non-unique key still visits every chapter`() {
        val mangaId = createLibraryManga("AscCursorChapters")
        val values = listOf(0L, 0, 0, 100, 100, 100, 100, 200, 200, 300)
        val ids = createChaptersWithLastReadAt(mangaId, values)
        val expected =
            ids
                .zip(values)
                .sortedWith(compareBy<Pair<Int, Long>> { it.second }.thenBy { it.first })
                .map { it.first.toString() }

        val (visited, total) = collectChapterIds(mangaId, "LAST_READ_AT", "ASC", pageSize = 4)

        assertEquals(ids.size, total)
        assertEquals(expected, visited)
    }

    // ---------- before / last (backward path) ----------

    @Test
    fun `before and last page backwards through a descending tie block`() {
        val mangaId = createLibraryManga("DescCursorBefore")
        val values = listOf(5L, 5, 5, 3, 3, 1)
        val ids = createChaptersWithLastReadAt(mangaId, values)
        // forward order (value DESC, id DESC): [c3, c2, c1, c5, c4, c6]
        val forward =
            ids
                .zip(values)
                .sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenByDescending { it.first })
                .map { it.first }

        // cursor of the last row inside the 5-block; composite cursors are "id-value"
        val midBlockCursor = "${forward[2]}-5"

        // before the mid-block cursor returns the rows above it inside the same block
        val before = chaptersPage(mangaId, "LAST_READ_AT", "DESC", """first: 10, before: "$midBlockCursor"""")
        before.assertNoErrors()
        assertEquals(forward.take(2).map { it.toString() }, nodeIds(before, "chapters"))
        assertEquals(false, before.dataPath("chapters", "pageInfo", "hasPreviousPage"))
        assertEquals(true, before.dataPath("chapters", "pageInfo", "hasNextPage"))

        // plain `last` returns the tail of the whole set in forward order
        val tail = chaptersPage(mangaId, "LAST_READ_AT", "DESC", "last: 2")
        tail.assertNoErrors()
        assertEquals(forward.takeLast(2).map { it.toString() }, nodeIds(tail, "chapters"))

        // `last` + `before` returns the tail of the before-window in forward order
        val windowTail = chaptersPage(mangaId, "LAST_READ_AT", "DESC", """last: 2, before: "$midBlockCursor"""")
        windowTail.assertNoErrors()
        assertEquals(forward.take(2).map { it.toString() }, nodeIds(windowTail, "chapters"))

        // `after` + `before` selects the window between two cursors across a block boundary
        val window =
            chaptersPage(
                mangaId,
                "LAST_READ_AT",
                "DESC",
                """first: 10, after: "${forward[0]}-5", before: "${forward[4]}-3"""",
            )
        window.assertNoErrors()
        assertEquals(forward.subList(1, 4).map { it.toString() }, nodeIds(window, "chapters"))
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            ChapterUserTable,
            CategoryMangaTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
        }
    }
}
