package suwayomi.tachidesk.manga.model

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.model.dataclass.PAGINATION_FACTOR
import suwayomi.tachidesk.manga.model.dataclass.PaginatedList
import suwayomi.tachidesk.manga.model.dataclass.paginatedFrom
import suwayomi.tachidesk.test.ApplicationTest

class PaginatedListTest : ApplicationTest() {
    @Test
    fun `empty list`() {
        val paginated = paginatedFrom(0) { listIndicesOf(0, 0) }

        assertEquals(
            PaginatedList(emptyList<Int>(), false),
            paginated,
        )
    }

    @Test
    fun `size smaller than PaginationFactor`() {
        val paginated = paginatedFrom(0) { listIndicesOf(0, PAGINATION_FACTOR - 1) }

        assertEquals(
            PaginatedList(listIndicesOf(0, PAGINATION_FACTOR - 1), false),
            paginated,
        )
    }

    @Test
    fun `one less than two times PaginationFactor`() {
        val masterLister = { listIndicesOf(0, PAGINATION_FACTOR * 2 - 1) }

        val firstPage = paginatedFrom(0, lister = masterLister)

        assertEquals(
            PaginatedList(listIndicesOf(0, PAGINATION_FACTOR), true),
            firstPage,
        )

        val secondPage = paginatedFrom(1, lister = masterLister)

        assertEquals(
            PaginatedList(listIndicesOf(PAGINATION_FACTOR, PAGINATION_FACTOR * 2 - 1), false),
            secondPage,
        )
    }

    @Test
    fun `two times PaginationFactor`() {
        val masterLister = { listIndicesOf(0, PAGINATION_FACTOR * 2) }

        val firstPage = paginatedFrom(0, lister = masterLister)

        assertEquals(
            PaginatedList(listIndicesOf(0, PAGINATION_FACTOR), true),
            firstPage,
        )

        val secondPage = paginatedFrom(1, lister = masterLister)

        assertEquals(
            PaginatedList(listIndicesOf(PAGINATION_FACTOR, PAGINATION_FACTOR * 2), false),
            secondPage,
        )
    }

    @Test
    fun `one more than two times PaginationFactor`() {
        val masterLister = { listIndicesOf(0, PAGINATION_FACTOR * 2 + 1) }

        val firstPage = paginatedFrom(0, lister = masterLister)

        assertEquals(
            PaginatedList(listIndicesOf(0, PAGINATION_FACTOR), true),
            firstPage,
        )

        val secondPage = paginatedFrom(1, lister = masterLister)

        assertEquals(
            PaginatedList(listIndicesOf(PAGINATION_FACTOR, PAGINATION_FACTOR * 2), true),
            secondPage,
        )

        val thirdPage = paginatedFrom(2, lister = masterLister)

        assertEquals(
            PaginatedList(listIndicesOf(PAGINATION_FACTOR * 2, PAGINATION_FACTOR * 2 + 1), false),
            thirdPage,
        )
    }

    private fun listIndicesOf(
        first: Int,
        last: Int,
    ): List<Int> {
        return (first until last).toList()
    }
}
