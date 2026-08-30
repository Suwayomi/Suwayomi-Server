package suwayomi.tachidesk.server.user

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import io.github.config4k.ClassContainer
import io.github.config4k.readers.SelectReader
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.SERVER_CONFIG_MODULE_NAME
import suwayomi.tachidesk.server.settings.UserSetting
import suwayomi.tachidesk.server.settings.UserSettingsRegistry
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import xyz.nulldev.ts.config.GlobalConfigManager
import java.io.File

private val logger = KotlinLogging.logger {}

const val USER_SETTINGS_BACKFILL_FILENAME = "userSettingsBackfill.json"

private fun backfillFile(applicationDirs: ApplicationDirs) = File(applicationDirs.dataRoot, USER_SETTINGS_BACKFILL_FILENAME)

private fun allUserSettings(): Map<String, UserSetting<*>> {
    userConfig // touch the lazy so all per-user settings get registered
    return UserSettingsRegistry.getAll()
}

/**
 * Saves the user-set values of the per-user settings to a file in the application directory.
 *
 * Runs as a pre-DB migration. The deprecated global settings no longer read from the config file, but the
 * user-set values are still present in the in-memory config, so they are read from there. Each value is
 * encoded with [suwayomi.tachidesk.server.settings.UserSettings.encode] so the post-DB migration can decode
 * it with [suwayomi.tachidesk.server.settings.UserSettings.decode].
 *
 * Only settings with a user-set value in the config are captured; settings the user never set are skipped,
 * their static default matches the old global default.
 */
fun saveUserSettingsBackfillFile(applicationDirs: ApplicationDirs) {
    val config = GlobalConfigManager.config
    val serverConfig = config.getConfig(SERVER_CONFIG_MODULE_NAME)

    val entries = mutableMapOf<String, String>()

    allUserSettings().forEach { (key, setting) ->
        if (!config.hasPath("${SERVER_CONFIG_MODULE_NAME}.$key")) {
            return@forEach
        }

        try {
            val reader = SelectReader.getReader(ClassContainer(setting.type, setting.typeArguments))
            val value = reader(serverConfig, key) ?: return@forEach
            entries[key] = userSettings.encode(value)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to read user-set value for setting $key; skipping" }
        }
    }

    val file = backfillFile(applicationDirs)
    val tmpFile = File(file.parentFile, "$USER_SETTINGS_BACKFILL_FILENAME.tmp")
    tmpFile.writeText(
        ConfigFactory.parseMap(entries).root().render(ConfigRenderOptions.concise().setJson(true)),
    )
    tmpFile.copyTo(file, overwrite = true)
    tmpFile.delete()

    logger.info { "Saved ${entries.size} user settings to $file" }
}

/**
 * Applies the per-user settings saved by [saveUserSettingsBackfillFile] to user 1.
 *
 * Runs as a post-DB migration. Each value is decoded with
 * [suwayomi.tachidesk.server.settings.UserSettings.decode] and stored as an explicit override for user 1,
 * pinning the admin's pre-migration settings so later changes to the (now per-user) defaults do not silently
 * change them.
 */
fun applyUserSettingsBackfillFile(applicationDirs: ApplicationDirs) {
    val file = backfillFile(applicationDirs)
    if (!file.exists()) {
        logger.warn { "No user settings backfill file found at $file; skipping user 1 settings backfill" }
        return
    }

    val config =
        try {
            ConfigFactory.parseFile(file)
        } catch (e: ConfigException) {
            logger.error(e) { "Failed to parse user settings backfill file $file; skipping user 1 settings backfill" }
            return
        }

    val settings = allUserSettings()
    var applied = 0

    config.entrySet().forEach { (key, value) ->
        val setting = settings[key]
        if (setting == null) {
            logger.warn { "Unknown setting $key in user settings backfill file; skipping" }
            return@forEach
        }

        try {
            @Suppress("UNCHECKED_CAST")
            val decoded = userSettings.decode(value.unwrapped().toString(), setting as UserSetting<Any>)
            if (decoded != setting.defaultValue) {
                userSettings.setAny(1, setting, decoded)
            }
            applied++
        } catch (e: Exception) {
            logger.error(e) { "Failed to apply backfilled value for setting $key; skipping" }
        }
    }

    logger.info { "Applied $applied user settings to user 1" }
}
