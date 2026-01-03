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
import suwayomi.tachidesk.graphql.types.IReaderExtensionType
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import suwayomi.tachidesk.server.JavalinSetup.future

class IReaderExtensionDataLoader : KotlinDataLoader<String, IReaderExtensionType?> {
    override val dataLoaderName = "IReaderExtensionDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, IReaderExtensionType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val extensions =
                        IReaderExtensionTable
                            .selectAll()
                            .where { IReaderExtensionTable.pkgName inList ids }
                            .map { IReaderExtensionType.fromResultRow(it) }
                            .associateBy { it.pkgName }
                    ids.map { extensions[it] }
                }
            }
        }
}

class IReaderExtensionForSourceDataLoader : KotlinDataLoader<Long, IReaderExtensionType?> {
    override val dataLoaderName = "IReaderExtensionForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, IReaderExtensionType?> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val extensions =
                        IReaderExtensionTable
                            .innerJoin(IReaderSourceTable)
                            .selectAll()
                            .where { IReaderSourceTable.id inList ids }
                            .toList()
                            .map { Triple(it[IReaderSourceTable.id].value, it[IReaderExtensionTable.pkgName], it) }
                            .let { triples ->
                                val extensionsByPkg =
                                    buildMap {
                                        triples.forEach {
                                            if (!containsKey(it.second)) {
                                                put(it.second, IReaderExtensionType.fromResultRow(it.third))
                                            }
                                        }
                                    }
                                triples.associate {
                                    it.first to extensionsByPkg[it.second]
                                }
                            }
                    ids.map { extensions[it] }
                }
            }
        }
}
