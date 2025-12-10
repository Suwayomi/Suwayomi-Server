package ireader.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import ireader.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * DataStore-backed implementation of the Preference interface.
 * 
 * @param dataStore The DataStore instance to read/write preferences
 * @param key The preference key
 * @param defaultValue The default value if preference is not set
 * @param adapter The adapter to handle type-specific operations
 */
internal class DataStorePreference<T>(
    private val dataStore: DataStore<Preferences>,
    private val key: String,
    private val defaultValue: T,
    private val adapter: DataStoreAdapter<T>
) : Preference<T> {

    /**
     * Returns the key of this preference.
     */
    override fun key(): String = key

    /**
     * Returns the current value of this preference synchronously.
     * Uses runBlocking for compatibility with existing synchronous code.
     */
    override fun get(): T {
        return runBlocking {
            try {
                dataStore.data
                    .map { preferences ->
                        adapter.get(key, preferences, defaultValue)
                    }
                    .catch { exception ->
                        // Handle CorruptionException and IOException
                        if (exception is IOException) {
                            emit(defaultValue)
                        } else {
                            throw exception
                        }
                    }
                    .first()
            } catch (e: Exception) {
                // Return default value on any error
                defaultValue
            }
        }
    }

    /**
     * Sets a new value for this preference asynchronously.
     */
    override fun set(value: T) {
        runBlocking {
            try {
                dataStore.edit { preferences ->
                    val (prefKey, prefValue) = adapter.set(key, value)
                    @Suppress("UNCHECKED_CAST")
                    preferences[prefKey as Preferences.Key<Any>] = prefValue
                }
            } catch (e: IOException) {
                // Log error but don't crash
                Log.error(e, "IOException while setting preference '{}' to value '{}'", key, value)
            } catch (e: Exception) {
                // Handle other exceptions
                Log.error(e, "Unexpected exception while setting preference '{}' to value '{}'", key, value)
            }
        }
    }

    /**
     * Returns whether there's an existing entry for this preference.
     */
    override fun isSet(): Boolean {
        return runBlocking {
            try {
                dataStore.data
                    .map { preferences ->
                        preferences.contains(adapter.getKey(key))
                    }
                    .catch { emit(false) }
                    .first()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Deletes the entry of this preference.
     */
    override fun delete() {
        runBlocking {
            try {
                dataStore.edit { preferences ->
                    preferences.remove(adapter.getKey(key))
                }
            } catch (e: IOException) {
                // Log error but don't crash
                Log.error(e, "IOException while deleting preference '{}'", key)
            } catch (e: Exception) {
                // Handle other exceptions
                Log.error(e, "Unexpected exception while deleting preference '{}'", key)
            }
        }
    }

    /**
     * Returns the default value of this preference.
     */
    override fun defaultValue(): T = defaultValue

    /**
     * Returns a cold Flow of this preference to receive updates when its value changes.
     */
    override fun changes(): Flow<T> {
        return dataStore.data
            .map { preferences ->
                adapter.get(key, preferences, defaultValue)
            }
            .catch { exception ->
                // Handle CorruptionException and IOException
                if (exception is IOException) {
                    emit(defaultValue)
                } else {
                    throw exception
                }
            }
    }

    /**
     * Returns a hot StateFlow of this preference bound to the given scope.
     */
    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return changes().stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = get()
        )
    }
}
