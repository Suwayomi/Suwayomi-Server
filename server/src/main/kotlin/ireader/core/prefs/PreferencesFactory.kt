package ireader.core.prefs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import xyz.nulldev.androidcompat.io.sharedprefs.JavaSharedPreferences

/**
 * Factory for creating IReader PreferenceStore instances.
 * 
 * Uses Tachidesk's SharedPreferences implementation (JavaSharedPreferences)
 * instead of DataStore for consistency with the rest of the codebase.
 */
class PreferenceStoreFactory {
    /**
     * Creates a PreferenceStore backed by SharedPreferences.
     *
     * @param names Variable number of name components that will be joined with underscores for the preference name
     * @return A SharedPreferences-backed PreferenceStore instance
     */
    fun create(vararg names: String): PreferenceStore {
        val preferenceName = "ireader_" + names.joinToString(separator = "_")
        val sharedPreferences = JavaSharedPreferences(preferenceName)
        return SharedPreferencesStore(sharedPreferences)
    }
}

/**
 * PreferenceStore implementation backed by Android SharedPreferences.
 * Uses Tachidesk's JavaSharedPreferences which stores data in XML files.
 */
private class SharedPreferencesStore(
    private val prefs: SharedPreferences,
) : PreferenceStore {
    override fun getString(key: String, defaultValue: String): Preference<String> =
        SharedPreferenceImpl(key, defaultValue, prefs)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        SharedPreferenceImpl(key, defaultValue, prefs)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        SharedPreferenceImpl(key, defaultValue, prefs)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        SharedPreferenceImpl(key, defaultValue, prefs)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        SharedPreferenceImpl(key, defaultValue, prefs)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        SharedPreferenceImpl(key, defaultValue, prefs)

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = ObjectPreferenceImpl(key, defaultValue, serializer, deserializer, prefs)

    override fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule,
    ): Preference<T> {
        val json = Json { this.serializersModule = serializersModule }
        return ObjectPreferenceImpl(
            key,
            defaultValue,
            { json.encodeToString(serializer, it) },
            { json.decodeFromString(serializer, it) },
            prefs,
        )
    }
}

/**
 * Preference implementation backed by SharedPreferences.
 */
private class SharedPreferenceImpl<T>(
    private val key: String,
    private val defaultValue: T,
    private val prefs: SharedPreferences,
) : Preference<T> {
    override fun key() = key

    override fun defaultValue() = defaultValue

    @Suppress("UNCHECKED_CAST")
    override fun get(): T =
        when (defaultValue) {
            is String -> prefs.getString(key, defaultValue as String) as T
            is Int -> prefs.getInt(key, defaultValue as Int) as T
            is Long -> prefs.getLong(key, defaultValue as Long) as T
            is Float -> prefs.getFloat(key, defaultValue as Float) as T
            is Boolean -> prefs.getBoolean(key, defaultValue as Boolean) as T
            is Set<*> -> prefs.getStringSet(key, defaultValue as Set<String>) as T
            else -> defaultValue
        }

    override fun set(value: T) {
        prefs.edit().apply {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                is Set<*> -> putStringSet(key, value as Set<String>)
            }
            apply()
        }
    }

    override fun isSet(): Boolean = prefs.contains(key)

    override fun delete() {
        prefs.edit().remove(key).apply()
    }

    override fun changes(): Flow<T> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    trySend(get())
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(get())
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}

/**
 * Object preference implementation with custom serialization.
 */
private class ObjectPreferenceImpl<T>(
    private val key: String,
    private val defaultValue: T,
    private val serializer: (T) -> String,
    private val deserializer: (String) -> T,
    private val prefs: SharedPreferences,
) : Preference<T> {
    override fun key() = key

    override fun defaultValue() = defaultValue

    override fun get(): T {
        val stored = prefs.getString(key, null)
        return if (stored != null) {
            try {
                deserializer(stored)
            } catch (e: Exception) {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    override fun set(value: T) {
        prefs.edit().putString(key, serializer(value)).apply()
    }

    override fun isSet(): Boolean = prefs.contains(key)

    override fun delete() {
        prefs.edit().remove(key).apply()
    }

    override fun changes(): Flow<T> =
        callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    trySend(get())
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            send(get())
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}
