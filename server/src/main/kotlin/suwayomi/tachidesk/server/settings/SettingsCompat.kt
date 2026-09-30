package suwayomi.tachidesk.server.settings

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.graphql.types.Settings

/**
 * Facilitate changes to the deprecated server settings to apply to the user settings.
 */
object SettingsCompat {
    private val logger = KotlinLogging.logger { }

    fun isMovedToUserSetting(name: String): Boolean =
        SettingsRegistry.get(name)?.deprecated != null && UserSettingsRegistry.get(name) != null

    fun applyMovedSettingsToUser(
        userId: Int,
        settings: Settings,
    ) {
        settings.asMap().forEach { (name, value) ->
            if (value == null || !isMovedToUserSetting(name)) {
                return@forEach
            }

            try {
                val converted =
                    SettingsRegistry
                        .get(name)!!
                        .typeInfo.convertToInternalType
                        ?.invoke(value) ?: value
                userSettings.setAny(userId, UserSettingsRegistry.get(name)!!, converted)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to apply per-user setting $name for user $userId due to" }
            }
        }
    }

    fun resetMovedUserSettings(userId: Int) {
        UserSettingsRegistry.getAll().forEach { (name, setting) ->
            if (isMovedToUserSetting(name)) {
                userSettings.reset(userId, setting)
            }
        }
    }
}
