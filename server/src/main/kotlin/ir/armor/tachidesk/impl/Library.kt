package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.impl.Manga.getManga
import ir.armor.tachidesk.model.database.CategoryMangaTable
import ir.armor.tachidesk.model.database.MangaTable
import ir.armor.tachidesk.model.database.toDataClass
import ir.armor.tachidesk.model.dataclass.MangaDataClass
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object Library {
// TODO: `Category.isLanding` is to handle the default categories a new library manga gets,
// ..implement that shit at some time...
// ..also Consider to rename it to `isDefault`
    suspend fun addMangaToLibrary(mangaId: Int) {
        val manga = getManga(mangaId)
        if (!manga.inLibrary) {
            transaction {
                MangaTable.update({ MangaTable.id eq manga.id }) {
                    it[inLibrary] = true
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
