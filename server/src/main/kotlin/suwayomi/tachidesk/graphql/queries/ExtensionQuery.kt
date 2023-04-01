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
import suwayomi.tachidesk.graphql.types.ExtensionType
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Installed
 * - HasUpdate
 * - Obsolete
 * - IsNsfw
 * - In Pkg name list
 * - Query name
 * - Sort?
 * - Paged Queries
 *
 * TODO Mutations
 * - Install
 * - Update
 * - Uninstall
 * - Check for updates (global mutation?)
 */
class ExtensionQuery {
    fun extension(dataFetchingEnvironment: DataFetchingEnvironment, pkgName: String): CompletableFuture<ExtensionType> {
        return dataFetchingEnvironment.getValueFromDataLoader<String, ExtensionType>("ExtensionDataLoader", pkgName)
    }

    fun extensions(): List<ExtensionType> {
        val results = transaction {
            ExtensionTable.selectAll().toList()
        }

        return results.map { ExtensionType(it) }
    }
}
