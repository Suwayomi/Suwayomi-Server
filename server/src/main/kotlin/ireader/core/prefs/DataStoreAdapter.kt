package ireader.core.prefs

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Base adapter interface for DataStore preference types.
 * Handles type-specific serialization/deserialization for DataStore preferences.
 */
interface DataStoreAdapter<T> {
    /**
     * Get the DataStore preferences key for the given key string.
     */
    fun getKey(key: String): Preferences.Key<*>

    /**
     * Get the value from preferences, returning the default value if not present or on error.
     */
    fun get(key: String, preferences: Preferences, defaultValue: T): T

    /**
     * Create a key-value pair for setting in DataStore.
     */
    fun set(key: String, value: T): Pair<Preferences.Key<*>, Any>
}

/**
 * Adapter for String preferences.
 */
internal object DataStoreStringAdapter : DataStoreAdapter<String> {
    override fun getKey(key: String): Preferences.Key<String> {
        return stringPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: String): String {
        return try {
            preferences[stringPreferencesKey(key)] ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun set(key: String, value: String): Pair<Preferences.Key<String>, String> {
        return Pair(stringPreferencesKey(key), value)
    }
}

/**
 * Adapter for Long preferences.
 */
internal object DataStoreLongAdapter : DataStoreAdapter<Long> {
    override fun getKey(key: String): Preferences.Key<Long> {
        return longPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: Long): Long {
        return try {
            preferences[longPreferencesKey(key)] ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun set(key: String, value: Long): Pair<Preferences.Key<Long>, Long> {
        return Pair(longPreferencesKey(key), value)
    }
}

/**
 * Adapter for Int preferences.
 */
internal object DataStoreIntAdapter : DataStoreAdapter<Int> {
    override fun getKey(key: String): Preferences.Key<Int> {
        return intPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: Int): Int {
        return try {
            preferences[intPreferencesKey(key)] ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun set(key: String, value: Int): Pair<Preferences.Key<Int>, Int> {
        return Pair(intPreferencesKey(key), value)
    }
}

/**
 * Adapter for Float preferences.
 */
internal object DataStoreFloatAdapter : DataStoreAdapter<Float> {
    override fun getKey(key: String): Preferences.Key<Float> {
        return floatPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: Float): Float {
        return try {
            preferences[floatPreferencesKey(key)] ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun set(key: String, value: Float): Pair<Preferences.Key<Float>, Float> {
        return Pair(floatPreferencesKey(key), value)
    }
}

/**
 * Adapter for Boolean preferences.
 */
internal object DataStoreBooleanAdapter : DataStoreAdapter<Boolean> {
    override fun getKey(key: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: Boolean): Boolean {
        return try {
            preferences[booleanPreferencesKey(key)] ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun set(key: String, value: Boolean): Pair<Preferences.Key<Boolean>, Boolean> {
        return Pair(booleanPreferencesKey(key), value)
    }
}

/**
 * Adapter for Set<String> preferences.
 */
internal object DataStoreStringSetAdapter : DataStoreAdapter<Set<String>> {
    override fun getKey(key: String): Preferences.Key<Set<String>> {
        return stringSetPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: Set<String>): Set<String> {
        return try {
            preferences[stringSetPreferencesKey(key)] ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun set(key: String, value: Set<String>): Pair<Preferences.Key<Set<String>>, Set<String>> {
        return Pair(stringSetPreferencesKey(key), value)
    }
}

/**
 * Adapter for custom objects with serializer/deserializer functions.
 * Stores objects as serialized strings.
 */
internal class DataStoreObjectAdapter<T>(
    private val serializer: (T) -> String,
    private val deserializer: (String) -> T
) : DataStoreAdapter<T> {
    override fun getKey(key: String): Preferences.Key<String> {
        return stringPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: T): T {
        return try {
            val serialized = preferences[stringPreferencesKey(key)]
            if (serialized != null) {
                deserializer(serialized)
            } else {
                defaultValue
            }
        } catch (e: Exception) {
            // Log error and return default value
            defaultValue
        }
    }

    override fun set(key: String, value: T): Pair<Preferences.Key<String>, String> {
        return try {
            Pair(stringPreferencesKey(key), serializer(value))
        } catch (e: Exception) {
            // If serialization fails, store empty string
            Pair(stringPreferencesKey(key), "")
        }
    }
}

/**
 * Adapter for JSON-serializable objects using kotlinx.serialization.
 * Stores objects as JSON strings.
 */
internal class DataStoreJsonObjectAdapter<T>(
    private val defaultValue: T,
    private val serializer: KSerializer<T>,
    private val serializersModule: SerializersModule = EmptySerializersModule()
) : DataStoreAdapter<T> {
    private val json = Json {
        this.serializersModule = this@DataStoreJsonObjectAdapter.serializersModule
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun getKey(key: String): Preferences.Key<String> {
        return stringPreferencesKey(key)
    }

    override fun get(key: String, preferences: Preferences, defaultValue: T): T {
        return try {
            val jsonString = preferences[stringPreferencesKey(key)]
            if (jsonString != null && jsonString.isNotEmpty()) {
                json.decodeFromString(serializer, jsonString)
            } else {
                defaultValue
            }
        } catch (e: SerializationException) {
            // JSON parsing failed, return default
            defaultValue
        } catch (e: Exception) {
            // Any other error, return default
            defaultValue
        }
    }

    override fun set(key: String, value: T): Pair<Preferences.Key<String>, String> {
        return try {
            val jsonString = json.encodeToString(serializer, value)
            Pair(stringPreferencesKey(key), jsonString)
        } catch (e: SerializationException) {
            // If serialization fails, store empty string
            Pair(stringPreferencesKey(key), "")
        } catch (e: Exception) {
            // Any other error, store empty string
            Pair(stringPreferencesKey(key), "")
        }
    }
}
