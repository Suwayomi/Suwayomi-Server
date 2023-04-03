/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.queries.util.GreaterOrLessThanLong
import suwayomi.tachidesk.graphql.queries.util.andWhereGreaterOrLessThen
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Query options(optionally query the title, description, or/and)
 *
 * TODO Mutations
 * - Favorite
 * - Unfavorite
 * - Add to category
 * - Remove from category
 * - Check for updates
 * - Download x(all = -1) chapters
 * - Delete read/all downloaded chapters
 * - Add/update meta
 * - Delete meta
 */
class MangaQuery {
    fun manga(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<MangaType?> {
        return dataFetchingEnvironment.getValueFromDataLoader("MangaDataLoader", id)
    }

    enum class MangaSort {
        ID,
        TITLE,
        IN_LIBRARY_AT,
        LAST_FETCHED_AT
    }

    data class MangaQueryInput(
        val ids: List<Int>? = null,
        val categoryIds: List<Int>? = null,
        val sourceIds: List<Long>? = null,
        val inLibrary: Boolean? = null,
        val inLibraryAt: GreaterOrLessThanLong? = null,
        val sort: MangaSort? = null,
        val sortOrder: SortOrder? = null,
        val page: Int? = null,
        val count: Int? = null
    )

    fun mangas(input: MangaQueryInput? = null): MangaNodeList {
        val results = transaction {
            var res = MangaTable.selectAll()

            if (input != null) {
                if (input.categoryIds != null) {
                    res = MangaTable.innerJoin(CategoryMangaTable)
                        .select { CategoryMangaTable.category inList input.categoryIds }
                }
                if (input.ids != null) {
                    res.andWhere { MangaTable.id inList input.ids }
                }
                if (input.sourceIds != null) {
                    res.andWhere { MangaTable.sourceReference inList input.sourceIds }
                }
                if (input.inLibrary != null) {
                    res.andWhere { MangaTable.inLibrary eq input.inLibrary }
                }
                if (input.inLibraryAt != null) {
                    res.andWhereGreaterOrLessThen(
                        column = MangaTable.inLibraryAt,
                        greaterOrLessThan = input.inLibraryAt
                    )
                }
                if (input.sort != null) {
                    val orderBy = when (input.sort) {
                        MangaSort.ID -> MangaTable.id
                        MangaSort.TITLE -> MangaTable.title
                        MangaSort.IN_LIBRARY_AT -> MangaTable.inLibraryAt
                        MangaSort.LAST_FETCHED_AT -> MangaTable.lastFetchedAt
                    }
                    res.orderBy(orderBy, order = input.sortOrder ?: SortOrder.ASC)
                }
                if (input.count != null) {
                    val offset = if (input.page == null) 0 else (input.page * input.count).toLong()
                    res.limit(input.count, offset)
                }
            }

            res.toList()
        }

        return results.map { MangaType(it) }.toNodeList() // todo paged
    }
}
