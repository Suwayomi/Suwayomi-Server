package suwayomi.tachidesk.server

import android.app.Application
import android.content.Context
import mu.KotlinLogging
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.prefs.Preferences

private fun migratePreferences(
    parent: String?,
    rootNode: Preferences,
) {
    val subNodes = rootNode.childrenNames()

    for (subNodeName in subNodes) {
        val subNode = rootNode.node(subNodeName)
        val key =
            if (parent != null) {
                "$parent/$subNodeName"
            } else {
                subNodeName
            }
        val preferences = Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)

        val items: Map<String, String?> =
            subNode.keys().associateWith {
                subNode[it, null]?.ifBlank { null }
            }

        preferences.edit().apply {
            items.forEach { (key, value) ->
                if (value != null) {
                    putString(key, value)
                }
            }
        }.apply()

        migratePreferences(key, subNode) // Recursively migrate sub-level nodes
    }
}

const val MIGRATION_VERSION = 1

fun runMigrations(applicationDirs: ApplicationDirs) {
    val migrationPreferences =
        Injekt.get<Application>()
            .getSharedPreferences(
                "migrations",
                Context.MODE_PRIVATE,
            )
    val version = migrationPreferences.getInt("version", 0)
    val logger = KotlinLogging.logger("Migration")
    logger.info { "Running migrations, previous version $version, target version $MIGRATION_VERSION" }

    if (version < 1) {
        logger.info { "Running migration for version: 1" }
        val oldMangaDownloadDir = File(applicationDirs.downloadsRoot)
        val newMangaDownloadDir = File(applicationDirs.mangaDownloadsRoot)
        val downloadDirs = oldMangaDownloadDir.listFiles().orEmpty()

        val moveDownloadsToNewFolder = !newMangaDownloadDir.exists() && downloadDirs.isNotEmpty()
        if (moveDownloadsToNewFolder) {
            newMangaDownloadDir.mkdirs()

            for (downloadDir in downloadDirs) {
                if (downloadDir == File(applicationDirs.thumbnailDownloadsRoot)) {
                    continue
                }

                downloadDir.renameTo(File(newMangaDownloadDir, downloadDir.name))
            }
        }

        // Migrate from old preferences api
        val prefRootNode = "suwayomi/tachidesk"
        val isMigrationRequired = Preferences.userRoot().nodeExists(prefRootNode)
        if (isMigrationRequired) {
            val preferences = Preferences.userRoot().node(prefRootNode)
            migratePreferences(null, preferences)
            preferences.removeNode()
        }
    }
    migrationPreferences.edit().putInt("version", MIGRATION_VERSION).apply()
}
