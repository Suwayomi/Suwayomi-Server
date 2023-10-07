package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Manga.getManga
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.time.Instant

object Library {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun addMangaToLibrary(mangaId: Int) {
        val manga = getManga(mangaId)
        if (!manga.inLibrary) {
            transaction {
                val defaultCategories =
                    CategoryTable.select {
                        (CategoryTable.isDefault eq true) and (CategoryTable.id neq Category.DEFAULT_CATEGORY_ID)
                    }.toList()
                val existingCategories = CategoryMangaTable.select { CategoryMangaTable.manga eq mangaId }.toList()

                MangaTable.update({ MangaTable.id eq manga.id }) {
                    it[inLibrary] = true
                    it[inLibraryAt] = Instant.now().epochSecond
                }

                if (existingCategories.isEmpty()) {
                    defaultCategories.forEach { category ->
                        CategoryMangaTable.insert {
                            it[CategoryMangaTable.category] = category[CategoryTable.id].value
                            it[CategoryMangaTable.manga] = mangaId
                        }
                    }
                }
            }.apply {
                handleMangaThumbnail(mangaId, true)
            }
        }
    }

    suspend fun removeMangaFromLibrary(mangaId: Int) {
        val manga = getManga(mangaId)
        if (manga.inLibrary) {
            transaction {
                MangaTable.update({ MangaTable.id eq manga.id }) {
                    it[inLibrary] = false
                }
            }.apply {
                handleMangaThumbnail(mangaId, false)
            }
        }
    }

    fun handleMangaThumbnail(
        mangaId: Int,
        inLibrary: Boolean,
    ) {
        scope.launch {
            try {
                if (inLibrary) {
                    ThumbnailDownloadHelper.download(mangaId)
                } else {
                    ThumbnailDownloadHelper.delete(mangaId)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
