package suwayomi.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.Database
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.server.ApplicationDirs
import suwayomi.server.database.migration.lib.loadMigrationsFrom
import suwayomi.server.database.migration.lib.runMigrations

object DBManager {
    val db by lazy {
        val applicationDirs by DI.global.instance<ApplicationDirs>()
        Database.connect("jdbc:h2:${applicationDirs.dataRoot}/database", "org.h2.Driver")
    }
}

fun databaseUp() {
    // must mention db object so the lazy block executes
    val db = DBManager.db
    db.useNestedTransactions = true

    val migrations = loadMigrationsFrom("suwayomi.server.database.migration")
    runMigrations(migrations)
}
