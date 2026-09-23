package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import suwayomi.tachidesk.manga.impl.extension.lnreader.LnReaderRepository.sourceId
import java.util.concurrent.ConcurrentHashMap

class LnPluginStorage(
    private val preferences: SharedPreferences,
    private val json: Json = Json { encodeDefaults = true },
    private val webStorageOrigin: String? = null,
) {
    @Serializable
    data class Item(
        val created: Long,
        val value: JsonElement,
        val expires: Long? = null,
    )

    @Serializable
    private data class ReadResult(
        val present: Boolean,
        val value: JsonElement = JsonNull,
        val created: Long? = null,
        val expires: Long? = null,
    )

    private val declaredSettings = ConcurrentHashMap<String, String>()

    fun registerSetting(
        key: String,
        type: String,
    ) {
        declaredSettings[key] = type
    }

    private fun isDeclaredKey(key: String): Boolean =
        declaredSettings.containsKey(key) ||
            (preferences.contains(key) && !key.startsWith(STORAGE_PREFIX) && !key.startsWith(WEB_STORAGE_PREFIX))

    fun invoke(
        operation: String,
        requestJson: String,
    ): String {
        require(requestJson.length <= MAX_REQUEST_BYTES) { "LNReader storage request is too large" }
        if (operation == "storage.registerDeclaredSettings") {
            json.parseToJsonElement(requestJson).jsonObject.forEach { (k, v) -> registerSetting(k, v.jsonPrimitive.content) }
            return "null"
        }
        val req = json.parseToJsonElement(requestJson).jsonObject
        return when (operation) {
            "storage.get" -> read(requireKey(req), req["raw"]?.jsonPrimitive?.booleanOrNull == true)
            "storage.set" -> write(req)
            "storage.delete" -> delete(requireKey(req))
            "storage.keys" -> json.encodeToString(keys())
            "storage.clear" -> clear()
            "webStorage.local" -> snapshot(LOCAL_STORAGE_KEY, webStorageOrigin)
            "webStorage.session" -> snapshot(SESSION_STORAGE_KEY, webStorageOrigin)
            else -> throw UnsupportedOperationException("Unsupported LNReader storage operation '$operation'")
        }
    }

    fun read(
        key: String,
        raw: Boolean = false,
    ): String {
        val (created, value, expires) =
            if (isDeclaredKey(key) && preferences.contains(key)) {
                val v =
                    when (val value = preferences.all[key]) {
                        is Boolean -> JsonPrimitive(value)
                        is String -> JsonPrimitive(value)
                        is Set<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) })
                        else -> JsonNull
                    }
                Triple(0L, v, null)
            } else {
                val it = item(key) ?: return json.encodeToString(ReadResult(false))
                Triple(it.created, it.value, it.expires)
            }
        val v =
            if (raw) {
                JsonObject(
                    mapOf(
                        "created" to JsonPrimitive(created),
                        "value" to value,
                        "expires" to (expires?.let(::JsonPrimitive) ?: JsonNull),
                    ),
                )
            } else {
                value
            }
        return json.encodeToString(ReadResult(true, v, created, expires))
    }

    fun write(request: JsonObject): String {
        val key = requireKey(request)
        val value = requireNotNull(request["value"]) { "LNReader storage value is required" }

        if (isDeclaredKey(key) || declaredSettings.containsKey(key)) {
            val editor = preferences.edit()
            when (declaredSettings[key]) {
                "Switch" -> {
                    editor.putBoolean(key, value.jsonPrimitive.boolean)
                }

                "CheckboxGroup" -> {
                    editor.putStringSet(key, (value as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet())
                }

                else -> {
                    when {
                        value is JsonPrimitive && value.booleanOrNull != null -> editor.putBoolean(key, value.boolean)
                        value is JsonArray -> editor.putStringSet(key, value.map { it.jsonPrimitive.content }.toSet())
                        else -> editor.putString(key, value.jsonPrimitive.content)
                    }
                }
            }
            editor.apply()
            return "null"
        }

        val encoded = json.encodeToString(Item(System.currentTimeMillis(), value, request["expires"]?.jsonPrimitive?.longOrNull))
        require(encoded.length <= MAX_ITEM_BYTES) { "LNReader storage value is too large" }
        preferences.edit().putString(storageKey(key), encoded).apply()
        return "null"
    }

    fun delete(key: String): String {
        preferences
            .edit()
            .remove(storageKey(key))
            .apply { if (isDeclaredKey(key)) remove(key) }
            .apply()
        return "null"
    }

    fun keys(): List<String> =
        preferences.all.keys
            .filter { it.startsWith(STORAGE_PREFIX) }
            .map { it.removePrefix(STORAGE_PREFIX) }
            .filter { item(it) != null }
            .sorted()

    fun clear(): String {
        preferences.edit().apply { keys().forEach { remove(storageKey(it)) } }.apply()
        return "null"
    }

    fun snapshot(key: String): String = snapshot(key, webStorageOrigin)

    private fun snapshot(
        key: String,
        expectedOrigin: String?,
    ): String {
        if (expectedOrigin != null &&
            (expectedOrigin.isBlank() || preferences.getString(WEB_STORAGE_ORIGIN_KEY, null) != expectedOrigin)
        ) {
            return "null"
        }
        return preferences
            .getString(key, null)
            ?.let(json::parseToJsonElement)
            ?.also {
                require(it is JsonObject || it is JsonNull) { "Invalid LNReader Web Storage snapshot" }
            }?.let(json::encodeToString) ?: "null"
    }

    fun saveWebStorageSnapshots(
        local: JsonObject?,
        session: JsonObject?,
    ) {
        preferences
            .edit()
            .putString(WEB_STORAGE_ORIGIN_KEY, webStorageOrigin.orEmpty())
            .putString(LOCAL_STORAGE_KEY, json.encodeToString(local ?: JsonObject(emptyMap())))
            .putString(SESSION_STORAGE_KEY, json.encodeToString(session ?: JsonObject(emptyMap())))
            .apply()
    }

    private fun item(key: String): Item? {
        val item =
            preferences.getString(storageKey(key), null)?.let {
                runCatching { json.decodeFromString<Item>(it) }.getOrNull()
            } ?: return null
        if (item.expires != null && System.currentTimeMillis() > item.expires) {
            preferences.edit().remove(storageKey(key)).apply()
            return null
        }
        return item
    }

    private fun requireKey(request: JsonObject): String =
        requireKey(requireNotNull(request["key"]?.jsonPrimitive?.contentOrNull) { "LNReader storage key is required" })

    private fun requireKey(key: String): String =
        key.also {
            require(it.isNotEmpty() && it.length <= MAX_KEY_LENGTH && it.none(Char::isISOControl)) { "Invalid LNReader storage key" }
        }

    private fun storageKey(key: String): String = STORAGE_PREFIX + requireKey(key)

    companion object {
        private const val STORAGE_PREFIX = "__lnkv."
        private const val WEB_STORAGE_PREFIX = "__lnweb."
        private const val WEB_STORAGE_ORIGIN_KEY = "__lnweb.origin"
        private const val LOCAL_STORAGE_KEY = "__lnweb.local"
        private const val SESSION_STORAGE_KEY = "__lnweb.session"
        private const val MAX_KEY_LENGTH = 256
        private const val MAX_REQUEST_BYTES = 128 * 1024
        private const val MAX_ITEM_BYTES = 128 * 1024

        fun forPlugin(
            pluginId: String,
            webStorageOrigin: String = "",
        ): LnPluginStorage =
            LnPluginStorage(
                sourcePreferences("source_${sourceId(pluginId)}"),
                webStorageOrigin = webStorageOrigin,
            )
    }
}
