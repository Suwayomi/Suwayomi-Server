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
 * For each key of [keys], the first row of this column set whose [partitionBy] is that key, by
 * [orderBy] among the rows matching [where], ties going to the lowest [idColumn]: "the last read
 * chapter of each manga", "the alphabetically last manga of each category", ...
 *
 * [keys] selects one column, the key, from a table that isn't part of this column set (the manga
 * table for chapters per manga): each key's first row is a correlated `ORDER BY ... LIMIT 1`
 * subquery on it. With an index that starts with [partitionBy] and follows [orderBy], such as
 * `chapter (manga, fetched_at DESC, source_order DESC)`, the database reads the first entry of
 * each key and stops, instead of ranking every row of every key. Without such an index it still
 * only sorts one key's rows at a time.
 *
 * The first rows are found in [rankedOn], this column set by default. When [orderBy] and [where]
 * only read some of its tables, rank on those: a chapter's left join to its user data keeps H2
 * from reading the chapter index in order, about 4 times slower.
 *
 * It takes a single query, and the rows hold every column of this column set, a join included.
 * The first ids lead the `FROM` and this column set is joined to them, never the other way round:
 * H2 can't index a derived table, so with this column set first (a left join pins it there) it
 * would scan the whole table and re-scan the first ids for each row.
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
    return firstRows
        .join(this, JoinType.INNER, onColumn = firstRows[firstId], otherColumn = idColumn)
        .select(columns)
        .toList()
}
