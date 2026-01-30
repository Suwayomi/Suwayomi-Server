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
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.graphql.types.AnimeNodeList
import suwayomi.tachidesk.graphql.types.AnimeNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.AnimeType
import suwayomi.tachidesk.server.JavalinSetup.future

class AnimeDataLoader : KotlinDataLoader<Int, AnimeType?> {
    override val dataLoaderName = "AnimeDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, AnimeType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val anime =
                        AnimeTable
                            .selectAll()
                            .where { AnimeTable.id inList ids }
                            .map { AnimeType(it) }
                            .associateBy { it.id }
                    ids.map { anime[it] }
                }
            }
        }
}

class AnimeForSourceDataLoader : KotlinDataLoader<Long, AnimeNodeList> {
    override val dataLoaderName = "AnimeForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, AnimeNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val animeBySourceId =
                        AnimeTable
                            .selectAll()
                            .where { AnimeTable.sourceReference inList ids }
                            .map { AnimeType(it) }
                            .groupBy { it.sourceId }
                    ids.map { (animeBySourceId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}
