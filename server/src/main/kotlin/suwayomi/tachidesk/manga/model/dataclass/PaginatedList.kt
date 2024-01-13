package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlin.math.min

open class PaginatedList<T>(
    val page: List<T>,
    val hasNextPage: Boolean,
)

const val PAGINATION_FACTOR = 50

fun <T> paginatedFrom(
    pageNum: Int,
    paginationFactor: Int = PAGINATION_FACTOR,
    lister: () -> List<T>,
): PaginatedList<T> {
    val list = lister()
    val lastIndex = list.size - 1

    val lowerIndex = pageNum * paginationFactor
    val higherIndex = (pageNum + 1) * paginationFactor - 1

    if (lowerIndex > lastIndex) {
        return PaginatedList(emptyList(), false)
    }

    val sliced = list.slice(lowerIndex..min(lastIndex, higherIndex))

    return PaginatedList(
        sliced,
        higherIndex < lastIndex,
    )
}
