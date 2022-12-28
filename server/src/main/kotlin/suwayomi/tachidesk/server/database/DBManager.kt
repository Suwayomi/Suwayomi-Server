package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.loadMigrationsFrom
import de.neonew.exposed.migrations.runMigrations
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.ServerConfig

object DBManager {
    private val logger = KotlinLogging.logger {}

    val masterDB by lazy {
        val applicationDirs by DI.global.instance<ApplicationDirs>()

        Database.connect(
            "jdbc:h2:${applicationDirs.dataRoot}/server",
            "org.h2.Driver",
            databaseConfig = DatabaseConfig {
                useNestedTransactions = true
            }
        )
    }

    fun masterDbUp() {
        val db = masterDB
        logger.info { "Initialized masterDb: ${masterDB.url}" }
    }

    private val usersDatabases = mutableMapOf<String, Database>()
    fun databaseForUser(username: String) {
        usersDatabases.getOrElse(username) {
            val applicationDirs by DI.global.instance<ApplicationDirs>()

            // call db to initialize the lazy object
            logger.info {
                "initializing database for user $username"
            }

            val database = Database.connect(
                "jdbc:h2:${applicationDirs.dataRoot}/server",
                "org.h2.Driver",
                databaseConfig = DatabaseConfig {
                    useNestedTransactions = true
                }
            )

            val migrations =
                loadMigrationsFrom("suwayomi.tachidesk.server.database.migration", ServerConfig::class.java)
            runMigrations(migrations, database)

            usersDatabases[username] = database

            database
        }
    }
}
