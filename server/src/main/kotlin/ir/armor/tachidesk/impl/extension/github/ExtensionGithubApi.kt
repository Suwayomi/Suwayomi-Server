package ir.armor.tachidesk.impl.extension.github

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
import ir.armor.tachidesk.model.dataclass.ExtensionDataClass
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.RealResponseBody
import okio.GzipSource
import okio.buffer
import uy.kohesive.injekt.injectLazy
import java.io.IOException

object ExtensionGithubApi {
    const val BASE_URL = "https://raw.githubusercontent.com"
    const val REPO_URL_PREFIX = "$BASE_URL/tachiyomiorg/tachiyomi-extensions/repo"

    private const val LIB_VERSION_MIN = "1.2"
    private const val LIB_VERSION_MAX = "1.2"

    private fun parseResponse(json: JsonArray): List<OnlineExtension> {
        return json
            .map { it.asJsonObject }
            .filter { element ->
                val versionName = element["version"].string
                val libVersion = versionName.substringBeforeLast('.')
                libVersion == LIB_VERSION_MAX
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

    fun getApkUrl(extension: ExtensionDataClass): String {
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

// ref: https://stackoverflow.com/questions/51901333/okhttp-3-how-to-decompress-gzip-deflate-response-manually-using-java-android
private class UnzippingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Chain): Response {
        val response: Response = chain.proceed(chain.request())
        return unzip(response)
    }

    @Throws(IOException::class)
    private fun unzip(response: Response): Response {
        if (response.body == null) {
            return response
        }

        // check if we have gzip response
        val contentEncoding: String? = response.headers["Content-Encoding"]

        // this is used to decompress gzipped responses
        return if (contentEncoding != null && contentEncoding == "gzip") {
            val body = response.body!!
            val contentLength: Long = body.contentLength()
            val responseBody = GzipSource(body.source())
            val strippedHeaders: Headers = response.headers.newBuilder().build()
            response.newBuilder().headers(strippedHeaders)
                .body(RealResponseBody(body.contentType().toString(), contentLength, responseBody.buffer()))
                .build()
        } else {
            response
        }
    }
}
