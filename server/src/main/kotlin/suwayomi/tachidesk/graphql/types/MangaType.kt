/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.time.Instant
import java.util.concurrent.CompletableFuture

class MangaType(
    val id: Int,
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val initialized: Boolean,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val status: MangaStatus,
    val inLibrary: Boolean,
    val inLibraryAt: Long,
    val realUrl: String?,
    var lastFetchedAt: Long?,
    var chaptersLastFetchedAt: Long?
) {
    constructor(row: ResultRow) : this(
        row[MangaTable.id].value,
        row[MangaTable.sourceReference],
        row[MangaTable.url],
        row[MangaTable.title],
        row[MangaTable.thumbnail_url],
        row[MangaTable.initialized],
        row[MangaTable.artist],
        row[MangaTable.author],
        row[MangaTable.description],
        row[MangaTable.genre].toGenreList(),
        MangaStatus.valueOf(row[MangaTable.status]),
        row[MangaTable.inLibrary],
        row[MangaTable.inLibraryAt],
        row[MangaTable.realUrl],
        row[MangaTable.lastFetchedAt],
        row[MangaTable.chaptersLastFetchedAt]
    )

    constructor(dataClass: MangaDataClass) : this(
        dataClass.id,
        dataClass.sourceId.toLong(),
        dataClass.url,
        dataClass.title,
        dataClass.thumbnailUrl,
        dataClass.initialized,
        dataClass.artist,
        dataClass.author,
        dataClass.description,
        dataClass.genre,
        MangaStatus.valueOf(dataClass.status),
        dataClass.inLibrary,
        dataClass.inLibraryAt,
        dataClass.realUrl,
        dataClass.lastFetchedAt,
        dataClass.chaptersLastFetchedAt
    )

    fun chapters(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<ChapterType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, List<ChapterType>>("ChaptersForMangaDataLoader", id)
    }

    fun age(): Long? {
        if (lastFetchedAt == null) return null
        return Instant.now().epochSecond.minus(lastFetchedAt!!)
    }

    fun chaptersAge(): Long? {
        if (chaptersLastFetchedAt == null) return null

        return Instant.now().epochSecond.minus(chaptersLastFetchedAt!!)
    }

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MetaType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, MetaType>("MangaMetaDataLoader", id)
    }

    fun categories(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<CategoryType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, List<CategoryType>>("CategoriesForMangaDataLoader", id)
    }

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<SourceType?> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, SourceType?>("SourceForMangaDataLoader", id)
    }
}
