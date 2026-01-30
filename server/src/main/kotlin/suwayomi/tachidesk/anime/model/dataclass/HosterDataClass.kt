package suwayomi.tachidesk.anime.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.Hoster

data class HosterDataClass(
    val index: Int,
    val hosterUrl: String,
    val hosterName: String,
    val lazy: Boolean,
    val videoCount: Int?,
)

fun List<Hoster>.toDataClass(): List<HosterDataClass> =
    mapIndexed { index, hoster ->
        HosterDataClass(
            index = index,
            hosterUrl = hoster.hosterUrl,
            hosterName = hoster.hosterName,
            lazy = hoster.lazy,
            videoCount = hoster.videoList?.size,
        )
    }
