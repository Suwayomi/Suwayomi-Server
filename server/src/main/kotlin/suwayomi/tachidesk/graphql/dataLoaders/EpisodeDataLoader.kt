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
import suwayomi.tachidesk.anime.model.table.EpisodeTable
import suwayomi.tachidesk.graphql.types.EpisodeNodeList
import suwayomi.tachidesk.graphql.types.EpisodeNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.EpisodeType
import suwayomi.tachidesk.server.JavalinSetup.future

class EpisodeDataLoader : KotlinDataLoader<Int, EpisodeType?> {
    override val dataLoaderName = "EpisodeDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, EpisodeType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val episodes =
                        EpisodeTable
                            .selectAll()
                            .where { EpisodeTable.id inList ids }
                            .map { EpisodeType(it) }
                            .associateBy { it.id }
                    ids.map { episodes[it] }
                }
            }
        }
}

class EpisodesForAnimeDataLoader : KotlinDataLoader<Int, EpisodeNodeList> {
    override val dataLoaderName = "EpisodesForAnimeDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, EpisodeNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val episodesByAnimeId =
                        EpisodeTable
                            .selectAll()
                            .where { EpisodeTable.anime inList ids }
                            .map { EpisodeType(it) }
                            .groupBy { it.animeId }
                    ids.map { (episodesByAnimeId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}
