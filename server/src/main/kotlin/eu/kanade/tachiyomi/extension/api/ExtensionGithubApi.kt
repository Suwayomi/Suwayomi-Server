package eu.kanade.tachiyomi.extension.api

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import ir.armor.tachidesk.model.dataclass.ExtensionDataClass
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ExtensionGithubApi {
    const val BASE_URL = "https://raw.githubusercontent.com"
    const val REPO_URL_PREFIX = "$BASE_URL/tachiyomiorg/tachiyomi-extensions/repo"

    private fun parseResponse(json: JsonArray): List<Extension.Available> {
        return json
            .filter { element ->
                val versionName = element.jsonObject["version"]!!.jsonPrimitive.content
                val libVersion = versionName.substringBeforeLast('.').toDouble()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map { element ->
                val name = element.jsonObject["name"]!!.jsonPrimitive.content.substringAfter("Tachiyomi: ")
                val pkgName = element.jsonObject["pkg"]!!.jsonPrimitive.content
                val apkName = element.jsonObject["apk"]!!.jsonPrimitive.content
                val versionName = element.jsonObject["version"]!!.jsonPrimitive.content
                val versionCode = element.jsonObject["code"]!!.jsonPrimitive.int
                val lang = element.jsonObject["lang"]!!.jsonPrimitive.content
                val nsfw = element.jsonObject["nsfw"]!!.jsonPrimitive.int == 1
                val icon = "$REPO_URL_PREFIX/icon/${apkName.replace(".apk", ".png")}"

                Extension.Available(name, pkgName, versionName, versionCode, lang, nsfw, apkName, icon)
            }
    }

    fun findExtensions(): List<Extension.Available> {
        val service: ExtensionGithubService = ExtensionGithubService.create()

        val response = service.getRepo()
        return parseResponse(response)
    }

    fun getApkUrl(extension: ExtensionDataClass): String {
        return "$REPO_URL_PREFIX/apk/${extension.apkName}"
    }
}
