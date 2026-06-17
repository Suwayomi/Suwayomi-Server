package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class ExtensionInfo(
    val storeIndexUrl: String,
    val name: String,
    val pkgName: String,
    val apkUrl: String,
    val iconUrl: String,
    val extensionLib: String,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val contentRating: ContentRating,
    val sources: List<ExtensionSource>,
)

data class ExtensionSource(
    val id: Long,
    val name: String,
    val lang: String,
    val homeUrl: String,
)

enum class ContentRating {
    SAFE,
    SUGGESTIVE,
    EROTICA,
    PORNOGRAPHIC,
}
