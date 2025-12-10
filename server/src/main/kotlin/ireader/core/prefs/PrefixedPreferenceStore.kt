/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.prefs

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

/**
 * An implementation of a [PreferenceStore] that writes to a [prefix]ed key, allowing to share a
 * single [commonStore] with many consumers.
 */
class PrefixedPreferenceStore(
    private val commonStore: PreferenceStore,
    private val prefix: String
) : PreferenceStore {

    /**
     * Returns a [String] preference for this [key].
     */
    override fun getString(key: String, defaultValue: String): Preference<String> {
        return commonStore.getString(prefix + key, defaultValue)
    }

    /**
     * Returns a [Long] preference for this [key].
     */
    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return commonStore.getLong(prefix + key, defaultValue)
    }

    /**
     * Returns an [Int] preference for this [key].
     */
    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return commonStore.getInt(prefix + key, defaultValue)
    }

    /**
     * Returns a [Float] preference for this [key].
     */
    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return commonStore.getFloat(prefix + key, defaultValue)
    }

    /**
     * Returns a [Boolean] preference for this [key].
     */
    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return commonStore.getBoolean(prefix + key, defaultValue)
    }

    /**
     * Returns a [Set<String>] preference for this [key].
     */
    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return commonStore.getStringSet(prefix + key, defaultValue)
    }

    /**
     * Returns a preference of type [T] for this [key]. The [serializer] and [deserializer] function
     * must be provided.
     */
    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T
    ): Preference<T> {
        return commonStore.getObject(prefix + key, defaultValue, serializer, deserializer)
    }

    override fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule
    ): Preference<T> {
        return commonStore.getJsonObject(prefix + key, defaultValue, serializer, serializersModule)
    }
}
