package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class TrackRecordDataClass(
    val id: Int,
    val mangaId: Int,
    val trackerId: Int,
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,
    val lastChapterRead: Double,
    val totalChapters: Int,
    val status: Int,
    val score: Double,
    var scoreString: String? = null,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
)
