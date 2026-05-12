package suwayomi.tachidesk.server

import android.app.Application
import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.database.H2Migration
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

        preferences
            .edit()
            .apply {
                items.forEach { (key, value) ->
                    if (value != null) {
                        putString(key, value)
                    }
                }
            }.apply()

        migratePreferences(key, subNode) // Recursively migrate sub-level nodes
    }
}

private fun migratePreferencesToNewXmlFileBasedStorage() {
    // Migrate from old preferences api
    val prefRootNode = "suwayomi/tachidesk"
    val isMigrationRequired = Preferences.userRoot().nodeExists(prefRootNode)
    if (isMigrationRequired) {
        val preferences = Preferences.userRoot().node(prefRootNode)
        migratePreferences(null, preferences)
        preferences.removeNode()
    }
}

private fun migrateMangaDownloadDir(applicationDirs: ApplicationDirs) {
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
}

fun migrateH2DatabaseToV24240(applicationDirs: ApplicationDirs) {
    H2Migration.migrate(
        applicationDirs.dataRoot,
        "1.4.200",
        "2.4.240",
    )
}

private val MIGRATIONS =
    listOf<Pair<String, (ApplicationDirs) -> Unit>>(
        "InitialMigration" to { applicationDirs ->
            migrateMangaDownloadDir(applicationDirs)
            migratePreferencesToNewXmlFileBasedStorage()
        },
        "FixGlobalUpdateScheduling" to {
            Injekt.get<IUpdater>().deleteLastAutomatedUpdateTimestamp()
        },
        "MigrateH2DatabaseToV2.4.240" to { applicationDirs ->
            migrateH2DatabaseToV24240(applicationDirs)
        },
    )

fun runMigrations(applicationDirs: ApplicationDirs) {
    val logger = KotlinLogging.logger("Migration")

    val migrationPreferences =
        Injekt
            .get<Application>()
            .getSharedPreferences(
                "migrations",
                Context.MODE_PRIVATE,
            )
    val version = migrationPreferences.getInt("version", 0)

    logger.info { "Running migrations, previous version $version, target version ${MIGRATIONS.size}" }

    MIGRATIONS.forEachIndexed { index, (migrationName, migrationFunction) ->
        val migrationVersion = index + 1

        val isMigrationRequired = version < migrationVersion
        if (!isMigrationRequired) {
            logger.info { "Skipping migration version $migrationVersion: $migrationName" }
            return@forEachIndexed
        }

        logger.info { "Running migration version $migrationVersion: $migrationName" }

        migrationFunction(applicationDirs)

        migrationPreferences.edit().putInt("version", migrationVersion).apply()
    }
}
