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
import suwayomi.tachidesk.manga.impl.Manga.getManga
import suwayomi.tachidesk.manga.impl.util.lang.isEmpty
import suwayomi.tachidesk.manga.impl.util.lang.isNotEmpty
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import java.time.Instant
import kotlin.and

object Library {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun addMangaToLibrary(
        userId: Int,
        mangaId: Int,
    ) {
        val manga = getManga(mangaId)
        if (!manga.inLibrary) {
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

                // todo change to upsert
                if (MangaUserTable.selectAll().where { MangaUserTable.user eq userId and (MangaUserTable.manga eq mangaId) }.isEmpty()) {
                    MangaUserTable.insert {
                        it[MangaUserTable.manga] = mangaId
                        it[MangaUserTable.user] = userId
                        it[inLibrary] = true
                        it[inLibraryAt] = Instant.now().epochSecond
                    }
                } else {
                    MangaUserTable.update({ MangaUserTable.user eq userId and (MangaUserTable.manga eq mangaId) }) {
                        it[inLibrary] = true
                        it[inLibraryAt] = Instant.now().epochSecond
                    }
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

    suspend fun removeMangaFromLibrary(
        userId: Int,
        mangaId: Int,
    ) {
        val manga = getManga(userId, mangaId)
        if (manga.inLibrary) {
            transaction {
                MangaUserTable.update({ MangaUserTable.user eq userId and (MangaUserTable.manga eq mangaId) }) {
                    it[inLibrary] = false
                }
            }.apply {
                handleMangaThumabnail(mangaId)
            }
        }
    }

    fun handleMangaThumbnail(
        mangaId: Int,
    ) {
        scope.launch {
            // todo grab
            val sourceId =
                transaction {
                    MangaTable
                        .select(MangaTable.sourceReference)
                        .where { MangaTable.id eq mangaId }
                        .first()
                        .get(MangaTable.sourceReference)
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
