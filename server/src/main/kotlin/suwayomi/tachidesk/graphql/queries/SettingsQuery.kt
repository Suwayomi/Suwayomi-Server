package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.SettingsType

class SettingsQuery {
    @RequireAuth
    fun settings(): SettingsType = SettingsType()
}
