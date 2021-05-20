package eu.kanade.tachiyomi.extension.api

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import ir.armor.tachidesk.model.dataclass.ExtensionDataClass

object ExtensionGithubApi {
    const val BASE_URL = "https://raw.githubusercontent.com"
    const val REPO_URL_PREFIX = "$BASE_URL/tachiyomiorg/tachiyomi-extensions/repo"

    private fun parseResponse(json: JsonArray): List<Extension.Available> {
        return json
            .map { it.asJsonObject }
            .filter { element ->
                val versionName = element["version"].string
                val libVersion = versionName.substringBeforeLast('.').toDouble()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map { element ->
                val name = element["name"].string.substringAfter("Tachiyomi: ")
                val pkgName = element["pkg"].string
                val apkName = element["apk"].string
                val versionName = element["version"].string
                val versionCode = element["code"].int
                val lang = element["lang"].string
                val nsfw = element["nsfw"].int == 1
                val icon = "$REPO_URL_PREFIX/icon/${apkName.replace(".apk", ".png")}"

                Extension.Available(name, pkgName, versionName, versionCode, lang, nsfw, apkName, icon)
            }
    }

    suspend fun findExtensions(): List<Extension.Available> {

        val response = ExtensionGithubService.getRepo()
        return parseResponse(response)
    }

    fun getApkUrl(extension: ExtensionDataClass): String {
        return "$REPO_URL_PREFIX/apk/${extension.apkName}"
    }
}
