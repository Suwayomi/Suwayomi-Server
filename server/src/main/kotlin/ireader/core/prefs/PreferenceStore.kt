package ireader.core.prefs

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Implementation of IReader's PreferenceStore
 * Must be in ireader.core.prefs package to match extension expectations
 */
class PreferenceStoreImpl(
    private val packageName: String,
) : PreferenceStore {
    private val prefs = Injekt.get<Application>().getSharedPreferences("suwayomi/ireader/$packageName", Context.MODE_PRIVATE)

    override fun getString(
        key: String,
        defaultValue: String,
    ): Preference<String> = PreferenceImpl(key, defaultValue, prefs)

    override fun getLong(
        key: String,
        defaultValue: Long,
    ): Preference<Long> = PreferenceImpl(key, defaultValue, prefs)

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Preference<Int> = PreferenceImpl(key, defaultValue, prefs)

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ): Preference<Float> = PreferenceImpl(key, defaultValue, prefs)

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Preference<Boolean> = PreferenceImpl(key, defaultValue, prefs)

    override fun getStringSet(
        key: String,
        defaultValue: Set<String>,
    ): Preference<Set<String>> = PreferenceImpl(key, defaultValue, prefs)

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
    ): Preference<T> =
        ObjectPreferenceImpl(
            key,
            defaultValue,
            { it.toString() },
            { defaultValue },
            prefs,
        )
}

interface PreferenceStore {
    fun getString(
        key: String,
        defaultValue: String = "",
    ): Preference<String>

    fun getLong(
        key: String,
        defaultValue: Long = 0,
    ): Preference<Long>

    fun getInt(
        key: String,
        defaultValue: Int = 0,
    ): Preference<Int>

    fun getFloat(
        key: String,
        defaultValue: Float = 0f,
    ): Preference<Float>

    fun getBoolean(
        key: String,
        defaultValue: Boolean = false,
    ): Preference<Boolean>

    fun getStringSet(
        key: String,
        defaultValue: Set<String> = emptySet(),
    ): Preference<Set<String>>

    fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T>

    fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule,
    ): Preference<T>
}

// Preference interface is defined in Preference.kt

private class PreferenceImpl<T>(
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

    override fun changes(): Flow<T> {
        // Stub implementation - preference changes not supported on server
        return kotlinx.coroutines.flow.flowOf(get())
    }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        // Stub implementation - returns current value as StateFlow
        return MutableStateFlow(get()).asStateFlow()
    }
}

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

    override fun changes(): Flow<T> {
        // Stub implementation - preference changes not supported on server
        return kotlinx.coroutines.flow.flowOf(get())
    }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        // Stub implementation - returns current value as StateFlow
        return MutableStateFlow(get()).asStateFlow()
    }
}
