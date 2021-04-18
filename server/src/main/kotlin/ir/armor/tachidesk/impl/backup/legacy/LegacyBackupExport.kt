package ir.armor.tachidesk.impl.backup.legacy

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.github.salomonbrys.kotson.set
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.source.LocalSource
import ir.armor.tachidesk.impl.Category.getCategoryList
import ir.armor.tachidesk.impl.CategoryManga.getMangaCategories
import ir.armor.tachidesk.impl.backup.BackupFlags
import ir.armor.tachidesk.impl.backup.legacy.models.Backup
import ir.armor.tachidesk.impl.backup.legacy.models.Backup.CURRENT_VERSION
import ir.armor.tachidesk.impl.backup.models.CategoryImpl
import ir.armor.tachidesk.impl.backup.models.ChapterImpl
import ir.armor.tachidesk.impl.backup.models.Manga
import ir.armor.tachidesk.impl.backup.models.MangaImpl
import ir.armor.tachidesk.impl.util.GetHttpSource.getHttpSource
import ir.armor.tachidesk.model.database.ChapterTable
import ir.armor.tachidesk.model.database.MangaTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object LegacyBackupExport : LegacyBackupBase() {

    suspend fun createLegacyBackup(flags: BackupFlags): String? {
        // Create root object
        val root = JsonObject()

        // Create manga array
        val mangaEntries = JsonArray()

        // Create category array
        val categoryEntries = JsonArray()

        // Create extension ID/name mapping
        val extensionEntries = JsonArray()

        // Add values to root
        root[Backup.VERSION] = CURRENT_VERSION
        root[Backup.MANGAS] = mangaEntries
        root[Backup.CATEGORIES] = categoryEntries
        root[Backup.EXTENSIONS] = extensionEntries

        transaction {
            val mangas = MangaTable.select { (MangaTable.inLibrary eq true) }

            val extensions: MutableSet<String> = mutableSetOf()

            // Backup library manga and its dependencies
            mangas.map {
                MangaImpl.fromQuery(it)
            }.forEach { manga ->

                mangaEntries.add(backupMangaObject(manga, flags))

                // Maintain set of extensions/sources used (excludes local source)
                if (manga.source != LocalSource.ID) {
                    getHttpSource(manga.source).let {
                        extensions.add("${it.id}:${it.name}")
                    }
                }
            }

            // Backup categories
            if (flags.includeCategories) {
                backupCategories(categoryEntries)
            }

            // Backup extension ID/name mapping
            backupExtensionInfo(extensionEntries, extensions)
        }

        return parser.toJson(root)
    }

    private fun backupMangaObject(manga: Manga, options: BackupFlags): JsonElement {
        // Entry for this manga
        val entry = JsonObject()

        // Backup manga fields
        entry[Backup.MANGA] = parser.toJsonTree(manga)
        val mangaId = manga.id!!.toInt()

        // Check if user wants chapter information in backup
        if (options.includeChapters) {
            // Backup all the chapters
            val chapters = ChapterTable.select { ChapterTable.manga eq mangaId }.map { ChapterImpl.fromQuery(it) }
            if (chapters.count() > 0) {
                val chaptersJson = parser.toJsonTree(chapters)
                if (chaptersJson.asJsonArray.size() > 0) {
                    entry[Backup.CHAPTERS] = chaptersJson
                }
            }
        }

        // Check if user wants category information in backup
        if (options.includeCategories) {
            // Backup categories for this manga
            val categoriesForManga = getMangaCategories(mangaId)
            if (categoriesForManga.isNotEmpty()) {
                val categoriesNames = categoriesForManga.map { it.name }
                entry[Backup.CATEGORIES] = parser.toJsonTree(categoriesNames)
            }
        }

        // Check if user wants track information in backup
        if (options.includeTracking) { // TODO
//            val tracks = databaseHelper.getTracks(manga).executeAsBlocking()
//            if (tracks.isNotEmpty()) {
//                entry[TRACK] = parser.toJsonTree(tracks)
//            }
        }
//
//        // Check if user wants history information in backup
        if (options.includeHistory) { // TODO
//            val historyForManga = databaseHelper.getHistoryByMangaId(manga.id!!).executeAsBlocking()
//            if (historyForManga.isNotEmpty()) {
//                val historyData = historyForManga.mapNotNull { history ->
//                    val url = databaseHelper.getChapter(history.chapter_id).executeAsBlocking()?.url
//                    url?.let { DHistory(url, history.last_read) }
//                }
//                val historyJson = parser.toJsonTree(historyData)
//                if (historyJson.asJsonArray.size() > 0) {
//                    entry[HISTORY] = historyJson
//                }
//            }
        }

        return entry
    }

    private fun backupCategories(root: JsonArray) {
        val categories = getCategoryList().map{
            CategoryImpl().apply {
                name = it.name
                order = it.order
            }
        }
        categories.forEach { root.add(parser.toJsonTree(it)) }
    }

    private fun backupExtensionInfo(root: JsonArray, extensions: Set<String>) {
        extensions.sorted().forEach {
            root.add(it)
        }
    }
}
