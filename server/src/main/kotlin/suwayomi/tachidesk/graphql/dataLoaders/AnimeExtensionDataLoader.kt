/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import suwayomi.tachidesk.graphql.types.AnimeExtensionType
import suwayomi.tachidesk.server.JavalinSetup.future

class AnimeExtensionDataLoader : KotlinDataLoader<String, AnimeExtensionType?> {
    override val dataLoaderName = "AnimeExtensionDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, AnimeExtensionType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val extensions =
                        AnimeExtensionTable
                            .selectAll()
                            .where { AnimeExtensionTable.pkgName inList ids }
                            .map { AnimeExtensionType(it) }
                            .associateBy { it.pkgName }
                    ids.map { extensions[it] }
                }
            }
        }
}

class AnimeExtensionForSourceDataLoader : KotlinDataLoader<Long, AnimeExtensionType?> {
    override val dataLoaderName = "AnimeExtensionForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, AnimeExtensionType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val extensions =
                        AnimeExtensionTable
                            .innerJoin(AnimeSourceTable)
                            .selectAll()
                            .where { AnimeSourceTable.id inList ids }
                            .toList()
                            .map { Triple(it[AnimeSourceTable.id].value, it[AnimeExtensionTable.pkgName], it) }
                            .let { triples ->
                                val sources =
                                    buildMap {
                                        triples.forEach {
                                            if (!containsKey(it.second)) {
                                                put(it.second, AnimeExtensionType(it.third))
                                            }
                                        }
                                    }
                                triples.associate { it.first to sources[it.second] }
                            }
                    ids.map { extensions[it] }
                }
            }
        }
}
