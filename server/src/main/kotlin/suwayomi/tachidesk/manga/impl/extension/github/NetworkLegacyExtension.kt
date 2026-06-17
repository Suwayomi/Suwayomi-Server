package suwayomi.tachidesk.manga.impl.extension.github

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.model.dataclass.ContentRating
import suwayomi.tachidesk.manga.model.dataclass.ExtensionInfo
import suwayomi.tachidesk.manga.model.dataclass.ExtensionSource
import suwayomi.tachidesk.manga.model.dataclass.ExtensionStore

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NetworkLegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val version: String,
    val code: Long,
    val nsfw: Int,
    val sources: List<Source>? = null,
) {
    @Serializable
    data class Source(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    )
}

fun NetworkLegacyExtension.toExtensionInfo(
    store: ExtensionStore,
    storeBaseUrl: String,
): ExtensionInfo =
    ExtensionInfo(
        storeIndexUrl = store.indexUrl,
        name = name.substringAfter("Tachiyomi: "),
        pkgName = pkg,
        apkUrl = "$storeBaseUrl/apk/$apk",
        iconUrl = "$storeBaseUrl/icon/$pkg.png",
        extensionLib = version.substringBeforeLast('.'),
        versionCode = code,
        versionName = version,
        lang = lang,
        contentRating = if (nsfw == 1) ContentRating.PORNOGRAPHIC else ContentRating.SAFE,
        sources =
            if (sources.isNullOrEmpty()) {
                listOf(
                    ExtensionSource(
                        id = 0,
                        name = name,
                        lang = lang,
                        homeUrl = "",
                        message = null,
                        contentRating = if (nsfw == 1) ContentRating.PORNOGRAPHIC else ContentRating.SAFE,
                    ),
                )
            } else {
                sources.map { source ->
                    ExtensionSource(
                        id = source.id,
                        name = source.name,
                        lang = source.lang,
                        homeUrl = source.baseUrl,
                        message = null,
                        contentRating = if (nsfw == 1) ContentRating.PORNOGRAPHIC else ContentRating.SAFE,
                    )
                }
            },
    )
