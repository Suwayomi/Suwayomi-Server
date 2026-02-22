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
import suwayomi.tachidesk.graphql.types.AnimeSourceNodeList
import suwayomi.tachidesk.graphql.types.AnimeSourceNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.AnimeSourceType
import suwayomi.tachidesk.server.JavalinSetup.future

class AnimeSourceDataLoader : KotlinDataLoader<Long, AnimeSourceType?> {
    override val dataLoaderName = "AnimeSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, AnimeSourceType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val sources =
                        AnimeSourceTable
                            .selectAll()
                            .where { AnimeSourceTable.id inList ids }
                            .mapNotNull { AnimeSourceType(it) }
                            .associateBy { it.id }
                    ids.map { sources[it] }
                }
            }
        }
}

class AnimeSourcesForExtensionDataLoader : KotlinDataLoader<String, AnimeSourceNodeList> {
    override val dataLoaderName = "AnimeSourcesForExtensionDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, AnimeSourceNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val sourcesByExtensionPkg =
                        AnimeSourceTable
                            .innerJoin(AnimeExtensionTable)
                            .selectAll()
                            .where { AnimeExtensionTable.pkgName inList ids }
                            .map { Pair(it[AnimeExtensionTable.pkgName], AnimeSourceType(it)) }
                            .groupBy { it.first }
                            .mapValues { it.value.mapNotNull { pair -> pair.second } }

                    ids.map { (sourcesByExtensionPkg[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}
