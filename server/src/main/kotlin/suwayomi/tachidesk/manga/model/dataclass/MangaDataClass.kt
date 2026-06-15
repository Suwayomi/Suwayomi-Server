package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import suwayomi.tachidesk.manga.impl.Manga.getMangaMetaMap
import suwayomi.tachidesk.manga.impl.util.lang.trimAll
import suwayomi.tachidesk.manga.model.table.MangaStatus
import java.time.Instant

data class MangaDataClass(
    val id: Int,
    val sourceId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val thumbnailUrlLastFetched: Long = 0,
    val initialized: Boolean = false,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: String = MangaStatus.UNKNOWN.name,
    val inLibrary: Boolean = false,
    val inLibraryAt: Long = 0,
    val source: SourceDataClass? = null,
    val realUrl: String? = null,
    val lastFetchedAt: Long? = 0,
    val chaptersLastFetchedAt: Long? = 0,
    val updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    val freshData: Boolean = false,
    val unreadCount: Long? = null,
    val downloadCount: Long? = null,
    val chapterCount: Long? = null,
    val lastReadAt: Long? = null,
    val lastChapterRead: ChapterDataClass? = null,
    val age: Long? = if (lastFetchedAt == null) 0 else Instant.now().epochSecond.minus(lastFetchedAt),
    val chaptersAge: Long? = if (chaptersLastFetchedAt == null) null else Instant.now().epochSecond.minus(chaptersLastFetchedAt),
    val trackers: List<MangaTrackerDataClass>? = null,
    val lastModifiedAt: Long = 0,
    val version: Long = 0,
) {
    override fun toString(): String = "\"$title\" (id= $id) (sourceId= $sourceId)"

    @Deprecated("Remove with V1 Api")
    val meta: Map<String, String> by lazy {
        getMangaMetaMap(id)
    }
}

data class PagedMangaListDataClass(
    val mangaList: List<MangaDataClass>,
    val hasNextPage: Boolean,
)

internal fun String?.toGenreList() = this?.split(",")?.trimAll().orEmpty()
