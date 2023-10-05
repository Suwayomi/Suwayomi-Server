package suwayomi.tachidesk.global.impl

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
        transaction {
            val meta =
                transaction {
                    GlobalMetaTable.select { GlobalMetaTable.key eq key }
                }.firstOrNull()

            if (meta == null) {
                GlobalMetaTable.insert {
                    it[GlobalMetaTable.key] = key
                    it[GlobalMetaTable.value] = value
                }
            } else {
                GlobalMetaTable.update({ GlobalMetaTable.key eq key }) {
                    it[GlobalMetaTable.value] = value
                }
            }
        }
    }

    fun getMetaMap(): Map<String, String> {
        return transaction {
            GlobalMetaTable.selectAll()
                .associate { it[GlobalMetaTable.key] to it[GlobalMetaTable.value] }
        }
    }
}
