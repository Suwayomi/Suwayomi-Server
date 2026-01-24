package ireader.core.prefs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

/**
 * Interface for IReader's PreferenceStore.
 * Must be in ireader.core.prefs package to match extension expectations.
 * 
 * This interface follows the same contract as the original IReader PreferenceStore,
 * allowing extensions to store and retrieve preferences.
 */
interface PreferenceStore {
    /**
     * Get a string preference.
     */
    fun getString(key: String, defaultValue: String = ""): Preference<String>

    /**
     * Get a long preference.
     */
    fun getLong(key: String, defaultValue: Long = 0): Preference<Long>

    /**
     * Get an integer preference.
     */
    fun getInt(key: String, defaultValue: Int = 0): Preference<Int>

    /**
     * Get a float preference.
     */
    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float>

    /**
     * Get a boolean preference.
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean>

    /**
     * Get a string set preference.
     */
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>>

    /**
     * Get an object preference with custom serialization.
     */
    fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T>

    /**
     * Get an object preference with JSON serialization.
     */
    fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule,
    ): Preference<T>
}
