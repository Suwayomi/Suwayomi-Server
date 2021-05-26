package suwayomi.anime.impl.extension.github

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import suwayomi.anime.model.dataclass.AnimeExtensionDataClass
import suwayomi.tachidesk.impl.util.network.UnzippingInterceptor
import uy.kohesive.injekt.injectLazy

object ExtensionGithubApi {
    const val BASE_URL = "https://raw.githubusercontent.com"
    const val REPO_URL_PREFIX = "$BASE_URL/jmir1/tachiyomi-extensions/repo"

    private const val LIB_VERSION_MIN = 1.3
    private const val LIB_VERSION_MAX = 1.3

    private fun parseResponse(json: JsonArray): List<OnlineExtension> {
        return json
            .map { it.asJsonObject }
            .filter { element ->
                val versionName = element["version"].string
                val libVersion = versionName.substringBeforeLast('.').toDouble()
                libVersion in LIB_VERSION_MIN..LIB_VERSION_MAX
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

                OnlineExtension(name, pkgName, versionName, versionCode, lang, nsfw, apkName, icon)
            }
    }

    suspend fun findExtensions(): List<OnlineExtension> {
        val response = getRepo()
        return parseResponse(response)
    }

    fun getApkUrl(extension: AnimeExtensionDataClass): String {
        return "$REPO_URL_PREFIX/apk/${extension.apkName}"
    }

    private val client by lazy {
        val network: NetworkHelper by injectLazy()
        network.client.newBuilder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .header("Content-Encoding", "gzip")
                    .header("Content-Type", "application/json")
                    .build()
            }
            .addInterceptor(UnzippingInterceptor())
            .build()
    }

    private fun getRepo(): com.google.gson.JsonArray {
        val request = Request.Builder()
            .url("$REPO_URL_PREFIX/index.json.gz")
            .build()

        val response = client.newCall(request).execute().use { response -> response.body!!.string() }
        return JsonParser.parseString(response).asJsonArray
    }
}
