@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.PartialSettingsType
import suwayomi.tachidesk.graphql.types.Settings
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.server.settings.SettingsCompat
import suwayomi.tachidesk.server.settings.SettingsUpdater
import suwayomi.tachidesk.server.settings.SettingsValidator
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.hasPermission
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
    fun setSettings(
        @GraphQLIgnore
        userId: Int,
        @GraphQLIgnore
        permissions: List<UserPermission>,
        input: SetSettingsInput,
    ): SetSettingsPayload {
        val (clientMutationId, settings) = input

        SettingsCompat.applyMovedSettingsToUser(userId, settings)

        if (!permissions.hasPermission(UserPermission.MANAGE_SETTINGS)) {
            return SetSettingsPayload(clientMutationId, SettingsType.masked(userId))
        }

        updateSettings(settings)

        return SetSettingsPayload(clientMutationId, SettingsType(userId))
    }

    data class ResetSettingsInput(
        val clientMutationId: String? = null,
    )

    data class ResetSettingsPayload(
        val clientMutationId: String?,
        val settings: SettingsType,
    )

    @RequireAuth
    fun resetSettings(
        @GraphQLIgnore
        userId: Int,
        @GraphQLIgnore
        permissions: List<UserPermission>,
        input: ResetSettingsInput,
    ): ResetSettingsPayload {
        val (clientMutationId) = input

        SettingsCompat.resetMovedUserSettings(userId)

        if (!permissions.hasPermission(UserPermission.MANAGE_SETTINGS)) {
            return ResetSettingsPayload(clientMutationId, SettingsType.masked(userId))
        }

        GlobalConfigManager.resetUserConfig()
        val settings = SettingsType.defaults()
        updateSettings(settings)

        return ResetSettingsPayload(clientMutationId, settings)
    }
}
