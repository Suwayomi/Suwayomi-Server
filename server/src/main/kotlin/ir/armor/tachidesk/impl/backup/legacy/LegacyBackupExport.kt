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
import ir.armor.tachidesk.impl.backup.BackupFlags
import ir.armor.tachidesk.impl.backup.legacy.models.Backup
import ir.armor.tachidesk.impl.backup.legacy.models.Backup.CURRENT_VERSION
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

        // Check if user wants chapter information in backup
        if (options.includeChapters && false) { // TODO
            // Backup all the chapters
            val mangaId = manga.id!!.toInt()
            val chapters = ChapterTable.select { ChapterTable.manga eq mangaId }.map { ChapterImpl.fromQuery(it) }
            if (chapters.count() > 0) {
                val chaptersJson = parser.toJsonTree(chapters)
                if (chaptersJson.asJsonArray.size() > 0) {
                    entry[Backup.CHAPTERS] = chaptersJson
                }
            }
        }

        // TODO the rest

        return entry
    }

    private fun backupCategories(root: JsonArray) { // TODO
//        val categories = databaseHelper.getCategories().executeAsBlocking()
//        categories.forEach { root.add(parser.toJsonTree(it)) }
    }

    private fun backupExtensionInfo(root: JsonArray, extensions: Set<String>) {
        extensions.sorted().forEach {
            root.add(it)
        }
    }
}
