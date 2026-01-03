@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.helpers.MAYBE_TYPE_PREFIX
import suwayomi.tachidesk.server.database.migration.helpers.UNLIMITED_TEXT
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName
import suwayomi.tachidesk.server.serverConfig

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

@Suppress("ClassName", "unused")
class M0053_TrackersFixIds : SQLMigration() {
    private val TrackRecordTable by lazy { "TrackRecord".toSqlName() }
    private val SyncIdColumn by lazy { "sync_id".toSqlName() }
    private val LibraryIdColumn by lazy { "library_id".toSqlName() }
    private val RemoteIdColumn by lazy { "remote_id".toSqlName() }
    private val RemoteUrlColumn by lazy { "remote_url".toSqlName() }

    override val sql by lazy {
        """
        -- Save the current remote_id as library_id, since old Kitsu tracker did not use this correctly
        UPDATE $TrackRecordTable SET $LibraryIdColumn = $RemoteIdColumn WHERE $SyncIdColumn = 3;

        -- Kitsu isn't using the remote_id field properly, but the ID is present in the URL
        -- This parses a url and gets the ID from the trailing path part, e.g. https://kitsu.app/manga/<id>
        UPDATE $TrackRecordTable SET $RemoteIdColumn = ${toNumber(rightMost(RemoteUrlColumn, '/'))} WHERE $SyncIdColumn = 3;
        """.trimIndent()
    }

    fun h2RightMost(
        field: String,
        sep: Char,
    ): String = "SUBSTRING($field, LOCATE('$sep', $field, -1) + 1)"

    fun postgresRightMost(
        field: String,
        sep: Char,
    ): String = "SUBSTRING(SUBSTRING($field FROM '$sep[^$sep]*$') FROM 2)"

    fun h2ToNumber(expr: String): String = expr

    fun postgresToNumber(expr: String): String = "TO_NUMBER($expr, '0000000000')"

    fun rightMost(
        field: String,
        sep: Char,
    ) = when (serverConfig.databaseType.value) {
        DatabaseType.H2 -> h2RightMost(field, sep)
        DatabaseType.POSTGRESQL -> postgresRightMost(field, sep)
    }

    fun toNumber(expr: String) =
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> h2ToNumber(expr)
            DatabaseType.POSTGRESQL -> postgresToNumber(expr)
        }
}
