package ir.armor.tachidesk.database

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.Config
import ir.armor.tachidesk.database.table.CategoryTable
import ir.armor.tachidesk.database.table.ChapterTable
import ir.armor.tachidesk.database.table.ExtensionTable
import ir.armor.tachidesk.database.table.MangaTable
import ir.armor.tachidesk.database.table.PageTable
import ir.armor.tachidesk.database.table.SourceTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DBMangaer {
    val db by lazy {
        Database.connect("jdbc:h2:${Config.dataRoot}/database", "org.h2.Driver")
    }
}

fun makeDataBaseTables() {
    // mention db object to connect
    DBMangaer.db
//    val db = DBMangaer.db
//    db.useNestedTransactions = true

    transaction {
        SchemaUtils.create(ExtensionTable)
        SchemaUtils.create(SourceTable)
        SchemaUtils.create(MangaTable)
        SchemaUtils.create(ChapterTable)
        SchemaUtils.create(PageTable)
        SchemaUtils.create(CategoryTable)
    }
}
