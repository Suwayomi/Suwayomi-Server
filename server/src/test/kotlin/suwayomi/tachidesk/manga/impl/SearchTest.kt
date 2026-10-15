package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.javalin.http.BadRequestResponse
import io.javalin.json.JavalinJackson
import io.javalin.json.toJsonString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.impl.Search.FilterChange
import suwayomi.tachidesk.manga.impl.Search.FilterObject
import suwayomi.tachidesk.manga.impl.Search.SerializableGroup
import suwayomi.tachidesk.manga.impl.Search.getFilterList
import suwayomi.tachidesk.manga.impl.Search.setFilter
import suwayomi.tachidesk.manga.impl.Search.sourceSearch
import suwayomi.tachidesk.manga.impl.util.source.GetSource.registerSource
import suwayomi.tachidesk.manga.impl.util.source.GetSource.unregisterSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.createSMangas
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchTest : ApplicationTest() {
    class FakeSearchableSource(
        id: Long,
    ) : StubSource(id) {
        var mangas: List<SManga> = emptyList()

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = MangasPage(mangas, false)
    }

    // high, unique id: FilterListTest registers stub sources at ids 0..N and the
    // (userId, sourceId) filter-change cache is a shared singleton — a collision
    // would replay another test's changes onto this 0-filter source
    private val sourceId = 777_001L
    private val source = FakeSearchableSource(sourceId)
    private val mangasCount = 10
    private val userId = 1

    @BeforeAll
    fun setup() {
        registerSource(sourceId to source)

        this.source.mangas = createSMangas(mangasCount)
    }

    @Test
    fun searchWorks() {
        val searchResults =
            runBlocking {
                sourceSearch(userId, sourceId, "all the mangas", 1)
            }

        assertEquals(mangasCount, searchResults.mangaList.size, "should return all the mangas")
    }

    @AfterAll
    fun teardown() {
        unregisterSource(this.sourceId)
    }
}

@Suppress("UNCHECKED_CAST")
class FilterListTest : ApplicationTest() {
    private val userId = 1

    open class EmptyFilterListSource(
        id: Long,
    ) : StubSource(id) {
        open var mFilterList = FilterList()

        override fun getFilterList(): FilterList = mFilterList
    }

    @Test
    fun `empty FilterList returns empty List`() =
        runTest {
            val source = registerSource(EmptyFilterListSource::class)
            source.mFilterList = FilterList()

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                0,
                filterList.size,
            )
        }

    class FilterListSource(
        id: Long,
    ) : EmptyFilterListSource(id) {
        class SelectFilter(
            name: String,
            values: Array<String>,
        ) : Filter.Select<String>(name, values)

        class TextFilter(
            name: String,
        ) : Filter.Text(name)

        class TestCheckBox(
            name: String,
        ) : Filter.CheckBox(name, false)

        class TriState(
            name: String,
            state: Int,
        ) : Filter.TriState(name, state)

        class Group(
            name: String,
            state: List<TestCheckBox>,
        ) : Filter.Group<TestCheckBox>(name, state)

        class Sort(
            name: String,
            values: Array<String>,
            state: Selection,
        ) : Filter.Sort(name, values, state)

        override var mFilterList =
            FilterList(
                Filter.Header("This is a header"),
                Filter.Separator(),
                SelectFilter("Select one of these:", arrayOf("this", "that", "none of them")),
                TextFilter("text filter"),
                TestCheckBox("check this or else!"),
                TriState("wanna hook up?", Filter.TriState.STATE_IGNORE),
                Group(
                    "my Todo",
                    listOf(
                        TestCheckBox("Write Tests"),
                        TestCheckBox("Write More Tests"),
                        TestCheckBox("Write Even More Tests"),
                    ),
                ),
                Sort(
                    "Sort",
                    arrayOf("Alphabetic", "Date published", "Rating"),
                    Filter.Sort.Selection(2, false),
                ),
            )
    }

    @Test
    fun convertsEveryTypeCorrectly() =
        runTest {
            val source = registerSource(FilterListSource::class)
            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                FilterObject("Header", source.mFilterList[0]),
                filterList[0],
            )
            assertEquals(
                FilterObject("Separator", source.mFilterList[1]),
                filterList[1],
            )
            assertEquals(
                FilterObject("Select", source.mFilterList[2]),
                filterList[2],
            )
            assertEquals(
                FilterObject("Text", source.mFilterList[3]),
                filterList[3],
            )
            assertEquals(
                FilterObject("CheckBox", source.mFilterList[4]),
                filterList[4],
            )
            assertEquals(
                FilterObject("TriState", source.mFilterList[5]),
                filterList[5],
            )
            assertEquals(
                filterList[6],
                FilterObject(
                    "Group",
                    SerializableGroup(
                        source.mFilterList[6].name,
                        listOf(
                            FilterObject("CheckBox", (source.mFilterList[6].state as List<Filter<*>>)[0]),
                            FilterObject("CheckBox", (source.mFilterList[6].state as List<Filter<*>>)[1]),
                            FilterObject("CheckBox", (source.mFilterList[6].state as List<Filter<*>>)[2]),
                        ),
                    ),
                ),
            )
            assertEquals(
                FilterObject("Sort", source.mFilterList[7]),
                filterList[7],
            )

            // make sure that we can convert this to json
            JavalinJackson().toJsonString(filterList)
        }

    suspend fun setFilter(
        sourceId: Long,
        filterChange: FilterChange,
    ) {
        setFilter(userId, sourceId, listOf(filterChange))
    }

    @Test
    fun `Header and Separator should not change`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(0, "change!")),
            )

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(1, "change!")),
            )

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                filterList[0].filter.state,
                0,
            )

            assertEquals(
                filterList[1].filter.state,
                0,
            )
        }

    @Test
    fun `Select changes are Int`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(2, "1")),
            )

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                filterList[2].filter.state,
                1,
            )
        }

    @Test
    fun `Text changes are String`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(3, "I'm a changed man!")),
            )

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                filterList[3].filter.state,
                "I'm a changed man!",
            )
        }

    @Test
    fun `CheckBox changes are Boolean`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(4, "true")),
            )

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                filterList[4].filter.state,
                true,
            )
        }

    @Test
    fun `TriState changes are Int`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(5, "1")),
            )

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                filterList[5].filter.state,
                Filter.TriState.STATE_INCLUDE,
            )
        }

    @Test
    fun `Group changes are Filters`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(6, """{"position":0,"state":"true"}""")),
            )

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                (filterList[6].filter.state as List<FilterObject>)[0].filter.state,
                true,
            )
        }

    @Test
    fun `Sort changes are Filter,Sort,Selection`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(7, """{"index":1,"ascending":"true"}""")),
            )

            val filterList = getFilterList(userId, source.id, false)

            assertEquals(
                filterList[7].filter.state,
                Filter.Sort.Selection(1, true),
            )
        }

    /** Builds a fresh [FilterList] per call like a real source — never shares the instance. */
    class FreshFilterListSource(
        id: Long,
    ) : EmptyFilterListSource(id) {
        override fun getFilterList(): FilterList =
            FilterList(
                FilterListSource.TextFilter("text filter"),
                FilterListSource.TestCheckBox("check filter"),
            )
    }

    @Test
    fun `filter changes do not leak between users`() =
        runTest {
            val source = registerSource(FreshFilterListSource::class)
            val otherUserId = userId + 1_000

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(0, "user1's filter"), FilterChange(1, "true")),
            )

            // the other user must see the pristine defaults, not user1's state
            val otherFilterList = getFilterList(otherUserId, source.id, false)
            assertEquals("", otherFilterList[0].filter.state)
            assertEquals(false, otherFilterList[1].filter.state)

            // and user1's stored changes still apply for user1
            val userFilterList = getFilterList(userId, source.id, false)
            assertEquals("user1's filter", userFilterList[0].filter.state)
            assertEquals(true, userFilterList[1].filter.state)
        }

    @Test
    fun `setFilter validates position and value`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            // out-of-range position
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(99, "x")))
            }
            // non-numeric Select value
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(2, "not-a-number")))
            }
            // out-of-range Select index
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(2, "99")))
            }
            // non-boolean CheckBox value
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(4, "notabool")))
            }
            // non-integer TriState value
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(5, "x")))
            }
            // malformed Group payload
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(6, "not json")))
            }
            // out-of-range Group inner position
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(6, """{"position":99,"state":"true"}""")))
            }
            // malformed Sort payload
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(7, "not json")))
            }

            // rejected changes must not corrupt the stored baseline — a valid change made
            // afterwards applies on top of nothing
            setFilter(userId, source.id, listOf(FilterChange(3, "clean")))
            val filterList = getFilterList(userId, source.id, false)
            assertEquals("clean", filterList[3].filter.state)
            assertEquals(0, filterList[2].filter.state)
        }

    @Test
    fun `setFilter drops stored changes that no longer apply`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            // stored while the source still offered a Sort filter at position 7
            setFilter(
                userId,
                source.id,
                listOf(FilterChange(7, """{"index":1,"ascending":true}""")),
            )

            // extension "upgrade": the filter list shrank, so the stored change at
            // position 7 is stale. Without auto-recovery strict validation would
            // reject every future write for this (user, source) until a manual reset.
            source.mFilterList =
                FilterList(
                    FilterListSource.TextFilter("text filter"),
                    FilterListSource.TestCheckBox("check filter"),
                )

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(0, "recovered")),
            )

            val filterList = getFilterList(userId, source.id, false)
            assertEquals("recovered", filterList[0].filter.state)
            assertEquals(false, filterList[1].filter.state)

            // the stale change was purged, not just skipped — restoring the original
            // layout must not resurrect the old Sort selection
            source.mFilterList = FilterListSource(-1L).mFilterList
            val restoredList = getFilterList(userId, source.id, false)
            assertEquals(Filter.Sort.Selection(2, false), restoredList[7].filter.state)
        }

    @Test
    fun `setFilter still rejects an invalid change after dropping stale ones`() =
        runTest {
            val source = registerSource(FilterListSource::class)

            setFilter(
                userId,
                source.id,
                listOf(FilterChange(7, """{"index":1,"ascending":true}""")),
            )

            source.mFilterList = FilterList(FilterListSource.TextFilter("text filter"))

            // the stale position-7 change is recoverable, but a bad new change must
            // still fail strict validation with 400 semantics
            assertFailsWith<BadRequestResponse> {
                setFilter(userId, source.id, listOf(FilterChange(5, "x")))
            }
        }

    companion object {
        private var sourceCount = 0L

        private fun registerSource(sourceClass: KClass<*>): EmptyFilterListSource =
            synchronized(sourceClass) {
                val source = sourceClass.primaryConstructor!!.call(sourceCount) as EmptyFilterListSource
                registerSource(sourceCount to source)
                sourceCount++
                source
            }

        @AfterAll
        @JvmStatic
        fun teardown() {
            (0 until sourceCount).forEach { unregisterSource(it) }
        }
    }
}
