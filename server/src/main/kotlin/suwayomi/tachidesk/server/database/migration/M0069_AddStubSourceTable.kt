package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.Migration
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName

@Suppress("ClassName", "unused")
class M0069_AddStubSourceTable : Migration() {
    private class StubSourceTable : IdTable<Long>() {
        override val id = long("id").entityId().uniqueIndex()
        val name = varchar("name", 128)
        val lang = varchar("lang", 32)
    }

    override fun run() {
        with(TransactionManager.current()) {
            SchemaUtils.createStatements(StubSourceTable()).forEach { exec(it) }

            val stubSourceTable = "StubSource".toSqlName()
            val sourceTable = "Source".toSqlName()
            val idColumn = "id".toSqlName()
            val nameColumn = "name".toSqlName()
            val langColumn = "lang".toSqlName()

            exec(
                """
                INSERT INTO $stubSourceTable ($idColumn, $nameColumn, $langColumn)
                SELECT $idColumn, $nameColumn, $langColumn FROM $sourceTable
                WHERE $idColumn NOT IN (SELECT $idColumn FROM $stubSourceTable)
                """.trimIndent(),
            )
            currentDialectMetadata.resetCaches()
        }
    }
}
