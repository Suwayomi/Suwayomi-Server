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
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.CategoryType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.server.JavalinSetup.future

class CategoryDataLoader : KotlinDataLoader<Int, CategoryType> {
    override val dataLoaderName = "CategoryDataLoader"
    override fun getDataLoader(): DataLoader<Int, CategoryType> = DataLoaderFactory.newDataLoader<Int, CategoryType> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                CategoryTable.select { CategoryTable.id inList ids }
                    .map { CategoryType(it) }
            }
        }
    }
}

class CategoriesForMangaDataLoader : KotlinDataLoader<Int, List<CategoryType>> {
    override val dataLoaderName = "CategoriesForMangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, List<CategoryType>> = DataLoaderFactory.newDataLoader<Int, List<CategoryType>> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val itemsByRef = CategoryMangaTable.innerJoin(CategoryTable)
                    .select { CategoryMangaTable.manga inList ids }
                    .map { Pair(it[CategoryMangaTable.manga].value, CategoryType(it)) }
                    .groupBy { it.first }
                    .mapValues { it.value.map { pair -> pair.second } }
                ids.map { itemsByRef[it] ?: emptyList() }
            }
        }
    }
}
