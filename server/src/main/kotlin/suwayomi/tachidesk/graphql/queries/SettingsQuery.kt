package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.types.SettingsType

class SettingsQuery {
    fun settings(): SettingsType {
        return SettingsType()
    }
}
