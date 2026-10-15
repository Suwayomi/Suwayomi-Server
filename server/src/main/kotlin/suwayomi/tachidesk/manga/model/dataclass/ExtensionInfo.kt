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

/**
 * Persisted content-warning level (`EXTENSION.content_warning` / `SOURCE.content_warning`
 * integer columns).
 *
 * NOTE: whether the wire values in extension indexes/manifests are ordinals or explicit
 * codes is unconfirmed upstream; the server treats its own model ordering as the persisted
 * contract, so [value] is pinned explicitly instead of relying on `ordinal`. Comparisons
 * like `>= MIXED` are severity thresholds.
 */
enum class ContentWarning(
    val value: Int,
) {
    SAFE(0),
    MIXED(1),
    NSFW(2),
    ;

    companion object {
        val maxValue = entries.maxOf { it.value }

        fun fromValue(value: Int) = entries.find { it.value == value } ?: SAFE

        fun valueOf(contentWarning: Int) = fromValue(contentWarning)
    }
}
