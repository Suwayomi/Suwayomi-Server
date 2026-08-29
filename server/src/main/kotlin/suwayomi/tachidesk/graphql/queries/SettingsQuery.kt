package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.server.user.UserPermission

class SettingsQuery {
    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun settings(): SettingsType = SettingsType()
}
