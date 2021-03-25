package ir.armor.tachidesk.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.database.dataclass.MangaDataClass
import ir.armor.tachidesk.database.table.CategoryMangaTable
import ir.armor.tachidesk.database.table.MangaTable
import ir.armor.tachidesk.database.table.toDataClass
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update


fun addMangaToLibrary(mangaId: Int) {
    val manga = getManga(mangaId)
    if (!manga.inLibrary) {
        transaction {
            MangaTable.update({ MangaTable.id eq manga.id }) {
                it[inLibrary] = true
            }
        }
    }
}

fun removeMangaFromLibrary(mangaId: Int) {
    val manga = getManga(mangaId)
    if (manga.inLibrary) {
        transaction {
            MangaTable.update({ MangaTable.id eq manga.id }) {
                it[inLibrary] = false
                it[defaultCategory] = true
            }
            CategoryMangaTable.deleteWhere { CategoryMangaTable.manga eq mangaId }
        }
    }
}

fun getLibraryMangas(): List<MangaDataClass> {
    return transaction {
        MangaTable.select { (MangaTable.inLibrary eq true) and (MangaTable.defaultCategory eq true) }.map {
            MangaTable.toDataClass(it)
        }
    }
}
