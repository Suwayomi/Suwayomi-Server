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
    val jarUrl: String?,
    val iconUrl: String,
    val extensionLib: String,
    val versionCode: Long,
    val versionName: String,
    val lang: String,
    val contentWarning: ContentWarning,
    val sources: List<ExtensionSource>,
)

data class ExtensionSource(
    val id: Long,
    val name: String,
    val lang: String,
    val homeUrl: String,
    val message: String?,
    val contentWarning: ContentWarning,
)

enum class ContentWarning {
    SAFE,
    MIXED,
    NSFW,
    ;

    companion object {
        fun valueOf(contentWarning: Int) = entries.find { it.ordinal == contentWarning } ?: SAFE
    }
}
