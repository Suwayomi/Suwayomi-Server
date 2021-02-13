package ir.armor.tachidesk.util

import ir.armor.tachidesk.database.dataclass.MangaDataClass
import ir.armor.tachidesk.database.table.CategoryMangaTable
import ir.armor.tachidesk.database.table.MangaTable
import ir.armor.tachidesk.database.table.toDataClass
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

fun addMangaToCategory(mangaId: Int, categoryId: Int) {
    transaction {
        if (CategoryMangaTable.select { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }.firstOrNull() == null) {
            CategoryMangaTable.insert {
                it[CategoryMangaTable.category] = categoryId
                it[CategoryMangaTable.manga] = mangaId
            }
        }
    }
}

fun removeMangaFromCategory(mangaId: Int, categoryId: Int) {
    transaction {
        CategoryMangaTable.deleteWhere { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }
    }
}

fun getCategoryMangaList(categoryId: Int): List<MangaDataClass> {
    return transaction {
        CategoryMangaTable.innerJoin(MangaTable).select { CategoryMangaTable.category eq categoryId }.map {
            MangaTable.toDataClass(it)
        }
    }
}
