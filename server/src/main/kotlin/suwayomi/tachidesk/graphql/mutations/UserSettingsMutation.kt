@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.PartialUserSettingsType
import suwayomi.tachidesk.graphql.types.UserSettingsType
import suwayomi.tachidesk.server.settings.UserSettingsRegistry
import suwayomi.tachidesk.server.settings.asMap
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings

class UserSettingsMutation {
    data class SetUserSettingsInput(
        val clientMutationId: String? = null,
        val userSettings: PartialUserSettingsType,
    )

    data class SetUserSettingsPayload(
        val clientMutationId: String?,
        val userSettings: UserSettingsType,
    )

    @RequireAuth
    fun setUserSettings(
        @GraphQLIgnore
        userId: Int,
        input: SetUserSettingsInput,
    ): SetUserSettingsPayload {
        val (clientMutationId, settings) = input

        updateSettings(userId, settings)

        return SetUserSettingsPayload(clientMutationId, UserSettingsType(userId))
    }

    @GraphQLIgnore
    fun updateSettings(
        userId: Int,
        settings: PartialUserSettingsType,
    ) {
        // Ensure all UserSetting descriptors are registered in UserSettingsRegistry before looking them up;
        // otherwise an uninitialized registry would silently drop every field
        userConfig

        settings
            .asMap()
            .forEach { (name, value) ->
                if (value != null) {
                    val setting = UserSettingsRegistry.get(name) ?: return@forEach

                    val maybeConvertedValue =
                        setting.typeInfo?.convertToInternalType?.invoke(value) ?: value

                    userSettings.setAny(userId, setting, maybeConvertedValue)
                }
            }
    }

    data class ResetUserSettingsInput(
        val clientMutationId: String? = null,
    )

    data class ResetUserSettingsPayload(
        val clientMutationId: String?,
        val userSettings: UserSettingsType,
    )

    @RequireAuth
    fun resetUserSettings(
        @GraphQLIgnore
        userId: Int,
        input: ResetUserSettingsInput,
    ): ResetUserSettingsPayload {
        val (clientMutationId) = input

        userSettings.resetAll(userId)

        return ResetUserSettingsPayload(clientMutationId, UserSettingsType(userId))
    }
}
