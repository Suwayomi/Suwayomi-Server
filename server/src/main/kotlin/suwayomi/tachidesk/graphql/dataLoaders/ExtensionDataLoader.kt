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
import suwayomi.tachidesk.graphql.types.ExtensionType
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.JavalinSetup.future

class ExtensionDataLoader : KotlinDataLoader<String, ExtensionType?> {
    override val dataLoaderName = "ExtensionDataLoader"

    override fun getDataLoader(): DataLoader<String, ExtensionType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val extensions =
                        ExtensionTable.select { ExtensionTable.pkgName inList ids }
                            .map { ExtensionType(it) }
                            .associateBy { it.pkgName }
                    ids.map { extensions[it] }
                }
            }
        }
}

class ExtensionForSourceDataLoader : KotlinDataLoader<Long, ExtensionType?> {
    override val dataLoaderName = "ExtensionForSourceDataLoader"

    override fun getDataLoader(): DataLoader<Long, ExtensionType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val extensions =
                        ExtensionTable.innerJoin(SourceTable)
                            .select { SourceTable.id inList ids }
                            .toList()
                            .map { Triple(it[SourceTable.id].value, it[ExtensionTable.pkgName], it) }
                            .let { triples ->
                                val sources =
                                    buildMap {
                                        triples.forEach {
                                            if (!containsKey(it.second)) {
                                                put(it.second, ExtensionType(it.third))
                                            }
                                        }
                                    }
                                triples.associate {
                                    it.first to sources[it.second]
                                }
                            }
                    ids.map { extensions[it] }
                }
            }
        }
}
