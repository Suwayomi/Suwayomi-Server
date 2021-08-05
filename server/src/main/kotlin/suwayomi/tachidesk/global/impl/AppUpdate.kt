package suwayomi.tachidesk.global.impl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import suwayomi.tachidesk.manga.impl.util.network.await
import uy.kohesive.injekt.injectLazy

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class UpdateDataClass(
    val channel: String,
    val tag: String,
    val url: String
)

object AppUpdate {
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/Suwayomi/Tachidesk/releases/latest"
    const val LATEST_PREVIEW_URL = "https://api.github.com/repos/Suwayomi/Tachidesk-preview/releases/latest"

    private val json: Json by injectLazy()
    private val network: NetworkHelper by injectLazy()

    suspend fun checkUpdate(): List<UpdateDataClass> {
        val releaseJson = json.parseToJsonElement(
            network.client.newCall(
                GET(LATEST_RELEASE_URL)
            ).await().body!!.string()
        ).jsonObject

        val previewJson = json.parseToJsonElement(
            network.client.newCall(
                GET(LATEST_PREVIEW_URL)
            ).await().body!!.string()
        ).jsonObject

        return listOf(
            UpdateDataClass(
                "Release",
                releaseJson["tag_name"]!!.jsonPrimitive.content,
                releaseJson["html_url"]!!.jsonPrimitive.content,
            ),
            UpdateDataClass(
                "Preview",
                previewJson["tag_name"]!!.jsonPrimitive.content,
                previewJson["html_url"]!!.jsonPrimitive.content,
            ),
        )
    }
}
