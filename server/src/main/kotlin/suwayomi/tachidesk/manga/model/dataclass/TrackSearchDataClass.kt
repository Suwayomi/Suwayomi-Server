package suwayomi.tachidesk.manga.model.dataclass

import kotlinx.serialization.Serializable

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

@Serializable
data class TrackSearchDataClass(
    val id: Int,
    val trackerId: Int,
    val remoteId: Long,
    val title: String,
    val totalChapters: Int,
    val trackingUrl: String,
    val coverUrl: String,
    val summary: String,
    val publishingStatus: String,
    val publishingType: String,
    val startDate: String,
)
