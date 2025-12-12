package suwayomi.tachidesk.manga.impl.extension.ireader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/**
 * Represents an IReader extension available online.
 * 
 * This data class follows the same structure as OnlineExtension for Tachiyomi extensions.
 */
data class OnlineIReaderExtension(
    val repo: String,
    val name: String,
    val pkgName: String,
    val apkName: String,
    val lang: String,
    val versionCode: Int,
    val versionName: String,
    val isNsfw: Boolean,
    val iconUrl: String,
)
