/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text

@GraphQLDescription("Novel information from IReader source")
data class IReaderNovelType(
    val title: String,
    val cover: String,
    val description: String,
    val author: String,
    val artist: String,
    val genres: List<String>,
    val status: Long,
    val key: String,
) {
    constructor(mangaInfo: MangaInfo) : this(
        title = mangaInfo.title,
        cover = mangaInfo.cover,
        description = mangaInfo.description,
        author = mangaInfo.author,
        artist = mangaInfo.artist,
        genres = mangaInfo.genres,
        status = mangaInfo.status,
        key = mangaInfo.key,
    )
}

@GraphQLDescription("Chapter information from IReader source")
data class IReaderChapterType(
    val name: String,
    val key: String,
    val dateUpload: Long,
    val number: Float,
    val scanlator: String,
) {
    constructor(chapterInfo: ChapterInfo) : this(
        name = chapterInfo.name,
        key = chapterInfo.key,
        dateUpload = chapterInfo.dateUpload,
        number = chapterInfo.number,
        scanlator = chapterInfo.scanlator,
    )
}

@GraphQLDescription("Page of novels from IReader source")
data class IReaderNovelsPageType(
    val novels: List<IReaderNovelType>,
    val hasNextPage: Boolean,
) {
    constructor(pageInfo: MangasPageInfo) : this(
        novels = pageInfo.mangas.map { IReaderNovelType(it) },
        hasNextPage = pageInfo.hasNextPage,
    )
}

@GraphQLDescription("Chapter content page")
data class IReaderPageType(
    val text: String,
) {
    companion object {
        fun fromPage(page: Page): IReaderPageType =
            when (page) {
                is Text -> IReaderPageType(page.text)
                else -> IReaderPageType("")
            }
    }
}
