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
import suwayomi.tachidesk.graphql.types.IReaderSourceNodeList
import suwayomi.tachidesk.graphql.types.IReaderSourceNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.IReaderSourceType
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import suwayomi.tachidesk.server.JavalinSetup.future

class IReaderSourceDataLoader : KotlinDataLoader<Long, IReaderSourceType?> {
    override val dataLoaderName = "IReaderSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, IReaderSourceType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val sources =
                        IReaderSourceTable
                            .innerJoin(IReaderExtensionTable)
                            .selectAll()
                            .where { IReaderSourceTable.id inList ids }
                            .mapNotNull { IReaderSourceType.fromResultRow(it) }
                            .associateBy { it.id.toLong() }
                    ids.map { sources[it] }
                }
            }
        }
}

class IReaderSourcesForExtensionDataLoader : KotlinDataLoader<String, IReaderSourceNodeList> {
    override val dataLoaderName = "IReaderSourcesForExtensionDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, IReaderSourceNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)

                    val sourcesByExtensionPkg =
                        IReaderSourceTable
                            .innerJoin(IReaderExtensionTable)
                            .selectAll()
                            .where { IReaderExtensionTable.pkgName inList ids }
                            .mapNotNull { row ->
                                IReaderSourceType.fromResultRow(row)?.let { source ->
                                    Pair(row[IReaderExtensionTable.pkgName], source)
                                }
                            }
                            .groupBy { it.first }
                            .mapValues { it.value.map { pair -> pair.second } }

                    ids.map { (sourcesByExtensionPkg[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}
