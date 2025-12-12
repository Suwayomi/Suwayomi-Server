package suwayomi.tachidesk.manga.impl.extension.ireader

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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy

/**
 * API client for fetching IReader extensions from GitHub repositories.
 * 
 * This implementation follows the same patterns as ExtensionGithubApi for Tachiyomi extensions.
 */
object IReaderGithubApi {
    private val logger = KotlinLogging.logger {}
    private val json: Json by injectLazy()

    // IReader-specific version constraints
    private const val LIB_VERSION_MIN = 2
    private const val LIB_VERSION_MAX = 2

    @Serializable
    private data class IReaderExtensionJsonObject(
        @SerialName("name") val name: String,
        @SerialName("pkg") val pkg: String,
        @SerialName("version") val version: String,
        @SerialName("code") val code: Int,
        @SerialName("lang") val lang: String,
        @SerialName("apk") val apk: String,
        @SerialName("id") val id: Long,
        @SerialName("description") val description: String = "",
        @SerialName("nsfw") val nsfw: Boolean = false,
    )

    /**
     * Fetch extensions from a repository URL.
     */
    suspend fun findExtensions(repo: String): List<OnlineIReaderExtension> {
        val response = client.newCall(GET(repo)).awaitSuccess()

        return with(json) {
            response
                .parseAs<List<IReaderExtensionJsonObject>>()
                .toExtensions(repo.substringBeforeLast('/') + '/')
        }
    }

    /**
     * Get the APK download URL for an extension.
     */
    fun getApkUrl(repo: String, apkName: String): String = "${repo}apk/$apkName"

    /**
     * Get the JAR download URL for an extension.
     * IReader extensions provide pre-built JAR files.
     */
    fun getJarUrl(repo: String, apkName: String): String {
        val jarName = apkName.replace(".apk", ".jar")
        return "${repo}jar/$jarName"
    }

    private val client by lazy {
        val network: NetworkHelper by injectLazy()
        network.client
            .newBuilder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse
                    .newBuilder()
                    .header("Content-Type", "application/json")
                    .build()
            }.build()
    }

    /**
     * Convert JSON objects to OnlineIReaderExtension list.
     * Filters by supported library version.
     */
    private fun List<IReaderExtensionJsonObject>.toExtensions(repo: String): List<OnlineIReaderExtension> =
        this
            .filter {
                val libVersion = it.version.substringBefore('.').toIntOrNull() ?: 0
                libVersion in LIB_VERSION_MIN..LIB_VERSION_MAX
            }
            .map {
                OnlineIReaderExtension(
                    repo = repo,
                    name = it.name.substringAfter("IReader: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    lang = it.lang,
                    isNsfw = it.nsfw,
                    apkName = it.apk,
                    iconUrl = "${repo}icon/${it.pkg}.png",
                )
            }
}
