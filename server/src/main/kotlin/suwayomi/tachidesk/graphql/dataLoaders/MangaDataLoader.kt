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
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.future

class MangaDataLoader : KotlinDataLoader<Int, MangaType?> {
    override val dataLoaderName = "MangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, MangaType?> = DataLoaderFactory.newDataLoader { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val manga = MangaTable.select { MangaTable.id inList ids }
                    .map { MangaType(it) }
                    .associateBy { it.id }
                ids.map { manga[it] }
            }
        }
    }
}

class MangaForCategoryDataLoader : KotlinDataLoader<Int, MangaNodeList> {
    override val dataLoaderName = "MangaForCategoryDataLoader"
    override fun getDataLoader(): DataLoader<Int, MangaNodeList> = DataLoaderFactory.newDataLoader<Int, MangaNodeList> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val itemsByRef = if (ids.contains(0)) {
                    MangaTable
                        .leftJoin(CategoryMangaTable)
                        .select { MangaTable.inLibrary eq true }
                        .andWhere { CategoryMangaTable.manga.isNull() }
                        .map { MangaType(it) }
                        .let {
                            mapOf(0 to it)
                        }
                } else {
                    emptyMap()
                } + CategoryMangaTable.innerJoin(MangaTable)
                    .select { CategoryMangaTable.category inList ids }
                    .map { Pair(it[CategoryMangaTable.category].value, MangaType(it)) }
                    .groupBy { it.first }
                    .mapValues { it.value.map { pair -> pair.second } }

                ids.map { (itemsByRef[it] ?: emptyList()).toNodeList() }
            }
        }
    }
}
