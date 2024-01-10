package suwayomi.tachidesk.manga.impl.extension.github

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MIN
import uy.kohesive.injekt.injectLazy

object ExtensionGithubApi {
    private val logger = KotlinLogging.logger {}
    private val json: Json by injectLazy()

    @Serializable
    private data class ExtensionJsonObject(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String,
        val code: Int,
        val version: String,
        val nsfw: Int,
        val hasReadme: Int = 0,
        val hasChangelog: Int = 0,
        val sources: List<ExtensionSourceJsonObject>?,
    )

    @Serializable
    private data class ExtensionSourceJsonObject(
        val name: String,
        val lang: String,
        val id: Long,
        val baseUrl: String,
    )

    suspend fun findExtensions(repo: String): List<OnlineExtension> {
        val response =
            client.newCall(GET(repo)).awaitSuccess()

        return with(json) {
            response
                .parseAs<List<ExtensionJsonObject>>()
                .toExtensions(repo.substringBeforeLast('/') + '/')
        }
    }

    fun getApkUrl(
        repo: String,
        apkName: String,
    ): String {
        return "${repo}apk/$apkName"
    }

    private val client by lazy {
        val network: NetworkHelper by injectLazy()
        network.client.newBuilder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .header("Content-Type", "application/json")
                    .build()
            }
            .build()
    }

    private fun List<ExtensionJsonObject>.toExtensions(repo: String): List<OnlineExtension> {
        return this
            .filter {
                val libVersion = it.version.substringBeforeLast('.').toDouble()
                libVersion in LIB_VERSION_MIN..LIB_VERSION_MAX
            }
            .map {
                OnlineExtension(
                    repo = repo,
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources?.toExtensionSources() ?: emptyList(),
                    apkName = it.apk,
                    iconUrl = "${repo}icon/${it.pkg}.png",
                )
            }
    }

    private fun List<ExtensionSourceJsonObject>.toExtensionSources(): List<OnlineExtensionSource> {
        return this.map {
            OnlineExtensionSource(
                name = it.name,
                lang = it.lang,
                id = it.id,
                baseUrl = it.baseUrl,
            )
        }
    }
}
