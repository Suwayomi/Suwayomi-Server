package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Manga.getManga
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass

object Library {
    suspend fun addMangaToLibrary(mangaId: Int) {
        val manga = getManga(mangaId)
        if (!manga.inLibrary) {
            transaction {
                val defaultCategories = CategoryTable.select { CategoryTable.isDefault eq true }.toList()

                MangaTable.update({ MangaTable.id eq manga.id }) {
                    it[MangaTable.inLibrary] = true
                    it[MangaTable.defaultCategory] = defaultCategories.isEmpty()
                }

                defaultCategories.forEach { category ->
                    CategoryMangaTable.insert {
                        it[CategoryMangaTable.category] = category[CategoryTable.id].value
                        it[CategoryMangaTable.manga] = mangaId
                    }
                }
            }
        }
    }

    suspend fun removeMangaFromLibrary(mangaId: Int) {
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
}
