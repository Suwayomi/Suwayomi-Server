package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.BufferedSource
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.dataclass.ExtensionInfo
import suwayomi.tachidesk.manga.model.dataclass.ExtensionKind
import suwayomi.tachidesk.manga.model.dataclass.ExtensionSource
import suwayomi.tachidesk.manga.model.dataclass.ExtensionStore
import java.security.MessageDigest

object LnReaderRepository {
    private const val SOURCE_ID_BITS = 52
    private const val VERSION_PART_BITS = 17
    private const val VERSION_PART_MAX = (1L shl VERSION_PART_BITS) - 1
    private val versionRegex = Regex("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?$")
    private val manifestMarkers = setOf("id", "site", "url", "iconUrl", "customJS", "customCSS")

    @Serializable
    data class PluginItem(
        val id: String,
        val name: String,
        val site: String,
        val lang: String,
        val version: String,
        val url: String,
        val iconUrl: String,
        val customJS: String? = null,
        val customCSS: String? = null,
        val hasUpdate: Boolean? = null,
        val hasSettings: Boolean? = null,
    )

    data class ExistingSourceOwner(
        val runtimeKind: ExtensionKind,
        val pluginId: String?,
    )

    fun decodeAndValidate(
        json: Json,
        source: BufferedSource,
    ): List<PluginItem> = validate(json.decodeFromBufferedSource<List<PluginItem>>(source))

    internal fun containsManifestMarkers(
        json: Json,
        source: BufferedSource,
    ): Boolean {
        val array =
            runCatching { json.decodeFromBufferedSource<JsonElement>(source) }
                .getOrNull() as? JsonArray
                ?: return false
        return array.any { element ->
            (element as? JsonObject)?.keys?.any { it in manifestMarkers } == true
        }
    }

    private val LANGUAGE_CODE_MAP =
        mapOf(
            "english" to "en",
            "español" to "es",
            "spanish" to "es",
            "français" to "fr",
            "french" to "fr",
            "bahasa indonesia" to "id",
            "indonesian" to "id",
            "polski" to "pl",
            "polish" to "pl",
            "português" to "pt",
            "portuguese" to "pt",
            "русский" to "ru",
            "russian" to "ru",
            "türkçe" to "tr",
            "turkish" to "tr",
            "українська" to "uk",
            "ukrainian" to "uk",
            "tiếng việt" to "vi",
            "vietnamese" to "vi",
            "multi" to "all",
            "arabic" to "ar",
            "chinese" to "zh",
            "japanese" to "ja",
            "korean" to "ko",
            "thai" to "th",
        )

    fun toExtensionStore(indexUrl: String): ExtensionStore =
        ExtensionStore(
            indexUrl = indexUrl,
            name = "LNReader (${requireHttpUrl("repository", indexUrl).host})",
            badgeLabel = "LNReader",
            signingKey = "",
            contact = ExtensionStore.Contact(website = indexUrl, discord = null),
            isLegacy = false,
            extensionListUrl = null,
            kind = ExtensionKind.LNREADER,
        )

    fun normalizeLanguage(lang: String): String {
        val trimmed = lang.trim()
        val lower = trimmed.lowercase()
        LANGUAGE_CODE_MAP[lower]?.let { return it }
        return when {
            trimmed.contains("العربية") -> "ar"
            trimmed.contains("中文") || trimmed.contains("漢語") || trimmed.contains("汉语") -> "zh"
            trimmed.contains("日本語") -> "ja"
            trimmed.contains("한국어") || trimmed.contains("조선말") -> "ko"
            trimmed.contains("ไทย") -> "th"
            else -> lower
        }
    }

    fun toExtensionInfos(
        store: ExtensionStore,
        plugins: List<PluginItem>,
    ): List<ExtensionInfo> =
        plugins.map { plugin ->
            val normalizedLang = normalizeLanguage(plugin.lang)
            ExtensionInfo(
                storeIndexUrl = store.indexUrl,
                name = plugin.name,
                pkgName = packageName(plugin.id),
                apkUrl = null,
                jarUrl = null,
                iconUrl = plugin.iconUrl,
                extensionLib = null,
                versionCode = versionCode(plugin.version),
                versionName = plugin.version,
                lang = normalizedLang,
                contentWarning = ContentWarning.SAFE,
                sources =
                    listOf(
                        ExtensionSource(
                            id = sourceId(plugin.id),
                            name = plugin.name,
                            lang = normalizedLang,
                            homeUrl = plugin.site,
                            message = null,
                            contentWarning = ContentWarning.SAFE,
                        ),
                    ),
                runtimeKind = ExtensionKind.LNREADER,
                pluginId = plugin.id,
                siteUrl = plugin.site,
                codeUrl = plugin.url,
                customJsUrl = plugin.customJS,
                customCssUrl = plugin.customCSS,
            )
        }

    fun mergeStoreResults(
        storeResults: List<List<ExtensionInfo>>,
        configuredStoreOrder: Map<String, Int> = emptyMap(),
    ): List<ExtensionInfo> {
        val jvmExtensions = mutableListOf<ExtensionInfo>()
        val lnReaderExtensions = linkedMapOf<String, ExtensionInfo>()

        storeResults.forEach { extensions ->
            extensions.forEach { extension ->
                if (extension.runtimeKind == ExtensionKind.LNREADER) {
                    val pluginId = requireNotNull(extension.pluginId) { "LNReader extension is missing plugin id" }
                    val current = lnReaderExtensions[pluginId]
                    if (current == null || hasHigherStorePrecedence(extension, current, configuredStoreOrder)) {
                        lnReaderExtensions[pluginId] = extension
                    }
                } else {
                    // Keep the JVM stream in its original order so existing JVM duplicate/version selection is unchanged.
                    jvmExtensions += extension
                }
            }
        }

        return jvmExtensions + lnReaderExtensions.values
    }

    private fun hasHigherStorePrecedence(
        candidate: ExtensionInfo,
        current: ExtensionInfo,
        configuredStoreOrder: Map<String, Int>,
    ): Boolean {
        val candidateOrder = configuredStoreOrder[candidate.storeIndexUrl]
        val currentOrder = configuredStoreOrder[current.storeIndexUrl]
        return when {
            candidateOrder != null && currentOrder != null -> candidateOrder >= currentOrder
            candidateOrder != null -> true
            currentOrder != null -> false
            else -> candidate.storeIndexUrl >= current.storeIndexUrl
        }
    }

    fun validateSourceIdCollisions(
        extensions: Iterable<ExtensionInfo>,
        existingSources: Map<Long, ExistingSourceOwner> = emptyMap(),
    ) {
        val claimedIds = mutableMapOf<Long, String>()

        extensions.filter { it.runtimeKind == ExtensionKind.LNREADER }.forEach { extension ->
            val pluginId = requireNotNull(extension.pluginId) { "LNReader extension is missing plugin id" }
            val sourceId = sourceId(pluginId)
            val previousPluginId = claimedIds.putIfAbsent(sourceId, pluginId)
            check(previousPluginId == null || previousPluginId == pluginId) {
                "LNReader source id collision: '$pluginId' and '$previousPluginId' both map to $sourceId"
            }

            val existingOwner = existingSources[sourceId] ?: return@forEach
            check(existingOwner.runtimeKind == ExtensionKind.LNREADER && existingOwner.pluginId == pluginId) {
                "LNReader source id collision: plugin '$pluginId' maps to existing source id $sourceId"
            }
        }
    }

    /**
     * Frozen LNReader source identity: take the first 52 bits of
     * SHA-256("lnreader:" + pluginId) as an unsigned big-endian integer.
     * Zero is remapped to one so every persisted source id is positive.
     */
    fun sourceId(pluginId: String): Long {
        require(pluginId.isNotBlank()) { "LNReader plugin id must not be blank" }
        val digest = digest(pluginId)
        var first56Bits = 0L
        repeat(7) { index ->
            first56Bits = (first56Bits shl 8) or (digest[index].toLong() and 0xffL)
        }
        val projected = first56Bits ushr (56 - SOURCE_ID_BITS)
        return if (projected == 0L) 1L else projected
    }

    fun webStorageOrigin(value: String): String? {
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) return null
        return "${url.scheme}://${url.host}:${url.port}"
    }

    fun packageName(pluginId: String): String = "lnreader.p${digest(pluginId).toHex()}"

    private fun validate(plugins: List<PluginItem>): List<PluginItem> {
        val ids = mutableSetOf<String>()
        plugins.forEach { plugin ->
            requireField("id", plugin.id, 256)
            requireField("name", plugin.name, 128)
            requireField("lang", plugin.lang, 32)
            requireField("version", plugin.version, 16)
            require(ids.add(plugin.id)) { "Duplicate LNReader plugin id '${plugin.id}' in one repository" }
            requireSafeSite(plugin.site)
            requireHttpUrl("url", plugin.url)
            requireHttpUrl("iconUrl", plugin.iconUrl)
            plugin.customJS?.let { requireHttpUrl("customJS", it) }
            plugin.customCSS?.let { requireHttpUrl("customCSS", it) }
            versionCode(plugin.version)
        }
        return plugins
    }

    private fun requireSafeSite(site: String) {
        requireField("site", site, 2048)
        require(!site.startsWith("javascript:", ignoreCase = true) && !site.startsWith("data:", ignoreCase = true)) {
            "LNReader plugin site has an unsafe scheme"
        }
    }

    private fun requireField(
        field: String,
        value: String,
        maxLength: Int,
    ) {
        require(value.isNotBlank() && value.length <= maxLength && value.none(Char::isISOControl)) {
            "LNReader plugin $field is invalid"
        }
    }

    private fun requireHttpUrl(
        field: String,
        value: String,
    ): HttpUrl {
        require(value.length <= 2048) { "LNReader $field URL exceeds 2048 characters" }
        require(value.none(Char::isISOControl)) { "LNReader $field URL contains control characters" }
        val httpUrl = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid LNReader $field URL")
        require(httpUrl.scheme.equals("http", true) || httpUrl.scheme.equals("https", true)) {
            "LNReader $field URL must use HTTP(S)"
        }
        require(httpUrl.host.isNotBlank()) { "LNReader $field URL must include a host" }
        return httpUrl
    }

    private fun versionCode(version: String): Long {
        val match =
            versionRegex.matchEntire(version)
                ?: throw IllegalArgumentException("LNReader version '$version' must contain one to three numeric components")
        val parts = (1..3).map { match.groupValues[it].ifBlank { "0" }.toLong() }
        require(parts.all { it <= VERSION_PART_MAX }) { "LNReader version '$version' contains an oversized component" }
        return (parts[0] shl (VERSION_PART_BITS * 2)) or (parts[1] shl VERSION_PART_BITS) or parts[2]
    }

    private fun digest(pluginId: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("lnreader:$pluginId".toByteArray(Charsets.UTF_8))

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
