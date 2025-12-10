package ireader.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * DataStore-backed implementation of the PreferenceStore interface.
 * 
 * This implementation uses androidx.datastore.preferences to store application preferences
 * with type safety, coroutine support, and Flow-based observability.
 * 
 * @param dataStore The DataStore instance to use for storing preferences
 */
class DataStorePreferenceStore(
    private val dataStore: DataStore<Preferences>
) : PreferenceStore {

    /**
     * Returns a String preference for this key.
     */
    override fun getString(key: String, defaultValue: String): Preference<String> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreStringAdapter
        )
    }

    /**
     * Returns a Long preference for this key.
     */
    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreLongAdapter
        )
    }

    /**
     * Returns an Int preference for this key.
     */
    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreIntAdapter
        )
    }

    /**
     * Returns a Float preference for this key.
     */
    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreFloatAdapter
        )
    }

    /**
     * Returns a Boolean preference for this key.
     */
    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreBooleanAdapter
        )
    }

    /**
     * Returns a Set<String> preference for this key.
     */
    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreStringSetAdapter
        )
    }

    /**
     * Returns a preference of type T for this key using custom serializer/deserializer functions.
     */
    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T
    ): Preference<T> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreObjectAdapter(serializer, deserializer)
        )
    }

    /**
     * Returns a preference of type T for this key using kotlinx.serialization.
     */
    override fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule
    ): Preference<T> {
        return DataStorePreference(
            dataStore = dataStore,
            key = key,
            defaultValue = defaultValue,
            adapter = DataStoreJsonObjectAdapter(
                defaultValue = defaultValue,
                serializer = serializer,
                serializersModule = serializersModule
            )
        )
    }
}
