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
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import suwayomi.tachidesk.graphql.server.primitives.inIds
import suwayomi.tachidesk.graphql.types.SourceNodeList
import suwayomi.tachidesk.graphql.types.SourceNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.SourceType
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.JavalinSetup.future

class SourceDataLoader : KotlinDataLoader<Long, SourceType> {
    override val dataLoaderName = "SourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, SourceType> =
        newBatchedDataLoader { ids ->
            future {
                loggedSuspendTransaction {
                    val source =
                        SourceTable
                            .selectAll()
                            .where { SourceTable.id inIds ids }
                            .mapNotNull { SourceType(it) }
                            .associateBy { it.id }
                    ids.map { source[it] }.asDataLoaderResults()
                }
            }
        }
}

class SourcesForExtensionDataLoader : KotlinDataLoader<String, SourceNodeList> {
    override val dataLoaderName = "SourcesForExtensionDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, SourceNodeList> =
        newBatchedDataLoader { ids ->
            future {
                loggedSuspendTransaction {
                    val sourcesByExtensionPkg =
                        SourceTable
                            .innerJoin(ExtensionTable)
                            .selectAll()
                            .where { ExtensionTable.pkgName inList ids }
                            .map { Pair(it[ExtensionTable.pkgName], SourceType(it)) }
                            .groupBy { it.first }
                            .mapValues { it.value.mapNotNull { pair -> pair.second } }

                    ids.map { (sourcesByExtensionPkg[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}
