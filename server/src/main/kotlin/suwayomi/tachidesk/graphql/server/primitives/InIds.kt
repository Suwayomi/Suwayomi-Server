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

/**
 * `column IN (1, 2, 3)` with the ids written into the SQL instead of bound one by one.
 *
 * A DataLoader batch can hold hundreds of ids. With `inList`, H2 compares every row the index
 * returns against each bound parameter, so the cost grows with rows × ids: a library of 360
 * series spent most of each chapter loader in that check. With literal values H2 checks a
 * constant set instead. The values are numbers, so writing them into the SQL is safe.
 */
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

private fun inIds(
    column: Expression<*>,
    ids: Collection<Number>,
): Op<Boolean> = if (ids.isEmpty()) Op.FALSE else InIdsOp(column, ids.distinct())

@JvmName("inIntEntityIds")
infix fun Column<EntityID<Int>>.inIds(ids: Collection<Int>): Op<Boolean> = inIds(this, ids)

@JvmName("inLongEntityIds")
infix fun Column<EntityID<Long>>.inIds(ids: Collection<Long>): Op<Boolean> = inIds(this, ids)

@JvmName("inIntIds")
infix fun Column<Int>.inIds(ids: Collection<Int>): Op<Boolean> = inIds(this, ids)

@JvmName("inLongIds")
infix fun Column<Long>.inIds(ids: Collection<Long>): Op<Boolean> = inIds(this, ids)
