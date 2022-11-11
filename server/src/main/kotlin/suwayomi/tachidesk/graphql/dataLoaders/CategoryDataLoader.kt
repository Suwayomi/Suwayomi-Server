/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.util.concurrent.CompletableFuture

class CategoryDataLoader : KotlinDataLoader<Int, CategoryType> {
    override val dataLoaderName = "CategoryDataLoader"
    override fun getDataLoader(): DataLoader<Int, CategoryType> = DataLoaderFactory.newDataLoader<Int, CategoryType> { ids ->
        CompletableFuture.supplyAsync {
            transaction {
                addLogger(StdOutSqlLogger)
                CategoryTable.select { CategoryTable.id inList ids }
                    .map { CategoryType(it) }
            }
        }
    }
}

class CategoriesForMangaDataLoader : KotlinDataLoader<Int, List<CategoryType>> {
    override val dataLoaderName = "CategoriesForMangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, List<CategoryType>> = DataLoaderFactory.newDataLoader<Int, List<CategoryType>> { ids ->
        CompletableFuture.supplyAsync {
            transaction {
                addLogger(StdOutSqlLogger)
                val itemsByRef = CategoryMangaTable.innerJoin(CategoryTable).select { CategoryMangaTable.manga inList ids }
                    .map { Pair(it[CategoryMangaTable.manga].value, CategoryType(it)) }
                    .groupBy { it.first }
                    .mapValues { it.value.map { pair -> pair.second } }
                ids.map { itemsByRef[it] ?: emptyList() }
            }
        }
    }
}
