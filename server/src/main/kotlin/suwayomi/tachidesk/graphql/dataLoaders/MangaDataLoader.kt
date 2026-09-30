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
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.MangaNodeList
import suwayomi.tachidesk.graphql.types.MangaNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.MangaType
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
                val userId = graphQLContext.getAttribute(JavalinSetup.Attribute.TachideskUser).requireUser()
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val manga =
                        MangaTable
                            .getWithUserData(userId)
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
                                .innerJoin(MangaTable.getWithUserData(userId))
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

/** The manga totals of [MangaForCategoryDataLoader], counted in SQL. */
class MangaCountForCategoryDataLoader : KotlinDataLoader<Int, Int> {
    override val dataLoaderName = "MangaCountForCategoryDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Int> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(JavalinSetup.Attribute.TachideskUser).requireUser()
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val defaultCategoryId = Category.getDefaultCategoryId(userId)!!
                    val count = MangaTable.id.count()
                    val countByRef =
                        if (ids.contains(defaultCategoryId)) {
                            MangaTable
                                .getWithUserData(userId)
                                .leftJoin(
                                    CategoryMangaTable,
                                    onColumn = { MangaTable.id },
                                    otherColumn = { CategoryMangaTable.manga },
                                    additionalConstraint = { CategoryMangaTable.user eq userId },
                                ).select(count)
                                .where { MangaUserTable.inLibrary eq true }
                                .andWhere { CategoryMangaTable.manga.isNull() }
                                .let { mapOf(defaultCategoryId to it.single()[count].toInt()) }
                        } else {
                            emptyMap()
                        } +
                            CategoryMangaTable
                                .innerJoin(MangaTable.getWithUserData(userId))
                                .select(CategoryMangaTable.category, count)
                                .where { CategoryMangaTable.category inList ids and (CategoryMangaTable.user eq userId) }
                                .groupBy(CategoryMangaTable.category)
                                .associate { it[CategoryMangaTable.category].value to it[count].toInt() }

                    ids.map { countByRef[it] ?: 0 }
                }
            }
        }
}

class MangaForSourceDataLoader : KotlinDataLoader<Long, MangaNodeList> {
    override val dataLoaderName = "MangaForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, MangaNodeList> =
        DataLoaderFactory.newDataLoader<Long, MangaNodeList> { ids ->
            future {
                val userId = graphQLContext.getAttribute(JavalinSetup.Attribute.TachideskUser).requireUser()
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val mangaBySourceId =
                        MangaTable
                            .getWithUserData(userId)
                            .selectAll()
                            .where { MangaTable.sourceReference inList ids }
                            .map { MangaType(it) }
                            .groupBy { it.sourceId }
                    ids.map { (mangaBySourceId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

/** The manga totals of [MangaForSourceDataLoader], counted in SQL. */
class MangaCountForSourceDataLoader : KotlinDataLoader<Long, Int> {
    override val dataLoaderName = "MangaCountForSourceDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Long, Int> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val count = MangaTable.id.count()
                    val countBySourceId =
                        MangaTable
                            .select(MangaTable.sourceReference, count)
                            .where { MangaTable.sourceReference inList ids }
                            .groupBy(MangaTable.sourceReference)
                            .associate { it[MangaTable.sourceReference] to it[count].toInt() }
                    ids.map { countBySourceId[it] ?: 0 }
                }
            }
        }
}
