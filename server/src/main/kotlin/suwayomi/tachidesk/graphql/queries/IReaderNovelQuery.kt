/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderChapterType
import suwayomi.tachidesk.graphql.types.IReaderNovelType
import suwayomi.tachidesk.graphql.types.IReaderNovelsPageType
import suwayomi.tachidesk.graphql.types.IReaderPageType
import suwayomi.tachidesk.manga.impl.IReaderNovel

class IReaderNovelQuery {
    @RequireAuth
    @GraphQLDescription("Get popular novels from an IReader source")
    fun ireaderPopularNovels(
        sourceId: Long,
        page: Int = 1,
    ): IReaderNovelsPageType = IReaderNovelsPageType(IReaderNovel.getPopularNovels(sourceId, page))

    @RequireAuth
    @GraphQLDescription("Get latest novels from an IReader source")
    fun ireaderLatestNovels(
        sourceId: Long,
        page: Int = 1,
    ): IReaderNovelsPageType = IReaderNovelsPageType(IReaderNovel.getLatestNovels(sourceId, page))

    @RequireAuth
    @GraphQLDescription("Search for novels in an IReader source")
    fun ireaderSearchNovels(
        sourceId: Long,
        query: String,
        page: Int = 1,
    ): IReaderNovelsPageType = IReaderNovelsPageType(IReaderNovel.searchNovels(sourceId, query, page))

    @RequireAuth
    @GraphQLDescription("Get detailed information about a novel")
    fun ireaderNovelDetails(
        sourceId: Long,
        novelUrl: String,
    ): IReaderNovelType = IReaderNovelType(IReaderNovel.getNovelDetails(sourceId, novelUrl))

    @RequireAuth
    @GraphQLDescription("Get the list of chapters for a novel")
    fun ireaderNovelChapters(
        sourceId: Long,
        novelUrl: String,
    ): List<IReaderChapterType> = IReaderNovel.getChapterList(sourceId, novelUrl).map { IReaderChapterType(it) }

    @RequireAuth
    @GraphQLDescription("Get the content/pages of a chapter")
    fun ireaderChapterContent(
        sourceId: Long,
        chapterUrl: String,
    ): List<IReaderPageType> = IReaderNovel.getChapterContent(sourceId, chapterUrl).map { IReaderPageType.fromPage(it) }
}
