package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.util.Objects
import kotlin.math.min

open class PaginatedList<T>(
    val page: List<T>,
    val hasNextPage: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaginatedList<T>) return false
        return page == other.page && hasNextPage == other.hasNextPage
    }

    override fun hashCode(): Int = Objects.hash(page, hasNextPage)
}

const val PAGINATION_FACTOR = 50

fun <T> paginatedFrom(
    pageNum: Int,
    paginationFactor: Int = PAGINATION_FACTOR,
    lister: () -> List<T>,
): PaginatedList<T> {
    val list = lister()
    val lastIndex = list.size - 1

    // a negative page number used to produce a negative slice range and throw — clamp it to
    // the first page instead, the same lenient behavior a client expects from ?page=-1.
    // Index math runs in Long: pageNum * paginationFactor can overflow Int for absurd page
    // numbers, which would wrap around to a negative index and throw again
    val safePageNum = pageNum.coerceAtLeast(0)
    val lowerIndex = safePageNum.toLong() * paginationFactor
    val higherIndex = lowerIndex + paginationFactor - 1

    if (lowerIndex > lastIndex) {
        return PaginatedList(emptyList(), false)
    }

    val sliced = list.slice(lowerIndex.toInt()..min(lastIndex.toLong(), higherIndex).toInt())

    return PaginatedList(
        sliced,
        higherIndex < lastIndex,
    )
}
