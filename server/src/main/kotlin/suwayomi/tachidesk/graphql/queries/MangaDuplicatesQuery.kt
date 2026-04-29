/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.impl.MangaDuplicates

class MangaDuplicatesQuery {
    @RequireAuth
    fun findDuplicateMangas(mangaId: Int): List<MangaType> =
        MangaDuplicates
            .findDuplicates(mangaId)
            .map { MangaType(it) }
}
