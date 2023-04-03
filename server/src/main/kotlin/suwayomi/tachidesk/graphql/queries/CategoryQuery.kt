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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.CategoryNodeList
import suwayomi.tachidesk.graphql.types.CategoryNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Paged queries
 *
 * TODO Mutations
 * - Name
 * - Order
 * - Default
 * - Create
 * - Delete
 * - Add/update meta
 * - Delete meta
 */
class CategoryQuery {
    fun category(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<CategoryType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, CategoryType>("CategoryDataLoader", id)
    }

    enum class CategorySort {
        ID,
        NAME,
        ORDER
    }

    data class CategoriesQueryInput(
        val sort: CategorySort? = null,
        val sortOrder: SortOrder? = null,
        val ids: List<Int>? = null,
        val query: String? = null
    )

    fun categories(input: CategoriesQueryInput? = null): CategoryNodeList {
        val results = transaction {
            val res = CategoryTable.selectAll()

            if (input != null) {
                if (input.ids != null) {
                    res.andWhere { CategoryTable.id inList input.ids }
                }
                if (!input.query.isNullOrEmpty()) {
                    res.andWhere { CategoryTable.name like input.query }
                }
                val orderBy = when (input.sort) {
                    CategorySort.ID -> CategoryTable.id
                    CategorySort.NAME -> CategoryTable.name
                    CategorySort.ORDER, null -> CategoryTable.order
                }
                res.orderBy(orderBy, order = input.sortOrder ?: SortOrder.ASC)
            } else {
                res.orderBy(CategoryTable.order)
            }

            res.toList()
        }

        return results.map { CategoryType(it) }.toNodeList()
    }
}
