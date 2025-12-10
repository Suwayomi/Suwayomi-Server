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
import suwayomi.tachidesk.graphql.types.IReaderNovelNodeList
import suwayomi.tachidesk.graphql.types.IReaderNovelNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.manga.model.table.IReaderNovelTable
import suwayomi.tachidesk.server.JavalinSetup.future

class IReaderNovelDataLoader : KotlinDataLoader<Int, IReaderNovelType?> {
    override val dataLoaderName = "IReaderNovelDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, IReaderNovelType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val novels =
                        IReaderNovelTable
                            .selectAll()
                            .where { IReaderNovelTable.id inList ids }
                            .map { IReaderNovelType(it) }
                            .associateBy { it.id }
                    ids.map { novels[it] }
                }
            }
        }
}

class IReaderNovelForSourceDataLoader : KotlinDataLoader<Long, IReaderNovelNodeList> {
    override val dataLoaderName = "IReaderNovelForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, IReaderNovelNodeList> =
        DataLoaderFactory.newDataLoader<Long, IReaderNovelNodeList> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val novelsBySourceId =
                        IReaderNovelTable
                            .selectAll()
                            .where { IReaderNovelTable.sourceReference inList ids }
                            .map { IReaderNovelType(it) }
                            .groupBy { it.sourceId }
                    ids.map { (novelsBySourceId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}
