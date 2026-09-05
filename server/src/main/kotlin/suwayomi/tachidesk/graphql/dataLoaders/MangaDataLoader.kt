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
import org.jetbrains.exposed.v1.core.Slf4jSqlDebugLogger
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.graphql.types.MangaUserType
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser

class MangaDataLoader : KotlinDataLoader<Int, MangaType> {
    override val dataLoaderName = "MangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, MangaType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val manga =
                        MangaTable
                            .selectAll()
                            .where { MangaTable.id inList ids }
                            .map { MangaType(it) }
                            .associateBy { it.id }
                    ids.map { manga[it] }
                }
            }
        }
}

class MangaForCategoryDataLoader : KotlinDataLoader<Int, MangaNodeList> {
    override val dataLoaderName = "MangaForCategoryDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, MangaNodeList> =
        DataLoaderFactory.newDataLoader<Int, MangaNodeList> { ids ->
            future {
                val userId = graphQLContext.getAttribute(JavalinSetup.Attribute.TachideskUser).requireUser()
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val defaultCategoryId = Category.getDefaultCategoryId(userId)!!
                    val itemsByRef =
                        if (ids.contains(defaultCategoryId)) {
                            MangaTable
                                .getWithUserData(userId)
                                .leftJoin(
                                    CategoryMangaTable,
                                    onColumn = { MangaTable.id },
                                    otherColumn = { CategoryMangaTable.manga },
                                    additionalConstraint = { CategoryMangaTable.user eq userId },
                                ).selectAll()
                                .where { MangaUserTable.inLibrary eq true }
                                .andWhere { CategoryMangaTable.manga.isNull() }
                                .map { MangaType(it) }
                                .let {
                                    mapOf(defaultCategoryId to it)
                                }
                        } else {
                            emptyMap()
                        } +
                            CategoryMangaTable
                                .innerJoin(MangaTable)
                                .selectAll()
                                .where { CategoryMangaTable.category inList ids and (CategoryMangaTable.user eq userId) }
                                .map { Pair(it[CategoryMangaTable.category].value, MangaType(it)) }
                                .groupBy { it.first }
                                .mapValues { it.value.map { pair -> pair.second } }

                    ids.map { (itemsByRef[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class MangaForSourceDataLoader : KotlinDataLoader<Long, MangaNodeList> {
    override val dataLoaderName = "MangaForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, MangaNodeList> =
        DataLoaderFactory.newDataLoader<Long, MangaNodeList> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val mangaBySourceId =
                        MangaTable
                            .selectAll()
                            .where { MangaTable.sourceReference inList ids }
                            .map { MangaType(it) }
                            .groupBy { it.sourceId }
                    ids.map { (mangaBySourceId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class MangaUserForMangaDataLoader : KotlinDataLoader<Int, MangaUserType> {
    override val dataLoaderName = "MangaUserForMangaDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, MangaUserType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(JavalinSetup.Attribute.TachideskUser).requireUser()
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val manga =
                        MangaUserTable
                            .selectAll()
                            .where { MangaUserTable.user eq userId and (MangaUserTable.manga inList ids) }
                            .map { MangaUserType(it) }
                            .associateBy { it.mangaId }
                    ids.map { manga[it] }
                }
            }
        }
}
