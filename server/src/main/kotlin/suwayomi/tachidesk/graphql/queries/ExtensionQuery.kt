/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import eu.kanade.tachiyomi.source.local.LocalSource
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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
import suwayomi.tachidesk.graphql.server.primitives.OrderBy
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.server.primitives.QueryResults
import suwayomi.tachidesk.graphql.server.primitives.applyBeforeAfter
import suwayomi.tachidesk.graphql.server.primitives.greaterNotUnique
import suwayomi.tachidesk.graphql.server.primitives.lessNotUnique
import suwayomi.tachidesk.graphql.server.primitives.maybeSwap
import suwayomi.tachidesk.graphql.types.ExtensionNodeList
import suwayomi.tachidesk.graphql.types.ExtensionType
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import java.util.concurrent.CompletableFuture

class ExtensionQuery {
    fun extension(
        dataFetchingEnvironment: DataFetchingEnvironment,
        pkgName: String,
    ): CompletableFuture<ExtensionType> {
        return dataFetchingEnvironment.getValueFromDataLoader("ExtensionDataLoader", pkgName)
    }

    enum class ExtensionOrderBy(override val column: Column<out Comparable<*>>) : OrderBy<ExtensionType> {
        PKG_NAME(ExtensionTable.pkgName),
        NAME(ExtensionTable.name),
        APK_NAME(ExtensionTable.apkName),
        ;

        override fun greater(cursor: Cursor): Op<Boolean> {
            return when (this) {
                PKG_NAME -> ExtensionTable.pkgName greater cursor.value
                NAME -> greaterNotUnique(ExtensionTable.name, ExtensionTable.pkgName, cursor, String::toString)
                APK_NAME -> greaterNotUnique(ExtensionTable.apkName, ExtensionTable.pkgName, cursor, String::toString)
            }
        }

        override fun less(cursor: Cursor): Op<Boolean> {
            return when (this) {
                PKG_NAME -> ExtensionTable.pkgName less cursor.value
                NAME -> lessNotUnique(ExtensionTable.name, ExtensionTable.pkgName, cursor, String::toString)
                APK_NAME -> lessNotUnique(ExtensionTable.apkName, ExtensionTable.pkgName, cursor, String::toString)
            }
        }

        override fun asCursor(type: ExtensionType): Cursor {
            val value =
                when (this) {
                    PKG_NAME -> type.pkgName
                    NAME -> type.pkgName + "\\-" + type.name
                    APK_NAME -> type.pkgName + "\\-" + type.apkName
                }
            return Cursor(value)
        }
    }

    data class ExtensionCondition(
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
            opAnd.eq(apkName, ExtensionTable.apkName)
            opAnd.eq(iconUrl, ExtensionTable.iconUrl)
            opAnd.eq(name, ExtensionTable.name)
            opAnd.eq(versionName, ExtensionTable.versionName)
            opAnd.eq(versionCode, ExtensionTable.versionCode)
            opAnd.eq(lang, ExtensionTable.lang)
            opAnd.eq(isNsfw, ExtensionTable.isNsfw)
            opAnd.eq(isInstalled, ExtensionTable.isInstalled)
            opAnd.eq(hasUpdate, ExtensionTable.hasUpdate)
            opAnd.eq(isObsolete, ExtensionTable.isObsolete)

            return opAnd.op
        }
    }

    data class ExtensionFilter(
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
        override val and: List<ExtensionFilter>? = null,
        override val or: List<ExtensionFilter>? = null,
        override val not: ExtensionFilter? = null,
    ) : Filter<ExtensionFilter> {
        override fun getOpList(): List<Op<Boolean>> {
            return listOfNotNull(
                andFilterWithCompareString(ExtensionTable.apkName, apkName),
                andFilterWithCompareString(ExtensionTable.iconUrl, iconUrl),
                andFilterWithCompareString(ExtensionTable.name, name),
                andFilterWithCompareString(ExtensionTable.pkgName, pkgName),
                andFilterWithCompareString(ExtensionTable.versionName, versionName),
                andFilterWithCompare(ExtensionTable.versionCode, versionCode),
                andFilterWithCompareString(ExtensionTable.lang, lang),
                andFilterWithCompare(ExtensionTable.isNsfw, isNsfw),
                andFilterWithCompare(ExtensionTable.isInstalled, isInstalled),
                andFilterWithCompare(ExtensionTable.hasUpdate, hasUpdate),
                andFilterWithCompare(ExtensionTable.isObsolete, isObsolete),
            )
        }
    }

    fun extensions(
        condition: ExtensionCondition? = null,
        filter: ExtensionFilter? = null,
        orderBy: ExtensionOrderBy? = null,
        orderByType: SortOrder? = null,
        before: Cursor? = null,
        after: Cursor? = null,
        first: Int? = null,
        last: Int? = null,
        offset: Int? = null,
    ): ExtensionNodeList {
        val queryResults =
            transaction {
                val res = ExtensionTable.selectAll()

                res.adjustWhere { ExtensionTable.name neq LocalSource.EXTENSION_NAME }

                res.applyOps(condition, filter)

                if (orderBy != null || (last != null || before != null)) {
                    val orderByColumn = orderBy?.column ?: ExtensionTable.pkgName
                    val orderType = orderByType.maybeSwap(last ?: before)

                    if (orderBy == ExtensionOrderBy.PKG_NAME || orderBy == null) {
                        res.orderBy(orderByColumn to orderType)
                    } else {
                        res.orderBy(
                            orderByColumn to orderType,
                            ExtensionTable.pkgName to SortOrder.ASC,
                        )
                    }
                }

                val total = res.count()
                val firstResult = res.firstOrNull()?.get(ExtensionTable.pkgName)
                val lastResult = res.lastOrNull()?.get(ExtensionTable.pkgName)

                res.applyBeforeAfter(
                    before = before,
                    after = after,
                    orderBy = orderBy ?: ExtensionOrderBy.PKG_NAME,
                    orderByType = orderByType,
                )

                if (first != null) {
                    res.limit(first, offset?.toLong() ?: 0)
                } else if (last != null) {
                    res.limit(last)
                }

                QueryResults(total, firstResult, lastResult, res.toList())
            }

        val getAsCursor: (ExtensionType) -> Cursor = (orderBy ?: ExtensionOrderBy.PKG_NAME)::asCursor

        val resultsAsType = queryResults.results.map { ExtensionType(it) }

        return ExtensionNodeList(
            resultsAsType,
            if (resultsAsType.isEmpty()) {
                emptyList()
            } else {
                listOfNotNull(
                    resultsAsType.firstOrNull()?.let {
                        ExtensionNodeList.ExtensionEdge(
                            getAsCursor(it),
                            it,
                        )
                    },
                    resultsAsType.lastOrNull()?.let {
                        ExtensionNodeList.ExtensionEdge(
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
