package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.hasPermission

class SettingsQuery {
    @RequireAuth
    fun settings(
        @GraphQLIgnore
        userId: Int,
        @GraphQLIgnore
        permissions: List<UserPermission>,
    ): SettingsType =
        if (permissions.hasPermission(UserPermission.MANAGE_SETTINGS)) {
            SettingsType(userId)
        } else {
            SettingsType.masked(userId)
        }
}
