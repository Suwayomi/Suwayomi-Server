package suwayomi.tachidesk.server

import android.app.Application
import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.database.H2Migration
import suwayomi.tachidesk.server.util.ExitCode
import suwayomi.tachidesk.server.util.shutdownApp
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

private fun migrateH2DatabaseToV24240(applicationDirs: ApplicationDirs) {
    H2Migration.migrate(
        applicationDirs.dataRoot,
        "1.4.200",
        "2.4.240",
    )
}

private enum class MigrationType {
    PRE_DB_STARTUP,
    POST_DB_STARTUP,
}

private val PRE_DB_STARTUP_MIGRATIONS =
    listOf<Pair<String, suspend (ApplicationDirs) -> Unit>>(
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

private val POST_DB_MIGRATIONS = listOf<Pair<String, suspend (ApplicationDirs) -> Unit>>()

private val MIGRATIONS =
    mapOf<Any, List<Pair<String, suspend (ApplicationDirs) -> Unit>>>(
        MigrationType.PRE_DB_STARTUP to PRE_DB_STARTUP_MIGRATIONS,
        MigrationType.POST_DB_STARTUP to POST_DB_MIGRATIONS,
    )

private val migrationPreferences =
    Injekt
        .get<Application>()
        .getSharedPreferences(
            "migrations",
            Context.MODE_PRIVATE,
        )

private val version by lazy { migrationPreferences.getInt("version", 0) }

private suspend fun runMigrations(
    type: MigrationType,
    startIndex: Int,
    applicationDirs: ApplicationDirs,
) {
    val logger = KotlinLogging.logger("Migration(type= ${type.name})")
    try {
        val migrations = MIGRATIONS[type].orEmpty()

        migrations.forEachIndexed { index, (migrationName, migrationFunction) ->
            val migrationVersion = startIndex + index + 1

            val isMigrationRequired = version < migrationVersion
            if (!isMigrationRequired) {
                logger.debug { "Skipping migration version $migrationVersion: $migrationName" }
                return@forEachIndexed
            }

            logger.info { "Running migration version $migrationVersion: $migrationName" }

            migrationFunction(applicationDirs)

            migrationPreferences.edit().putInt("version", migrationVersion).commit()
        }
    } catch (e: Exception) {
        logger.error(e) { "Failed to run migrations" }
        shutdownApp(ExitCode.MigrationsRunFailure)
    }
}

suspend fun runMigrations(
    applicationDirs: ApplicationDirs,
    dbStartup: () -> Unit,
) {
    val logger = KotlinLogging.logger("Migration")

    logger.info { "Running migrations, previous version $version, target version ${MIGRATIONS.flatMap { it.value }.size}" }

    runMigrations(MigrationType.PRE_DB_STARTUP, 0, applicationDirs)

    dbStartup()

    runMigrations(MigrationType.POST_DB_STARTUP, PRE_DB_STARTUP_MIGRATIONS.size, applicationDirs)

    logger.info { "Migrations finished successfully" }
}
