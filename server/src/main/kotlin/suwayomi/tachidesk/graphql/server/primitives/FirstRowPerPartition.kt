package suwayomi.tachidesk.graphql.server.primitives

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.jdbc.select

/**
 * The first row of each [partitionBy] value by [orderBy], among the rows matching [where], ties
 * going to the lowest [idColumn]: "the last read chapter of each manga", "the newest manga of each
 * source", ...
 *
 * Ranked in SQL (`ROW_NUMBER() OVER (PARTITION BY ...)`) and joined back on the first rank, so it
 * takes a single query and a single row per partition leaves the database, however many rows each
 * partition has. The rows hold every column of this column set, a join included.
 */
fun <ID : Any> ColumnSet.firstRowPerPartition(
    partitionBy: Expression<*>,
    idColumn: Column<EntityID<ID>>,
    orderBy: List<Pair<Expression<*>, SortOrder>>,
    where: Op<Boolean>,
): List<ResultRow> {
    val selectedColumns = columns
    val rank =
        rowNumber()
            .over()
            .partitionBy(partitionBy)
            .orderBy(*(orderBy + (idColumn to SortOrder.ASC)).toTypedArray())
            .alias("partition_rank")
    val ranked =
        select(idColumn, rank)
            .where(where)
            .alias("ranked_rows")
    return join(
        ranked,
        JoinType.INNER,
        additionalConstraint = { (idColumn eq ranked[idColumn]) and (ranked[rank] eq 1L) },
    ).select(selectedColumns)
        .toList()
}
