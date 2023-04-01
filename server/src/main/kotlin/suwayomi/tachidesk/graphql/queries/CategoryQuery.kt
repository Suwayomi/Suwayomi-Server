/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Sort?
 * - Query by name
 * - In ID list
 * - Paged queries
 *
 * TODO Mutations
 * - Name
 * - Order
 * - Default
 */
class CategoryQuery {
    fun category(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<CategoryType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, CategoryType>("CategoryDataLoader", id)
    }

    fun categories(): List<CategoryType> {
        val results = transaction {
            CategoryTable.selectAll().toList()
        }

        return results.map { CategoryType(it) }
    }
}
