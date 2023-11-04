package xyz.nulldev.androidcompat.io.sharedprefs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.SharedPreferences
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import mu.KotlinLogging
import xyz.nulldev.androidcompat.util.SafePath
import xyz.nulldev.ts.config.ApplicationRootDir
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
class JavaSharedPreferences(key: String) : SharedPreferences {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val file =
        Path(
            ApplicationRootDir,
            "settings",
            "${SafePath.buildValidFilename(key)}.xml",
        )
    private val properties =
        Properties().also { properties ->
            try {
                if (file.exists()) {
                    file.inputStream().use { properties.loadFromXML(it) }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error loading settings from $key" }
            }
        }
    private val preferences =
        PropertiesSettings(
            properties,
            onModify = { properties ->
                try {
                    if (properties.isEmpty) {
                        file.deleteIfExists()
                    } else {
                        file.createParentDirectories()
                        file.outputStream().use {
                            properties.storeToXML(it, null)
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error saving settings in $key" }
                }
            },
        )
    private val listeners = mutableMapOf<SharedPreferences.OnSharedPreferenceChangeListener, (String) -> Unit>()

    // TODO: 2021-05-29 Need to find a way to get this working with all pref types
    override fun getAll(): MutableMap<String, *> {
        return preferences.keys.associateWith { preferences.getStringOrNull(it) }.toMutableMap()
    }

    override fun getString(
        key: String,
        defValue: String?,
    ): String? {
        return if (defValue != null) {
            preferences.getString(key, defValue)
        } else {
            preferences.getStringOrNull(key)
        }
    }

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? {
        try {
            return if (defValues != null) {
                preferences.decodeValue(SetSerializer(String.serializer()), key, defValues)
            } else {
                preferences.decodeValueOrNull(SetSerializer(String.serializer()), key)
            }
        } catch (e: SerializationException) {
            throw ClassCastException("$key was not a StringSet")
        }
    }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int {
        return preferences.getInt(key, defValue)
    }

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long {
        return preferences.getLong(key, defValue)
    }

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float {
        return preferences.getFloat(key, defValue)
    }

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean {
        return preferences.getBoolean(key, defValue)
    }

    override fun contains(key: String): Boolean {
        return key in preferences.keys
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor(preferences) { key ->
            listeners.forEach { (_, listener) ->
                listener(key)
            }
        }
    }

    class Editor(private val preferences: Settings, private val notify: (String) -> Unit) : SharedPreferences.Editor {
        private val actions = mutableListOf<Action>()

        private sealed class Action {
            data class Add(val key: String, val value: Any) : Action()

            data class Remove(val key: String) : Action()
            data object Clear : Action()
        }

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            if (value != null) {
                actions += Action.Add(key, value)
            } else {
                actions += Action.Remove(key)
            }
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            if (values != null) {
                actions += Action.Add(key, values)
            } else {
                actions += Action.Remove(key)
            }
            return this
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            actions += Action.Remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            actions.add(Action.Clear)
            return this
        }

        override fun commit(): Boolean {
            addToPreferences()
            return true
        }

        override fun apply() {
            addToPreferences()
        }

        private fun addToPreferences() {
            actions.forEach {
                @Suppress("UNCHECKED_CAST")
                when (it) {
                    is Action.Add -> {
                        when (val value = it.value) {
                            is Set<*> -> preferences.encodeValue(SetSerializer(String.serializer()), it.key, value as Set<String>)
                            is String -> preferences.putString(it.key, value)
                            is Int -> preferences.putInt(it.key, value)
                            is Long -> preferences.putLong(it.key, value)
                            is Float -> preferences.putFloat(it.key, value)
                            is Double -> preferences.putDouble(it.key, value)
                            is Boolean -> preferences.putBoolean(it.key, value)
                        }
                        notify(it.key)
                    }
                    is Action.Remove -> {
                        preferences.remove(it.key)
                        /**
                         * Set<String> are stored like
                         * key.0 = value1
                         * key.1 = value2
                         * key.size = 2
                         */
                        preferences.keys.forEach { key ->
                            if (key.startsWith(it.key + ".")) {
                                preferences.remove(key)
                            }
                        }

                        notify(it.key)
                    }
                    Action.Clear -> preferences.clear()
                }
            }
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val javaListener: (String) -> Unit = {
            listener.onSharedPreferenceChanged(this, it)
        }
        listeners[listener] = javaListener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    fun deleteAll(): Boolean {
        preferences.clear()
        return true
    }
}
