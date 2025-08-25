package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.loadMigrationsFrom
import de.neonew.exposed.migrations.runMigrations
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.ExperimentalKeywordApi
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.sql.SQLException
import kotlin.system.exitProcess

object DBManager {
    var db: Database? = null
        private set

    fun setupDatabase(): Database {
        if (TransactionManager.isInitialized()) {
            val currentDatabase = TransactionManager.currentOrNull()?.db
            if (currentDatabase != null) {
                TransactionManager.closeAndUnregister(currentDatabase)
            }
        }

        val applicationDirs = Injekt.get<ApplicationDirs>()
        val dbConfig =
            DatabaseConfig {
                useNestedTransactions = true
                @OptIn(ExperimentalKeywordApi::class)
                preserveKeywordCasing = false
            }
        return when (serverConfig.databaseType.value) {
            DatabaseType.POSTGRESQL ->
                Database.connect(
                    "jdbc:${serverConfig.databaseUrl.value}",
                    "org.postgresql.Driver",
                    user = serverConfig.databaseUsername.value,
                    password = serverConfig.databasePassword.value,
                    databaseConfig = dbConfig,
                )
            DatabaseType.H2 ->
                Database.connect(
                    "jdbc:h2:${applicationDirs.dataRoot}/database",
                    "org.h2.Driver",
                    databaseConfig = dbConfig,
                )
        }.also { db = it }
    }
}

private val logger = KotlinLogging.logger {}

fun databaseUp() {
    val db = DBManager.setupDatabase()
    // call db to initialize the lazy object
    logger.info {
        "Using ${db.vendor} database version ${db.version}"
    }
    try {
        if (serverConfig.databaseType.value == DatabaseType.POSTGRESQL) {
            transaction {
                val schema =
                    Schema(
                        "suwayomi",
                        serverConfig.databaseUsername.value.takeIf { it.isNotBlank() },
                    )
                SchemaUtils.createSchema(schema)
                SchemaUtils.setSchema(schema)
            }
        }

        val migrations = loadMigrationsFrom("suwayomi.tachidesk.server.database.migration", ServerConfig::class.java)
        runMigrations(migrations)
    } catch (e: SQLException) {
        logger.error(e) { "Error up-to-database migration" }
        exitProcess(101)
    }
}
