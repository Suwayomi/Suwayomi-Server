package suwayomi.tachidesk.global.impl

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.GlobalMetaTable

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object GlobalMeta {
    fun modifyMeta(
        key: String,
        value: String,
    ) {
        modifyMetas(mapOf(key to value))
    }

    fun modifyMetas(meta: Map<String, String>) {
        transaction {
            val dbMetaMap =
                GlobalMetaTable
                    .selectAll()
                    .where { GlobalMetaTable.key inList meta.keys }
                    .associateBy { it[GlobalMetaTable.key] }
            val (existingMeta, newMeta) = meta.toList().partition { (key) -> key in dbMetaMap.keys }

            if (existingMeta.isNotEmpty()) {
                BatchUpdateStatement(GlobalMetaTable)
                    .apply {
                        existingMeta.forEach { (key, value) ->
                            addBatch(EntityID(dbMetaMap[key]!![GlobalMetaTable.id].value, GlobalMetaTable))
                            this[GlobalMetaTable.value] = value
                        }
                    }.toExecutable()
                    .execute(this@transaction)
            }

            if (newMeta.isNotEmpty()) {
                GlobalMetaTable.batchInsert(newMeta) { (key, value) ->
                    this[GlobalMetaTable.key] = key
                    this[GlobalMetaTable.value] = value
                }
            }
        }
    }

    fun getMetaMap(): Map<String, String> =
        transaction {
            GlobalMetaTable
                .selectAll()
                .associate { it[GlobalMetaTable.key] to it[GlobalMetaTable.value] }
        }
}
