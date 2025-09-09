package suwayomi.tachidesk.manga.impl.download.model

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.manga.impl.download.model.DownloadState.Queued

class DownloadQueueItem(
    val chapterId: Int,
    val chapterIndex: Int,
    val mangaId: Int,
    val sourceId: Long,
    var position: Int,
    var pageCount: Int,
    var state: DownloadState = Queued,
    var progress: Float = 0f,
    var tries: Int = 0,
) {
    override fun toString(): String = "$mangaId - $chapterId | state= $state, tries= $tries, progress= $progress"
}
