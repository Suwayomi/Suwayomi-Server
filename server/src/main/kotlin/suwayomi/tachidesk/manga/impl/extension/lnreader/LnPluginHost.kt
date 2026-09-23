package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.nio.charset.Charset
import java.util.Base64

/** Routes every supported guest capability through [LnHostBridge.invoke]. */
class LnPluginHost(
    private val pluginId: String,
    private val runtime: LnPluginRuntime,
    private val json: Json = Json { encodeDefaults = true },
) {
    private val storage by lazy { LnPluginStorage.forPlugin(pluginId, runtime.webStorageOrigin ?: "") }
    private val network by lazy {
        LnNetworkGateway(
            Injekt.get<NetworkHelper>(),
            runtime,
            LnNetworkPolicy { LnNetworkPolicy.parseOrigins(serverConfig.lnReaderAllowedLocalOrigins.value) },
        )
    }

    fun bridge(): LnHostBridge = LnHostBridge(::invoke)

    private fun invoke(
        operation: String,
        requestJson: String,
    ): String =
        when (operation) {
            "fetch" -> {
                network.invoke(operation, requestJson)
            }

            "decode" -> {
                decode(requestJson)
            }

            "sleep" -> {
                sleep(requestJson)
            }

            "storage.registerDeclaredSettings" -> {
                val map = json.decodeFromString<Map<String, String>>(requestJson)
                map.forEach { (k, v) -> storage.registerSetting(k, v) }
                "{}"
            }

            "webStorage.local", "webStorage.session" -> {
                if (runtime.webStorageUtilized) storage.invoke(operation, requestJson) else "null"
            }

            else -> {
                storage.invoke(operation, requestJson)
            }
        }

    private fun sleep(requestJson: String): String {
        val request = json.parseToJsonElement(requestJson).jsonObject
        val ms =
            requireNotNull(
                request["ms"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toDoubleOrNull()
                    ?.toLong(),
            ) { "LNReader sleep duration is required" }
        runtime.sleep(ms)
        return "{}"
    }

    private fun decode(requestJson: String): String {
        val request = json.parseToJsonElement(requestJson).jsonObject
        val encoded = requireNotNull(request["bodyBase64"]?.jsonPrimitive?.contentOrNull) { "LNReader decode body is required" }
        require(encoded.length <= MAX_BASE64_BYTES) { "LNReader decode body is too large" }
        val rawName = requireNotNull(request["encoding"]?.jsonPrimitive?.contentOrNull) { "LNReader charset is required" }
        val cleanName = rawName.trim().trim('"', '\'').trim()
        val charset =
            if (cleanName.isNotBlank() &&
                cleanName.length <= MAX_CHARSET_NAME &&
                cleanName.none(Char::isISOControl) &&
                runCatching { Charset.isSupported(cleanName) }.getOrDefault(false)
            ) {
                Charset.forName(cleanName)
            } else {
                Charsets.UTF_8
            }
        val text = Base64.getDecoder().decode(encoded).toString(charset)
        require(text.length <= MAX_TEXT_CHARS) { "LNReader decoded text is too large" }
        return json.encodeToString(mapOf("text" to text))
    }

    private companion object {
        const val MAX_BASE64_BYTES = 700 * 1024
        const val MAX_CHARSET_NAME = 64
        const val MAX_TEXT_CHARS = 512 * 1024
    }
}
