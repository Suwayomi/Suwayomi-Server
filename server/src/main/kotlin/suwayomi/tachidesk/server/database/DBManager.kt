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
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.ServerConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object DBManager {
    val db by lazy {
        val applicationDirs = Injekt.get<ApplicationDirs>()
        Database.connect(
            "jdbc:h2:${applicationDirs.dataRoot}/database",
            "org.h2.Driver",
            databaseConfig =
                DatabaseConfig {
                    useNestedTransactions = true
                    @OptIn(ExperimentalKeywordApi::class)
                    preserveKeywordCasing = false
                },
        )
    }
}

private val logger = KotlinLogging.logger {}

fun databaseUp(db: Database = DBManager.db) {
    // call db to initialize the lazy object
    logger.info {
        "Using ${db.vendor} database version ${db.version}"
    }

    val migrations = loadMigrationsFrom("suwayomi.tachidesk.server.database.migration", ServerConfig::class.java)
    runMigrations(migrations)
}
