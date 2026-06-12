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
    val tag: String,
    val url: String,
)

object AppUpdate {
    private const val LATEST_RELEASE_REPO_URL = "https://api.github.com/repos/vtorres-t/Suwayomi-Server/releases/latest"

    private val json: Json by injectLazy()
    private val network: NetworkHelper by injectLazy()

    suspend fun checkUpdate(): List<UpdateDataClass> {
        val stableJson =
            json
                .parseToJsonElement(
                    network.client
                        .newCall(
                            GET(LATEST_RELEASE_REPO_URL),
                        ).await()
                        .body
                        .string(),
                ).jsonObject

        return listOf(
            UpdateDataClass(
                stableJson["tag_name"]!!.jsonPrimitive.content,
                stableJson["html_url"]!!.jsonPrimitive.content,
            ),
        )
    }
}
