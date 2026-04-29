/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.LibrarySearch

class LibrarySearchQuery {
    @RequireAuth
    fun searchLibrary(
        query: String,
        inLibraryOnly: Boolean? = null,
        searchTitle: Boolean? = null,
        searchAuthor: Boolean? = null,
        searchArtist: Boolean? = null,
        searchDescription: Boolean? = null,
        searchGenre: Boolean? = null,
        limit: Int? = null,
    ): List<MangaType> =
        LibrarySearch
            .search(
                query = query,
                inLibraryOnly = inLibraryOnly ?: true,
                searchTitle = searchTitle ?: true,
                searchAuthor = searchAuthor ?: true,
                searchArtist = searchArtist ?: false,
                searchDescription = searchDescription ?: false,
                searchGenre = searchGenre ?: true,
                limit = limit?.coerceIn(1, 1000) ?: 200,
            ).map { MangaType(it) }
}
