package suwayomi.tachidesk.graphql.server.primitives

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.EqOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select

/**
 * For each key of [keys], the first row whose [partitionBy] is that key by [orderBy], ties going to the lowest
 * [idColumn]. Each key is an `ORDER BY ... LIMIT 1` subquery, so an index on [partitionBy] then [orderBy] reads a
 * single entry per key. Rank on fewer tables with [rankedOn] when possible: a left join keeps H2 off the index.
 */
fun <ID : Any> ColumnSet.firstRowPerPartition(
    keys: Query,
    partitionBy: Expression<*>,
    idColumn: Column<EntityID<ID>>,
    orderBy: List<Pair<Expression<*>, SortOrder>>,
    where: Op<Boolean>? = null,
    rankedOn: ColumnSet = this,
): List<ResultRow> {
    val key = keys.set.fields.single()
    val inPartition = EqOp(partitionBy, key)
    val firstOfKey =
        rankedOn
            .select(idColumn)
            .where(if (where == null) inPartition else inPartition and where)
            .orderBy(*(orderBy + (idColumn to SortOrder.ASC)).toTypedArray())
            .limit(1)
    val firstId = wrapAsExpression<EntityID<ID>>(firstOfKey).alias("first_id")
    val firstRows = keys.copy().adjustSelect { select(firstId) }.alias("first_rows")
    // The first ids must lead the FROM: H2 can't index a derived table
    return firstRows
        .join(this, JoinType.INNER, onColumn = firstRows[firstId], otherColumn = idColumn)
        .select(columns)
        .toList()
}
