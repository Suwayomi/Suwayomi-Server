package ir.armor.tachidesk.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.model.database.CategoryMangaTable
import ir.armor.tachidesk.model.database.CategoryTable
import ir.armor.tachidesk.model.database.ChapterTable
import ir.armor.tachidesk.model.database.ExtensionTable
import ir.armor.tachidesk.model.database.MangaTable
import ir.armor.tachidesk.model.database.PageTable
import ir.armor.tachidesk.model.database.SourceTable
import ir.armor.tachidesk.server.ApplicationDirs
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance

object DBMangaer {
    val db by lazy {
        val dirs by DI.global.instance<ApplicationDirs>()
        Database.connect("jdbc:h2:${dirs.dataRoot}/database", "org.h2.Driver")
    }
}

fun makeDataBaseTables() {
    // must mention db object so the lazy block executes
    val db = DBMangaer.db
    db.useNestedTransactions = true

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            ExtensionTable,
            SourceTable,
            MangaTable,
            ChapterTable,
            PageTable,
            CategoryTable,
            CategoryMangaTable,
        )
    }
}
