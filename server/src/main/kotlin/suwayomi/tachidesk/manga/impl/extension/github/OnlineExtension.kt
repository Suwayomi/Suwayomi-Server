package suwayomi.tachidesk.manga.impl.extension.github

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class OnlineExtensionSource(
    val name: String,
    val lang: String,
    val id: Long,
    val baseUrl: String,
)

data class OnlineExtension(
    val repo: String,
    val name: String,
    val pkgName: String,
    val apkName: String,
    val lang: String,
    val versionCode: Int,
    val versionName: String,
    val isNsfw: Boolean,
    val hasReadme: Boolean,
    val hasChangelog: Boolean,
    val sources: List<OnlineExtensionSource>,
    val iconUrl: String,
)
