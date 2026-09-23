package suwayomi.tachidesk.manga.impl.text

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter

data class RawChapterText(
    val text: String,
    val customJs: String? = null,
    val customCss: String? = null,
)

interface ChapterTextSource : Source {
    suspend fun getChapterText(chapter: SChapter): RawChapterText
}
