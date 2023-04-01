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
import suwayomi.tachidesk.graphql.types.SourceType
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Filter by languages
 * - Filter by name
 * - Filter by NSFW
 * - In list of ids
 * - Sort?
 *
 * TODO Mutations
 * - Browse with filters
 * - Configure settings
 *
 */
class SourceQuery {
    fun source(dataFetchingEnvironment: DataFetchingEnvironment, id: Long): CompletableFuture<SourceType?> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, SourceType?>("SourceDataLoader", id)
    }

    fun sources(): List<SourceType> {
        val results = transaction {
            SourceTable.selectAll().toList().mapNotNull { SourceType(it) }
        }

        return results
    }
}
