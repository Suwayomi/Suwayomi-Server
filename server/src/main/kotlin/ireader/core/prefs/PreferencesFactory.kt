package ireader.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Desktop implementation of PreferenceStoreFactory that creates DataStore-backed PreferenceStore instances.
 *
 * This factory creates DataStore instances with automatic migration from Java Preferences.
 * Each PreferenceStore is backed by a separate DataStore file in the user's home directory.
 *
 * DataStore files are stored in: ~/.ireader/datastore/
 */
class PreferenceStoreFactory {

    private val rootPath = System.getProperty("user.home") + "/.ireader"
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val javaPrefsRootNode: java.util.prefs.Preferences = java.util.prefs.Preferences.userRoot()
        .node("org/ireader")

    /**
     * Creates a PreferenceStore backed by DataStore with automatic migration from Java Preferences.
     *
     * The DataStore file is created in ~/.ireader/datastore/ directory.
     * If Java Preferences exist with the same path, they will be automatically migrated on first access.
     *
     * @param names Variable number of name components that will be joined with underscores for the file name
     * @return A DataStore-backed PreferenceStore instance
     */
    fun create(vararg names: String): PreferenceStore {
        val preferenceName = names.joinToString(separator = "_")
        val nodePath = names.joinToString(separator = "/")
        val oldPrefsNode = javaPrefsRootNode.node(nodePath)

        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            migrations = listOf(
                DesktopPreferencesMigration(
                    oldPreferences = oldPrefsNode,
                    nodePath = nodePath
                )
            ),
            scope = dataStoreScope,
            produceFile = {
                val datastoreDir = File(rootPath, "datastore")
                datastoreDir.mkdirs()
                File(datastoreDir, "$preferenceName.preferences_pb")
            }
        )

        return DataStorePreferenceStore(dataStore)
    }
}
