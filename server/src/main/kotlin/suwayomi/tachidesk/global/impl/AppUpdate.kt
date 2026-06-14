package suwayomi.tachidesk.global.impl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import suwayomi.tachidesk.graphql.types.RepoType
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.manga.impl.util.network.await
import suwayomi.tachidesk.server.serverConfig
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
    private val json: Json by injectLazy()
    private val network: NetworkHelper by injectLazy()

    suspend fun checkServerUpdate(): List<UpdateDataClass> =
        checkUpdate(serverConfig.repoServerType.value, serverConfig.repoServerUrl.value)

    suspend fun checkWebUIUpdate(repoUrl: String): List<UpdateDataClass> = checkUpdate(serverConfig.repoWebUiType.value, repoUrl)

    suspend fun checkUpdate(
        repoType: RepoType,
        repoUrl: String,
    ): List<UpdateDataClass> {
        val repoType = serverConfig.repoServerType.value
        val cleanUrl = repoUrl.removeSuffix("/")

        val apiUrl =
            when (repoType) {
                RepoType.Github -> {
                    val path = cleanUrl.substringAfter("github.com/")
                    "https://api.github.com/repos/$path/releases/latest"
                }

                RepoType.Gitea -> {
                    val scheme = cleanUrl.substringBefore("://") + "://"
                    val domainAndPath = cleanUrl.substringAfter("://")
                    val baseUrl = scheme + domainAndPath.substringBefore("/")
                    val path = domainAndPath.substringAfter("/")
                    "$baseUrl/api/v1/repos/$path/releases/latest"
                }
            }

        val response =
            network.client
                .newCall(GET(apiUrl))
                .await()
                .body
                .string()

        val stableJson = json.parseToJsonElement(response).jsonObject

        return listOf(
            UpdateDataClass(
                stableJson["tag_name"]!!.jsonPrimitive.content,
                stableJson["html_url"]!!.jsonPrimitive.content,
            ),
        )
    }
}
