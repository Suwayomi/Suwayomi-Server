package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Category.DEFAULT_CATEGORY_ID
import suwayomi.tachidesk.manga.impl.util.lang.isEmpty
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.manga.model.view.MangaView
import suwayomi.tachidesk.manga.model.view.toDataClass

object CategoryManga {
    fun addMangaToCategory(mangaId: Int, categoryId: Int) {
        fun notAlreadyInCategory() = CategoryMangaTable.select { (CategoryMangaTable.category eq categoryId) and (CategoryMangaTable.manga eq mangaId) }.isEmpty()

        transaction {
            if (notAlreadyInCategory()) {
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
        if (categoryId == DEFAULT_CATEGORY_ID)
            return transaction {
                MangaView.select { (MangaView.inLibrary eq true) and (MangaView.defaultCategory eq true) }.map {
                    MangaView.toDataClass(it)
                }
            }

        return transaction {
            MangaView.select { MangaView.category eq categoryId }.map {
                MangaView.toDataClass(it)
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
