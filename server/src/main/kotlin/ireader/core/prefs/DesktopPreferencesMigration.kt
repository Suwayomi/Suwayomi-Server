package ireader.core.prefs

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.prefs.BackingStoreException

/**
 * Migrates Java Preferences data to DataStore on Desktop platforms.
 * 
 * This migration runs once when the app first uses DataStore. It reads all existing
 * Java Preferences and copies them to DataStore, attempting to preserve all preference types.
 * 
 * @param oldPreferences Java Preferences node to migrate from
 * @param nodePath Path of the preferences node for logging purposes
 */
class DesktopPreferencesMigration(
    private val oldPreferences: java.util.prefs.Preferences,
    private val nodePath: String
) : DataMigration<Preferences> {

    companion object {
        private const val MIGRATION_COMPLETED_KEY = "migration_completed"
    }

    /**
     * Checks if migration should run by looking for the migration_completed flag.
     * 
     * @param currentData Current DataStore preferences
     * @return true if migration has not been completed yet
     */
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val shouldMigrate = !currentData.contains(stringPreferencesKey(MIGRATION_COMPLETED_KEY))
        if (shouldMigrate) {
            println("DesktopPreferencesMigration: Migration needed for node: $nodePath")
        } else {
            println("DesktopPreferencesMigration: Migration already completed for node: $nodePath")
        }
        return shouldMigrate
    }

    /**
     * Migrates all Java Preferences data to DataStore.
     * 
     * Reads all key-value pairs from Java Preferences and copies them to DataStore.
     * Since Java Preferences doesn't have strong typing, we attempt to parse values
     * as different types. Errors for individual keys are logged but don't stop the migration.
     * 
     * @param currentData Current DataStore preferences (should be empty on first run)
     * @return Updated preferences with migrated data and migration_completed flag
     */
    override suspend fun migrate(currentData: Preferences): Preferences {
        println("DesktopPreferencesMigration: Starting migration for node: $nodePath")
        
        val keys: Array<String> = try {
            oldPreferences.keys()
        } catch (e: BackingStoreException) {
            println("DesktopPreferencesMigration: Failed to read keys from Java Preferences: ${e.message}")
            // Mark migration as complete even if we can't access old prefs
            return currentData.toMutablePreferences().apply {
                this[stringPreferencesKey(MIGRATION_COMPLETED_KEY)] = "true"
            }.toPreferences()
        }

        if (keys.isEmpty()) {
            println("DesktopPreferencesMigration: No preferences to migrate from node: $nodePath")
        } else {
            println("DesktopPreferencesMigration: Migrating ${keys.size} preferences from node: $nodePath")
        }

        val mutablePrefs = currentData.toMutablePreferences()
        var successCount = 0
        var errorCount = 0

        keys.forEach { key ->
            try {
                // Java Preferences stores everything as strings, so we need to try parsing
                val stringValue = oldPreferences.get(key, null)
                
                if (stringValue != null) {
                    // Try to determine the type and migrate accordingly
                    when {
                        // Try boolean
                        stringValue.equals("true", ignoreCase = true) || 
                        stringValue.equals("false", ignoreCase = true) -> {
                            val boolValue = oldPreferences.getBoolean(key, false)
                            mutablePrefs[booleanPreferencesKey(key)] = boolValue
                            println("DesktopPreferencesMigration: Migrated Boolean: $key = $boolValue")
                        }
                        // Try int
                        stringValue.toIntOrNull() != null -> {
                            val intValue = oldPreferences.getInt(key, 0)
                            mutablePrefs[intPreferencesKey(key)] = intValue
                            println("DesktopPreferencesMigration: Migrated Int: $key = $intValue")
                        }
                        // Try long
                        stringValue.toLongOrNull() != null -> {
                            val longValue = oldPreferences.getLong(key, 0L)
                            mutablePrefs[longPreferencesKey(key)] = longValue
                            println("DesktopPreferencesMigration: Migrated Long: $key = $longValue")
                        }
                        // Try float
                        stringValue.toFloatOrNull() != null -> {
                            val floatValue = oldPreferences.getFloat(key, 0f)
                            mutablePrefs[floatPreferencesKey(key)] = floatValue
                            println("DesktopPreferencesMigration: Migrated Float: $key = $floatValue")
                        }
                        // Default to string
                        else -> {
                            mutablePrefs[stringPreferencesKey(key)] = stringValue
                            println("DesktopPreferencesMigration: Migrated String: $key")
                        }
                    }
                    successCount++
                } else {
                    println("DesktopPreferencesMigration: Skipping null value for key: $key")
                    errorCount++
                }
            } catch (e: Exception) {
                println("DesktopPreferencesMigration: Failed to migrate preference: $key - ${e.message}")
                errorCount++
            }
        }

        // Mark migration as complete
        mutablePrefs[stringPreferencesKey(MIGRATION_COMPLETED_KEY)] = "true"
        
        println("DesktopPreferencesMigration: Migration completed for $nodePath: $successCount successful, $errorCount errors")
        
        return mutablePrefs.toPreferences()
    }

    /**
     * Cleanup after migration. Currently does nothing to preserve old Java Preferences
     * as a backup in case of issues.
     */
    override suspend fun cleanUp() {
        println("DesktopPreferencesMigration: Migration cleanup completed for node: $nodePath")
        // Intentionally not deleting old Java Preferences to keep as backup
        // Users can manually clear preferences if they want to remove old data
    }
}
