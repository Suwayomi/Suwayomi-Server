package suwayomi.tachidesk.manga.impl.text

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass

/**
 * Shared entry point for Light Novel prose content and reading progress.
 * Delegates directly to the canonical Chapter domain pipeline to eliminate parallel subsystems.
 */
object NovelContentService {
    suspend fun getChapterText(chapterId: Int): ChapterTextContent = Chapter.getChapterText(chapterId)

    suspend fun getChapterTextOrNull(chapterId: Int): ChapterTextContent? = Chapter.getChapterTextOrNull(chapterId)

    suspend fun updateTextProgress(
        chapterId: Int,
        progress: Float,
    ): ChapterDataClass = Chapter.updateTextProgress(chapterId, progress)
}
