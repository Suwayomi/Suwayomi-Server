package suwayomi.tachidesk.graphql.server.primitives

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ComplexExpression
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.or

// Writes the ids into the SQL: H2 checks every row against each bound parameter, but against a literal list as a set.
// Safe since the ids are numbers
private class InIdsOp(
    private val column: Expression<*>,
    private val ids: List<Number>,
) : Op<Boolean>(),
    ComplexExpression {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            +column
            +" IN ("
            +ids.joinToString(", ")
            +")"
        }
    }
}

// a single literal IN list cannot grow unbounded; larger id sets are chunked and OR-ed
private const val IN_IDS_CHUNK_SIZE = 1000

private fun inIds(
    column: Expression<*>,
    ids: Collection<Number>,
): Op<Boolean> {
    val distinctIds = ids.distinct()
    if (distinctIds.isEmpty()) {
        return Op.FALSE
    }
    return distinctIds
        .chunked(IN_IDS_CHUNK_SIZE)
        .map { InIdsOp(column, it) as Op<Boolean> }
        .reduce { acc, op -> acc or op }
}

@JvmName("inIntEntityIds")
infix fun Column<EntityID<Int>>.inIds(ids: Collection<Int>): Op<Boolean> = inIds(this, ids)

@JvmName("inLongEntityIds")
infix fun Column<EntityID<Long>>.inIds(ids: Collection<Long>): Op<Boolean> = inIds(this, ids)

@JvmName("inIntIds")
infix fun Column<Int>.inIds(ids: Collection<Int>): Op<Boolean> = inIds(this, ids)

@JvmName("inLongIds")
infix fun Column<Long>.inIds(ids: Collection<Long>): Op<Boolean> = inIds(this, ids)
