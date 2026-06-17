package suwayomi.tachidesk.graphql.queries

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ExtensionStoreType
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import java.util.concurrent.CompletableFuture

class ExtensionStoreQuery {
    @RequireAuth
    fun extensionStore(indexUrl: String): CompletableFuture<ExtensionStoreType?> =
        CompletableFuture.supplyAsync {
            transaction {
                ExtensionStoreTable
                    .selectAll()
                    .where { ExtensionStoreTable.indexUrl eq indexUrl }
                    .firstOrNull()
                    ?.let { ExtensionStoreType(it) }
            }
        }

    @RequireAuth
    fun extensionStores(): List<ExtensionStoreType> =
        transaction {
            ExtensionStoreTable
                .selectAll()
                .toList()
                .map { ExtensionStoreType(it) }
        }
}
