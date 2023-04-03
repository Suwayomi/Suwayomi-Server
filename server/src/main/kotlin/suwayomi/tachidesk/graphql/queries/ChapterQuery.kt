/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterNodeList
import suwayomi.tachidesk.graphql.types.ChapterNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.util.concurrent.CompletableFuture

/**
 * TODO Queries
 * - Filter by scanlators
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
    fun chapter(dataFetchingEnvironment: DataFetchingEnvironment, id: Int): CompletableFuture<ChapterType?> {
        return dataFetchingEnvironment.getValueFromDataLoader("ChapterDataLoader", id)
    }

    enum class ChapterSort {
        SOURCE_ORDER,
        NAME,
        UPLOAD_DATE,
        CHAPTER_NUMBER,
        LAST_READ_AT,
        FETCHED_AT
    }

    data class ChapterQueryInput(
        val ids: List<Int>? = null,
        val mangaIds: List<Int>? = null,
        val read: Boolean? = null,
        val bookmarked: Boolean? = null,
        val downloaded: Boolean? = null,
        val sort: ChapterSort? = null,
        val sortOrder: SortOrder? = null,
        val page: Int? = null,
        val count: Int? = null
    )

    fun chapters(input: ChapterQueryInput? = null): ChapterNodeList {
        val results = transaction {
            var res = ChapterTable.selectAll()

            if (input != null) {
                if (input.mangaIds != null) {
                    res.andWhere { ChapterTable.manga inList input.mangaIds }
                }
                if (input.ids != null) {
                    res.andWhere { ChapterTable.id inList input.ids }
                }
                if (input.read != null) {
                    res.andWhere { ChapterTable.isRead eq input.read }
                }
                if (input.bookmarked != null) {
                    res.andWhere { ChapterTable.isBookmarked eq input.bookmarked }
                }
                if (input.downloaded != null) {
                    res.andWhere { ChapterTable.isDownloaded eq input.downloaded }
                }
                val orderBy = when (input.sort) {
                    ChapterSort.SOURCE_ORDER, null -> ChapterTable.sourceOrder
                    ChapterSort.NAME -> ChapterTable.name
                    ChapterSort.UPLOAD_DATE -> ChapterTable.date_upload
                    ChapterSort.CHAPTER_NUMBER -> ChapterTable.chapter_number
                    ChapterSort.LAST_READ_AT -> ChapterTable.lastReadAt
                    ChapterSort.FETCHED_AT -> ChapterTable.fetchedAt
                }
                res.orderBy(orderBy, order = input.sortOrder ?: SortOrder.ASC)

                if (input.count != null) {
                    val offset = if (input.page == null) 0 else (input.page * input.count).toLong()
                    res.limit(input.count, offset)
                }
            } else {
                res.orderBy(ChapterTable.sourceOrder)
            }

            res.toList()
        }

        return results.map { ChapterType(it) }.toNodeList() // todo paged
    }
}
