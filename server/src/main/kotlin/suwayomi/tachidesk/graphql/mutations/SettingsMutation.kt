package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.PartialSettingsType
import suwayomi.tachidesk.graphql.types.Settings
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.server.SERVER_CONFIG_MODULE_NAME
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.settings.SettingsUpdater
import suwayomi.tachidesk.server.settings.SettingsValidator
import xyz.nulldev.ts.config.GlobalConfigManager

class SettingsMutation {
    data class SetSettingsInput(
        val clientMutationId: String? = null,
        val settings: PartialSettingsType,
    )

    data class SetSettingsPayload(
        val clientMutationId: String?,
        val settings: SettingsType,
    )

    @GraphQLIgnore
    fun updateSettings(settings: Settings) {
        val validationErrors = SettingsValidator.validate(settings, true)
        if (validationErrors.isNotEmpty()) {
            throw Exception("Validation errors: ${validationErrors.joinToString("; ")}")
        }

        SettingsUpdater.updateAll(settings)
    }

    @RequireAuth
    fun setSettings(input: SetSettingsInput): SetSettingsPayload {
        val (clientMutationId, settings) = input

        updateSettings(settings)

        return SetSettingsPayload(clientMutationId, SettingsType())
    }

    data class ResetSettingsInput(
        val clientMutationId: String? = null,
    )

    data class ResetSettingsPayload(
        val clientMutationId: String?,
        val settings: SettingsType,
    )

    @RequireAuth
    fun resetSettings(input: ResetSettingsInput): ResetSettingsPayload {
        val (clientMutationId) = input

        GlobalConfigManager.resetUserConfig()
        val defaultServerConfig =
            ServerConfig {
                GlobalConfigManager.config.getConfig(
                    SERVER_CONFIG_MODULE_NAME,
                )
            }

        val settings = SettingsType(defaultServerConfig)
        updateSettings(settings)

        return ResetSettingsPayload(clientMutationId, settings)
    }
}
