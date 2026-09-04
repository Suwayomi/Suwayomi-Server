package suwayomi.tachidesk.server

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.database.H2Migration
import suwayomi.tachidesk.server.user.applyUserSettingsBackfillFile
import suwayomi.tachidesk.server.user.saveUserSettingsBackfillFile
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

private fun migrateSharedPrefsToUser1() {
    val app = Injekt.get<Application>()

    // 1. Koreader sync preferences
    val koreaderPrefs = app.getSharedPreferences("koreader_sync", Context.MODE_PRIVATE)
    migrateStringPref(koreaderPrefs, "server_address", "server_address_1", "https://sync.koreader.rocks/")
    migrateStringPref(koreaderPrefs, "username", "username_1", "")
    migrateStringPref(koreaderPrefs, "user_key", "user_key_1", "")
    migrateStringPref(koreaderPrefs, "client_id", "client_id_1", "")

    // 2. Sync preferences
    val syncPrefs = app.getSharedPreferences("sync", Context.MODE_PRIVATE)
    migrateLongPref(syncPrefs, "last_sync_timestamp", "last_sync_timestamp_1", 0L)
    migrateLongPref(syncPrefs, "last_scheduled_sync", "last_scheduled_sync_1", 0L)
    migrateStringPref(syncPrefs, "last_sync_etag", "last_sync_etag_1", "")

    // 3. Tracker preferences
    val trackerPrefs = app.getSharedPreferences("tracker", Context.MODE_PRIVATE)
    migrateBooleanPref(trackerPrefs, "pref_auto_update_manga_sync_key", "pref_auto_update_manga_sync_1", true)

    for (trackerId in listOf(1, 2, 3, 4, 5, 7)) {
        migrateStringPref(trackerPrefs, "pref_mangasync_username_$trackerId", "pref_mangasync_username_1_$trackerId", "")
        migrateStringPref(trackerPrefs, "pref_mangasync_password_$trackerId", "pref_mangasync_password_1_$trackerId", "")
        migrateStringPref(trackerPrefs, "track_token_$trackerId", "track_token_1_$trackerId", "")
        migrateBooleanPref(trackerPrefs, "track_token_expired_$trackerId", "track_token_expired_1_$trackerId", false)
        migrateStringPref(trackerPrefs, "score_type_$trackerId", "score_type_1_$trackerId", "POINT_10")
    }
}

private fun migrateStringPref(
    prefs: SharedPreferences,
    oldKey: String,
    newKey: String,
    default: String,
) {
    val value = prefs.getString(oldKey, default)
    if (value != default) {
        prefs.edit().putString(newKey, value).apply()
    }
}

private fun migrateLongPref(
    prefs: SharedPreferences,
    oldKey: String,
    newKey: String,
    default: Long,
) {
    val value = prefs.getLong(oldKey, default)
    if (value != default) {
        prefs.edit().putLong(newKey, value).apply()
    }
}

private fun migrateBooleanPref(
    prefs: SharedPreferences,
    oldKey: String,
    newKey: String,
    default: Boolean,
) {
    val value = prefs.getBoolean(oldKey, default)
    if (value != default) {
        prefs.edit().putBoolean(newKey, value).apply()
    }
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
        "SaveUserSettingsBackfillFile" to { applicationDirs ->
            saveUserSettingsBackfillFile(applicationDirs)
            migrateSharedPrefsToUser1()
        },
    )

private val POST_DB_MIGRATIONS =
    listOf<Pair<String, suspend (ApplicationDirs) -> Unit>>(
        "MigrateUserSettingsToUser1" to { applicationDirs ->
            applyUserSettingsBackfillFile(applicationDirs)
        },
    )

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
