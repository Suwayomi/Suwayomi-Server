package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.types.GraphQLResponse
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
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

        val before = chaptersPage(mangaId, "LAST_READ_AT", "DESC", """first: 10, before: "$midBlockCursor"""")
        before.assertNoErrors()
        assertEquals(forward.take(2).map { it.toString() }, nodeIds(before, "chapters"))
        assertEquals(false, before.dataPath("chapters", "pageInfo", "hasPreviousPage"))
        assertEquals(true, before.dataPath("chapters", "pageInfo", "hasNextPage"))

        val tail = chaptersPage(mangaId, "LAST_READ_AT", "DESC", "last: 2")
        tail.assertNoErrors()
        assertEquals(forward.takeLast(2).map { it.toString() }, nodeIds(tail, "chapters"))

        val windowTail = chaptersPage(mangaId, "LAST_READ_AT", "DESC", """last: 2, before: "$midBlockCursor"""")
        windowTail.assertNoErrors()
        assertEquals(forward.take(2).map { it.toString() }, nodeIds(windowTail, "chapters"))

        // before is ignored once after is set
        val window =
            chaptersPage(
                mangaId,
                "LAST_READ_AT",
                "DESC",
                """first: 10, after: "${forward[0]}-5", before: "${forward[4]}-3"""",
            )
        window.assertNoErrors()
        assertEquals(forward.drop(1).map { it.toString() }, nodeIds(window, "chapters"))
    }

    /** Insert one store per [names] entry (ids follow insertion order); [urlTags] controls the
     *  indexUrl order independently of the id order. Returns indexUrl -> id. */
    private fun createExtensionStores(
        urlPrefix: String,
        names: List<String>,
        urlTags: List<String> = names.indices.map { it.toString().padStart(2, '0') },
    ): Map<String, Int> =
        transaction {
            names.zip(urlTags).associate { (name, tag) ->
                val indexUrl = "$urlPrefix$tag/index.min.json"
                val id =
                    ExtensionStoreTable
                        .insertAndGetId {
                            it[ExtensionStoreTable.indexUrl] = indexUrl
                            it[ExtensionStoreTable.name] = name
                            it[badgeLabel] = "test"
                            it[signingKey] = "test"
                            it[contactWebsite] = "https://example.com"
                        }.value
                indexUrl to id
            }
        }

    private fun storeUrls(response: GraphQLResponse<*>): List<String> =
        (response.dataPath("extensionStores", "nodes") as List<*>)
            .map { (it as Map<*, *>)["indexUrl"] as String }

    private fun extensionStoresPage(
        urlPrefix: String,
        orderArg: String,
        args: String,
    ): GraphQLResponse<*> =
        graphql(
            """
            query {
                extensionStores(
                    filter: { indexUrl: { startsWith: "$urlPrefix" } }
                    order: [$orderArg]
                    $args
                ) {
                    pageInfo {
                        hasNextPage
                        hasPreviousPage
                        endCursor
                    }
                    nodes { indexUrl }
                }
            }
            """.trimIndent(),
        )

    private fun collectStoreUrls(
        urlPrefix: String,
        orderArg: String,
        pageSize: Int,
        maxPages: Int = 100,
    ): List<String> {
        val visited = mutableListOf<String>()
        var after: String? = null
        var pages = 0
        while (true) {
            val afterArg = after?.let { """, after: "$it"""" } ?: ""
            val response = extensionStoresPage(urlPrefix, orderArg, "first: $pageSize$afterArg")

            response.assertNoErrors()
            val pageUrls = storeUrls(response)
            val hasNextPage = response.dataPath("extensionStores", "pageInfo", "hasNextPage") as Boolean
            val endCursor = response.dataPath("extensionStores", "pageInfo", "endCursor") as String?

            if (pageUrls.isEmpty()) {
                assertFalse(hasNextPage, "an empty page must not report hasNextPage=true (cursor deadlock)")
                return visited
            }

            visited += pageUrls
            if (!hasNextPage) return visited
            after = endCursor ?: fail("hasNextPage=true but endCursor is null")
            if (++pages > maxPages) fail("cursor pagination did not terminate after $maxPages pages")
        }
    }

    @Test
    fun `extensionStores asc name with desc id order does not drop tied rows`() {
        // cursor compares (name, id) same-direction, so the requested ID DESC key is normalized to ASC
        val urlToId = createExtensionStores("$STORE_PREFIX-asc-", listOf("AA", "BB", "BB", "BB", "CC"))
        val ids = urlToId.values.toList()
        val order = "{ by: NAME, byType: ASC }, { by: ID, byType: DESC }"

        val forward = collectStoreUrls("$STORE_PREFIX-asc-", order, pageSize = 2).map { urlToId.getValue(it) }
        assertEquals(ids, forward)

        // last:2 window ending just above "CC"; its tail lands inside the BB tie block
        val tail = extensionStoresPage("$STORE_PREFIX-asc-", order, """last: 2, before: "${ids[4]}-CC"""")
        tail.assertNoErrors()
        assertEquals(ids.subList(2, 4), storeUrls(tail).map { urlToId.getValue(it) })

        val head = extensionStoresPage("$STORE_PREFIX-asc-", order, """last: 3, before: "${ids[3]}-BB"""")
        head.assertNoErrors()
        assertEquals(ids.subList(0, 3), storeUrls(head).map { urlToId.getValue(it) })
    }

    @Test
    fun `extensionStores desc name with asc id order does not drop tied rows`() {
        val urlToId = createExtensionStores("$STORE_PREFIX-desc-", listOf("AA", "BB", "BB", "BB", "CC"))
        val ids = urlToId.values.toList()
        val order = "{ by: NAME, byType: DESC }, { by: ID, byType: ASC }"

        // aligned order is (name DESC, id DESC), i.e. the exact reverse of insertion
        val forward = collectStoreUrls("$STORE_PREFIX-desc-", order, pageSize = 2).map { urlToId.getValue(it) }
        assertEquals(ids.reversed(), forward)

        // last:2 window ending just above "AA"; its tail lands at the top of the BB tie block
        val tail = extensionStoresPage("$STORE_PREFIX-desc-", order, """last: 2, before: "${ids[0]}-AA"""")
        tail.assertNoErrors()
        assertEquals(listOf(ids[2], ids[1]), storeUrls(tail).map { urlToId.getValue(it) })
    }

    @Test
    fun `extensionStores ordered by id keeps bare decimal cursors`() {
        val urlToId = createExtensionStores("$STORE_PREFIX-id-", listOf("S1", "S2", "S3", "S4", "S5"))
        val ids = urlToId.values.toList()

        val first = extensionStoresPage("$STORE_PREFIX-id-", "{ by: ID }", "first: 2")
        first.assertNoErrors()
        assertEquals(ids.take(2), storeUrls(first).map { urlToId.getValue(it) })
        assertEquals(
            ids[1].toString(),
            first.dataPath("extensionStores", "pageInfo", "endCursor"),
            "id-order cursor must be the bare id",
        )

        val next =
            extensionStoresPage("$STORE_PREFIX-id-", "{ by: ID }", """first: 2, after: "${ids[1]}"""")
        next.assertNoErrors()
        assertEquals(ids.subList(2, 4), storeUrls(next).map { urlToId.getValue(it) })

        val desc = extensionStoresPage("$STORE_PREFIX-id-", "{ by: ID, byType: DESC }", "first: 2")
        desc.assertNoErrors()
        assertEquals(ids.takeLast(2).reversed(), storeUrls(desc).map { urlToId.getValue(it) })
        assertEquals(
            ids[3].toString(),
            desc.dataPath("extensionStores", "pageInfo", "endCursor"),
            "desc id-order cursor must be the bare id",
        )
        val descNext =
            extensionStoresPage(
                "$STORE_PREFIX-id-",
                "{ by: ID, byType: DESC }",
                """first: 3, after: "${ids[3]}"""",
            )
        descNext.assertNoErrors()
        assertEquals(ids.take(3).reversed(), storeUrls(descNext).map { urlToId.getValue(it) })
    }

    @Test
    fun `extensionStores keeps later keys while dropping a non-first id order`() {
        // indexUrl order runs opposite to id order inside the MM tie block
        val urlToId =
            createExtensionStores(
                "$STORE_PREFIX-guard-",
                listOf("MM", "MM", "MM", "NN"),
                urlTags = listOf("z", "y", "x", "n"),
            )
        val ids = urlToId.values.toList()
        // (name ASC, indexUrl ASC): the MM block resolves to x,y,z -> ids[2],ids[1],ids[0]
        val expected = listOf(ids[2], ids[1], ids[0], ids[3])

        val withId =
            extensionStoresPage(
                "$STORE_PREFIX-guard-",
                "{ by: NAME }, { by: ID }, { by: INDEX_URL }",
                "first: 10",
            )
        withId.assertNoErrors()
        assertEquals(expected, storeUrls(withId).map { urlToId.getValue(it) })

        val withoutId =
            extensionStoresPage(
                "$STORE_PREFIX-guard-",
                "{ by: NAME }, { by: INDEX_URL }",
                "first: 10",
            )
        withoutId.assertNoErrors()
        assertEquals(expected, storeUrls(withoutId).map { urlToId.getValue(it) })
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
            // stores seeded by other test classes are left alone
            ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl like "$STORE_PREFIX%" }
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
        }
    }

    private companion object {
        const val STORE_PREFIX = "https://desc-cursor.example/store"
    }
}
