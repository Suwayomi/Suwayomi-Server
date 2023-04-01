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
import suwayomi.tachidesk.graphql.types.SourceType
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.JavalinSetup.future

class SourceDataLoader : KotlinDataLoader<Long, SourceType?> {
    override val dataLoaderName = "SourceDataLoader"
    override fun getDataLoader(): DataLoader<Long, SourceType?> = DataLoaderFactory.newDataLoader { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                SourceTable.select { SourceTable.id inList ids }.map {
                    SourceType(it)
                }
            }
        }
    }
}

class SourceForMangaDataLoader : KotlinDataLoader<Int, SourceType?> {
    override val dataLoaderName = "SourceForMangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, SourceType?> = DataLoaderFactory.newDataLoader { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)

                val itemsByRef = MangaTable.innerJoin(SourceTable)
                    .select { MangaTable.id inList ids }
                    .map { Triple(it[MangaTable.id].value, it[MangaTable.sourceReference], it) }
                    .let { triples ->
                        val sources = buildMap {
                            triples.forEach {
                                if (!containsKey(it.second)) {
                                    put(it.second, SourceType(it.third))
                                }
                            }
                        }
                        triples.associate {
                            it.first to sources[it.second]
                        }
                    }

                ids.map { itemsByRef[it] }
            }
        }
    }
}

class SourcesForExtensionDataLoader : KotlinDataLoader<String, List<SourceType>> {
    override val dataLoaderName = "SourcesForExtensionDataLoader"
    override fun getDataLoader(): DataLoader<String, List<SourceType>> = DataLoaderFactory.newDataLoader { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)

                val sourcesByExtensionPkg = SourceTable.innerJoin(ExtensionTable)
                    .select { ExtensionTable.pkgName inList ids }
                    .map { Pair(it[ExtensionTable.pkgName], SourceType(it)) }
                    .groupBy { it.first }
                    .mapValues { it.value.mapNotNull { pair -> pair.second } }

                ids.map { sourcesByExtensionPkg[it] ?: emptyList() }
            }
        }
    }
}
