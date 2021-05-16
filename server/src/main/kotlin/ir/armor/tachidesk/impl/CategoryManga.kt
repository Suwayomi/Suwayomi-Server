package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.model.database.table.CategoryMangaTable
import ir.armor.tachidesk.model.database.table.CategoryTable
import ir.armor.tachidesk.model.database.table.MangaTable
import ir.armor.tachidesk.model.database.table.toDataClass
import ir.armor.tachidesk.model.dataclass.CategoryDataClass
import ir.armor.tachidesk.model.dataclass.MangaDataClass
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object CategoryManga {
    fun addMangaToCategory(mangaId: Int, categoryId: Int) {
        transaction {
            if (CategoryMangaTable.select { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }.firstOrNull() == null) {
                CategoryMangaTable.insert {
                    it[CategoryMangaTable.category] = categoryId
                    it[CategoryMangaTable.manga] = mangaId
                }

                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[MangaTable.defaultCategory] = false
                }
            }
        }
    }

    fun removeMangaFromCategory(mangaId: Int, categoryId: Int) {
        transaction {
            CategoryMangaTable.deleteWhere { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }
            if (CategoryMangaTable.select { CategoryMangaTable.manga eq mangaId }.count() == 0L) {
                MangaTable.update({ MangaTable.id eq mangaId }) {
                    it[MangaTable.defaultCategory] = true
                }
            }
        }
    }

    /**
     * list of mangas that belong to a category
     */
    fun getCategoryMangaList(categoryId: Int): List<MangaDataClass> {
        return transaction {
            CategoryMangaTable.innerJoin(MangaTable).select { CategoryMangaTable.category eq categoryId }.map {
                MangaTable.toDataClass(it)
            }
        }
    }

    /**
     * list of categories that a manga belongs to
     */
    fun getMangaCategories(mangaId: Int): List<CategoryDataClass> {
        return transaction {
            CategoryMangaTable.innerJoin(CategoryTable).select { CategoryMangaTable.manga eq mangaId }.orderBy(CategoryTable.order to SortOrder.ASC).map {
                CategoryTable.toDataClass(it)
            }
        }
    }
}
