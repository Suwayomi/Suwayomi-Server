/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.UpdatesType
import suwayomi.tachidesk.manga.model.dataclass.PaginationFactor
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable

/**
 * TODO Queries
 *
 * TODO Mutations
 * - Update the library
 * - Update a category
 * - Reset updater
 *
 */
class UpdatesQuery {
    data class UpdatesQueryInput(
        val page: Int
    )

    fun updates(input: UpdatesQueryInput): List<UpdatesType> {
        val results = transaction {
            ChapterTable.innerJoin(MangaTable)
                .select { (MangaTable.inLibrary eq true) and (ChapterTable.fetchedAt greater MangaTable.inLibraryAt) }
                .orderBy(ChapterTable.fetchedAt to SortOrder.DESC)
                .limit(PaginationFactor, (input.page - 1L) * PaginationFactor)
                .map {
                    UpdatesType(it)
                }
        }

        return results
    }
}
