package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import java.time.Instant

object Library {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun addMangaToLibrary(
        userId: Int,
        mangaId: Int,
    ) {
        val inLibrary =
            transaction {
                MangaUserTable
                    .select(MangaUserTable.id)
                    .where { MangaUserTable.manga eq mangaId and (MangaUserTable.user eq userId) and (MangaUserTable.inLibrary eq true) }
                    .any()
            }
        if (!inLibrary) {
            transaction {
                val defaultCategories =
                    CategoryTable
                        .selectAll()
                        .where {
                            MangaUserTable.user eq userId and
                                (CategoryTable.isDefault eq true) and
                                (CategoryTable.id neq Category.DEFAULT_CATEGORY_ID)
                        }.toList()
                val existingCategories = CategoryMangaTable.selectAll().where { CategoryMangaTable.manga eq mangaId }.toList()

                MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                    it[MangaUserTable.manga] = mangaId
                    it[MangaUserTable.user] = userId
                    it[MangaUserTable.inLibrary] = true
                    it[MangaUserTable.inLibraryAt] = Instant.now().epochSecond
                }

                if (existingCategories.isEmpty()) {
                    defaultCategories.forEach { category ->
                        CategoryMangaTable.upsert(CategoryMangaTable.manga, CategoryMangaTable.category) {
                            it[CategoryMangaTable.category] = category[CategoryTable.id].value
                            it[CategoryMangaTable.manga] = mangaId
                            it[CategoryMangaTable.user] = userId
                        }
                    }
                }
            }.apply {
                handleMangaThumbnail(mangaId)
            }
        }
    }

    fun removeMangaFromLibrary(
        userId: Int,
        mangaId: Int,
    ) {
        val inLibrary =
            transaction {
                MangaUserTable
                    .select(MangaUserTable.id)
                    .where { MangaUserTable.manga eq mangaId and (MangaUserTable.user eq userId) and (MangaUserTable.inLibrary eq true) }
                    .any()
            }
        if (inLibrary) {
            transaction {
                MangaUserTable.update({ MangaUserTable.user eq userId and (MangaUserTable.manga eq mangaId) }) {
                    it[MangaUserTable.inLibrary] = false
                }
            }.apply {
                handleMangaThumbnail(mangaId)
            }
        }
    }

    fun handleMangaThumbnail(mangaId: Int) {
        scope.launch {
            val sourceId =
                transaction {
                    MangaTable
                        .select(MangaTable.sourceReference)
                        .where { MangaTable.id eq mangaId }
                        .first()
                        .get(MangaTable.sourceReference)
                }
            val inLibrary =
                transaction {
                    MangaUserTable
                        .select(MangaUserTable.id)
                        .where { MangaUserTable.manga eq mangaId and (MangaUserTable.inLibrary eq true) }
                        .any()
                }

            if (sourceId == LocalSource.ID) {
                return@launch
            }

            try {
                if (inLibrary) {
                    ThumbnailDownloadHelper.download(mangaId)
                } else {
                    ThumbnailDownloadHelper
                        .delete(mangaId)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
