/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.model.table.GlobalMetaTable
import suwayomi.tachidesk.graphql.types.GlobalMetaItem
import suwayomi.tachidesk.graphql.types.MetaItem
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - In list of keys
 *
 * TODO Mutations
 * - Add/update meta
 * - Delete meta
 *
 */
class MetaQuery {
    fun meta(dataFetchingEnvironment: DataFetchingEnvironment, key: String): CompletableFuture<MetaItem?> {
        return dataFetchingEnvironment.getValueFromDataLoader<String, MetaItem?>("GlobalMetaDataLoader", key)
    }

    fun metas(): List<GlobalMetaItem> {
        val results = transaction {
            GlobalMetaTable.selectAll().toList()
        }

        return results.map { GlobalMetaItem(it) }
    }
}
