/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.manga.model.dataclass.MangaUserOverrideDataClass

class MangaUserOverrideType(
    val id: Int,
    val mangaId: Int,
    val title: String?,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genre: List<String>?,
    val notes: String?,
    val hasCustomCover: Boolean,
    val customCoverUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
) : Node {
    constructor(dc: MangaUserOverrideDataClass) : this(
        id = dc.id,
        mangaId = dc.mangaId,
        title = dc.title,
        author = dc.author,
        artist = dc.artist,
        description = dc.description,
        genre = dc.genre,
        notes = dc.notes,
        hasCustomCover = dc.hasCustomCover,
        customCoverUrl = if (dc.hasCustomCover) "/api/v1/manga/${dc.mangaId}/custom-cover" else null,
        createdAt = dc.createdAt,
        updatedAt = dc.updatedAt,
    )
}
