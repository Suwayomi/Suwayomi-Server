/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.queries.filter.BooleanFilter
import suwayomi.tachidesk.graphql.queries.filter.Filter
import suwayomi.tachidesk.graphql.queries.filter.HasGetOp
import suwayomi.tachidesk.graphql.queries.filter.IntFilter
import suwayomi.tachidesk.graphql.queries.filter.OpAnd
import suwayomi.tachidesk.graphql.queries.filter.StringFilter
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompare
import suwayomi.tachidesk.graphql.queries.filter.andFilterWithCompareString
import suwayomi.tachidesk.graphql.queries.filter.applyOps
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Order
import suwayomi.tachidesk.graphql.server.primitives.OrderBy
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.server.primitives.applyBeforeAfter
import suwayomi.tachidesk.graphql.server.primitives.greaterNotUnique
import suwayomi.tachidesk.graphql.server.primitives.lessNotUnique
import suwayomi.tachidesk.graphql.server.primitives.maybeSwap
import suwayomi.tachidesk.graphql.types.AnimeExtensionNodeList
import suwayomi.tachidesk.graphql.types.AnimeExtensionType
import java.util.concurrent.CompletableFuture

class AnimeExtensionQuery {
    @RequireAuth
    fun animeExtension(
        dataFetchingEnvironment: DataFetchingEnvironment,
        pkgName: String,
    ): CompletableFuture<AnimeExtensionType?> =
        dataFetchingEnvironment.getValueFromDataLoader("AnimeExtensionDataLoader", pkgName)

    enum class AnimeExtensionOrderBy(
        override val column: Column<*>,
    ) : OrderBy<AnimeExtensionType> {
        PKG_NAME(AnimeExtensionTable.pkgName),
        NAME(AnimeExtensionTable.name),
        APK_NAME(AnimeExtensionTable.apkName),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> =
            when (this) {
                PKG_NAME -> AnimeExtensionTable.pkgName greater cursor.value
                NAME -> greaterNotUnique(AnimeExtensionTable.name, AnimeExtensionTable.pkgName, cursor, String::toString)
                APK_NAME -> greaterNotUnique(AnimeExtensionTable.apkName, AnimeExtensionTable.pkgName, cursor, String::toString)
            }

        override fun less(cursor: Cursor): Op<Boolean> =
            when (this) {
                PKG_NAME -> AnimeExtensionTable.pkgName less cursor.value
                NAME -> lessNotUnique(AnimeExtensionTable.name, AnimeExtensionTable.pkgName, cursor, String::toString)
                APK_NAME -> lessNotUnique(AnimeExtensionTable.apkName, AnimeExtensionTable.pkgName, cursor, String::toString)
            }

        override fun asCursor(type: AnimeExtensionType): Cursor {
            val value =
                when (this) {
                    PKG_NAME -> type.pkgName
                    NAME -> type.pkgName + "-" + type.name
                    APK_NAME -> type.pkgName + "-" + type.apkName
                }
            return Cursor(value)
        }
    }

    data class AnimeExtensionOrder(
        override val by: AnimeExtensionOrderBy,
        override val byType: SortOrder? = null,
    ) : Order<AnimeExtensionOrderBy>

    data class AnimeExtensionCondition(
        val repo: String? = null,
        val apkName: String? = null,
        val iconUrl: String? = null,
        val name: String? = null,
        val pkgName: String? = null,
        val versionName: String? = null,
        val versionCode: Int? = null,
        val lang: String? = null,
        val isNsfw: Boolean? = null,
        val isInstalled: Boolean? = null,
        val hasUpdate: Boolean? = null,
        val isObsolete: Boolean? = null,
    ) : HasGetOp {
        override fun getOp(): Op<Boolean>? {
            val opAnd = OpAnd()
            opAnd.eq(repo, AnimeExtensionTable.repo)
            opAnd.eq(apkName, AnimeExtensionTable.apkName)
            opAnd.eq(iconUrl, AnimeExtensionTable.iconUrl)
            opAnd.eq(name, AnimeExtensionTable.name)
            opAnd.eq(versionName, AnimeExtensionTable.versionName)
            opAnd.eq(versionCode, AnimeExtensionTable.versionCode)
            opAnd.eq(lang, AnimeExtensionTable.lang)
            opAnd.eq(isNsfw, AnimeExtensionTable.isNsfw)
            opAnd.eq(isInstalled, AnimeExtensionTable.isInstalled)
            opAnd.eq(hasUpdate, AnimeExtensionTable.hasUpdate)
            opAnd.eq(isObsolete, AnimeExtensionTable.isObsolete)
            return opAnd.op
        }
    }

    data class AnimeExtensionFilter(
        val repo: StringFilter? = null,
        val apkName: StringFilter? = null,
        val iconUrl: StringFilter? = null,
        val name: StringFilter? = null,
        val pkgName: StringFilter? = null,
        val versionName: StringFilter? = null,
        val versionCode: IntFilter? = null,
        val lang: StringFilter? = null,
        val isNsfw: BooleanFilter? = null,
        val isInstalled: BooleanFilter? = null,
        val hasUpdate: BooleanFilter? = null,
        val isObsolete: BooleanFilter? = null,
        override val and: List<AnimeExtensionFilter>? = null,
        override val or: List<AnimeExtensionFilter>? = null,
        override val not: AnimeExtensionFilter? = null,
    ) : Filter<AnimeExtensionFilter> {
        override fun getOpList(): List<Op<Boolean>> =
            listOfNotNull(
                andFilterWithCompareString(AnimeExtensionTable.repo, repo),
                andFilterWithCompareString(AnimeExtensionTable.apkName, apkName),
                andFilterWithCompareString(AnimeExtensionTable.iconUrl, iconUrl),
                andFilterWithCompareString(AnimeExtensionTable.name, name),
                andFilterWithCompareString(AnimeExtensionTable.pkgName, pkgName),
                andFilterWithCompareString(AnimeExtensionTable.versionName, versionName),
                andFilterWithCompare(AnimeExtensionTable.versionCode, versionCode),
                andFilterWithCompareString(AnimeExtensionTable.lang, lang),
                andFilterWithCompare(AnimeExtensionTable.isNsfw, isNsfw),
                andFilterWithCompare(AnimeExtensionTable.isInstalled, isInstalled),
                andFilterWithCompare(AnimeExtensionTable.hasUpdate, hasUpdate),
                andFilterWithCompare(AnimeExtensionTable.isObsolete, isObsolete),
            )
    }

    @RequireAuth
    fun animeExtensions(
        condition: AnimeExtensionCondition? = null,
        filter: AnimeExtensionFilter? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderBy: AnimeExtensionOrderBy? = null,
        @GraphQLDeprecated(
            "Replaced with order",
            replaceWith = ReplaceWith("order"),
        )
        orderByType: SortOrder? = null,
        order: List<AnimeExtensionOrder>? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): AnimeExtensionNodeList {
        val queryResults =
            transaction {
                val res = AnimeExtensionTable.selectAll()

                res.applyOps(condition, filter)

                if (order != null || orderBy != null || (last != null || before != null)) {
                    val baseSort = listOf(AnimeExtensionOrder(AnimeExtensionOrderBy.PKG_NAME, SortOrder.ASC))
                    val deprecatedSort = listOfNotNull(orderBy?.let { AnimeExtensionOrder(orderBy, orderByType) })
                    val actualSort = (order.orEmpty() + deprecatedSort + baseSort)
                    actualSort.forEach { (orderBy, orderByType) ->
                        val orderByColumn = orderBy.column
                        val orderType = orderByType.maybeSwap(last ?: before)

                        res.orderBy(orderByColumn to orderType)
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(AnimeExtensionTable.pkgName)
                val lastResult = res.lastOrNull()?.get(AnimeExtensionTable.pkgName)

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = order?.firstOrNull()?.by ?: AnimeExtensionOrderBy.PKG_NAME,
                    orderByType = order?.firstOrNull()?.byType,
                )

                if (first != null) {
                    res.limit(first).offset(offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList())
            }

        val getAsCursor: (AnimeExtensionType) -> Cursor = (order?.firstOrNull()?.by ?: AnimeExtensionOrderBy.PKG_NAME)::asCursor

        val resultsAsType = queryResults.results.map { AnimeExtensionType(it) }

        return AnimeExtensionNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        AnimeExtensionNodeList.AnimeExtensionEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        AnimeExtensionNodeList.AnimeExtensionEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                )
            },
            pageInfo =
                PageInfo(
                    hasNextPage = queryResults.lastKey != resultsAsType.lastOrNull()?.pkgName,
                    hasPreviousPage = queryResults.firstKey != resultsAsType.firstOrNull()?.pkgName,
                    startCursor = resultsAsType.firstOrNull()?.let { getAsCursor(it) },
                    endCursor = resultsAsType.lastOrNull()?.let { getAsCursor(it) },
                ),
            totalCount = queryResults.total.toInt(),
        )
    }
}
