/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Filter by read
 * - Filter by bookmarked
 * - Filter by downloaded
 * - Filter by scanlators
 * - Sort? Upload date, source order, last read, chapter number
 * - Get page list?
 *
 * TODO Mutations
 * - Last page read
 * - Read status
 * - bookmark status
 * - Check for updates?
 * - Download
 * - Delete download
 */
class ChapterQuery {
    fun chapter(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<ChapterType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, ChapterType>("ChapterDataLoader", id)
    }

    data class ChapterQueryInput(
        val ids: List<Int>? = null,
        val mangaIds: List<Int>? = null,
        val page: Int? = null,
        val count: Int? = null
    )

    fun chapters(input: ChapterQueryInput? = null): List<ChapterType> {
        val results = transaction {
            var res = ChapterTable.selectAll()

            if (input != null) {
                if (input.mangaIds != null) {
                    res = res.andWhere { ChapterTable.manga inList input.mangaIds }
                }
                if (input.ids != null) {
                    res = res.andWhere { ChapterTable.id inList input.ids }
                }
                if (input.count != null) {
                    val offset = if (input.page == null) 0 else (input.page * input.count).toLong()
                    res = res.limit(input.count, offset)
                }
            }

            res.toList()
        }

        return results.map { ChapterType(it) }
    }
}
