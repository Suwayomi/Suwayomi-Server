package ir.armor.tachidesk.model.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.model.database.migration.lib.loadMigrationsFrom
import ir.armor.tachidesk.model.database.migration.lib.runMigrations
import ir.armor.tachidesk.server.ApplicationDirs
import org.jetbrains.exposed.sql.Database
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance

object DBMangaer {
    val db by lazy {
        val applicationDirs by DI.global.instance<ApplicationDirs>()
        Database.connect("jdbc:h2:${applicationDirs.dataRoot}/database", "org.h2.Driver")
    }
}

fun databaseUp() {
    // must mention db object so the lazy block executes
    val db = DBMangaer.db
    db.useNestedTransactions = true

    val migrations = loadMigrationsFrom("ir.armor.tachidesk.model.database.migration")
    runMigrations(migrations)
}
