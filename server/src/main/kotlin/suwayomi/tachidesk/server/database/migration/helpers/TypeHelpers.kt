package suwayomi.tachidesk.server.database.migration.helpers

import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.serverConfig

val UNLIMITED_TEXT
    get() =
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> "VARCHAR" // the default length is `Integer.MAX_VALUE`
            DatabaseType.POSTGRESQL -> "TEXT"
        }

val MAYBE_TYPE_PREFIX
    get() =
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> ""
            DatabaseType.POSTGRESQL -> "TYPE "
        }

val INITIAL_ORDER_NAME
    get() =
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> """"ORDER""""
            DatabaseType.POSTGRESQL -> """"order""""
        }
