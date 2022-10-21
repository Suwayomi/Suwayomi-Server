package xyz.nulldev.androidcompat.io.sharedprefs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.SharedPreferences
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import java.util.prefs.PreferenceChangeListener
import java.util.prefs.Preferences

@OptIn(ExperimentalSettingsImplementation::class, ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
class JavaSharedPreferences(key: String) : SharedPreferences {
    private val javaPreferences = Preferences.userRoot().node("suwayomi/tachidesk/$key")
    private val preferences = PreferencesSettings(javaPreferences)
    private val listeners = mutableMapOf<SharedPreferences.OnSharedPreferenceChangeListener, PreferenceChangeListener>()

    // TODO: 2021-05-29 Need to find a way to get this working with all pref types
    override fun getAll(): MutableMap<String, *> {
        return preferences.keys.associateWith { preferences.getStringOrNull(it) }.toMutableMap()
    }

    override fun getString(key: String, defValue: String?): String? {
        return if (defValue != null) {
            preferences.getString(key, defValue)
        } else {
            preferences.getStringOrNull(key)
        }
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
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

    override fun getInt(key: String, defValue: Int): Int {
        return preferences.getInt(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return preferences.getLong(key, defValue)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return preferences.getFloat(key, defValue)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return preferences.getBoolean(key, defValue)
    }

    override fun contains(key: String): Boolean {
        return key in preferences.keys
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor(preferences)
    }

    class Editor(private val preferences: PreferencesSettings) : SharedPreferences.Editor {
        val itemsToAdd = mutableMapOf<String, Any>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value != null) {
                itemsToAdd[key] = value
            } else {
                remove(key)
            }
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            if (values != null) {
                itemsToAdd[key] = values
            } else {
                remove(key)
            }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            itemsToAdd[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            itemsToAdd[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            itemsToAdd[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            itemsToAdd[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            itemsToAdd.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            itemsToAdd.clear()
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
            itemsToAdd.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Set<*> -> preferences.encodeValue(SetSerializer(String.serializer()), key, value as Set<String>)
                    is String -> preferences.putString(key, value)
                    is Int -> preferences.putInt(key, value)
                    is Long -> preferences.putLong(key, value)
                    is Float -> preferences.putFloat(key, value)
                    is Double -> preferences.putDouble(key, value)
                    is Boolean -> preferences.putBoolean(key, value)
                }
            }
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val javaListener = PreferenceChangeListener {
            listener.onSharedPreferenceChanged(this, it.key)
        }
        listeners[listener] = javaListener
        javaPreferences.addPreferenceChangeListener(javaListener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val registeredListener = listeners.remove(listener)
        if (registeredListener != null) {
            javaPreferences.removePreferenceChangeListener(registeredListener)
        }
    }

    fun deleteAll(): Boolean {
        javaPreferences.removeNode()
        return true
    }
}
