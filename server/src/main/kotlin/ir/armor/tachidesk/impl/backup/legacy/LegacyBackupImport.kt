package ir.armor.tachidesk.impl.backup.legacy

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import ir.armor.tachidesk.impl.backup.legacy.LegacyBackupRestoreValidator.ValidationResult
import ir.armor.tachidesk.impl.backup.legacy.LegacyBackupRestoreValidator.validate
import ir.armor.tachidesk.impl.backup.legacy.models.Backup
import ir.armor.tachidesk.impl.backup.legacy.models.DHistory
import ir.armor.tachidesk.impl.backup.models.Chapter
import ir.armor.tachidesk.impl.backup.models.ChapterImpl
import ir.armor.tachidesk.impl.backup.models.Manga
import ir.armor.tachidesk.impl.backup.models.MangaImpl
import ir.armor.tachidesk.impl.backup.models.Track
import ir.armor.tachidesk.impl.backup.models.TrackImpl
import ir.armor.tachidesk.impl.util.GetHttpSource.getHttpSource
import ir.armor.tachidesk.impl.util.awaitSingle
import ir.armor.tachidesk.model.database.MangaTable
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.InputStream
import java.util.Date

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

private val logger = KotlinLogging.logger {}

object LegacyBackupImport : LegacyBackupBase() {
    suspend fun restoreLegacyBackup(sourceStream: InputStream): ValidationResult {
        val reader = sourceStream.bufferedReader()
        val json = JsonParser.parseReader(reader).asJsonObject

        val validationResult = validate(json)

        val mangasJson = json.get(Backup.MANGAS).asJsonArray

        // Restore categories
        json.get(Backup.CATEGORIES)?.let { restoreCategories(it) }

        // Store source mapping for error messages
        sourceMapping = LegacyBackupRestoreValidator.getSourceMapping(json)

        // Restore individual manga
        mangasJson.forEach {
            restoreManga(it.asJsonObject)
        }

        logger.info {
            """
                Restore Errors:
                ${
            errors.map {
                "${it.first} - ${it.second}"
            }.joinToString("\n")
            }
                Restore Summary:
                - Missing Sources:
                ${validationResult.missingSources.joinToString("\n")}
                - Missing Trackers:
                ${validationResult.missingTrackers.joinToString("\n")}
            """.trimIndent()
        }

        return validationResult
    }

    private fun restoreCategories(categoriesJson: JsonElement) { // TODO
//        db.inTransaction {
//            backupManager.restoreCategories(categoriesJson.asJsonArray)
//        }
//
//        restoreProgress += 1
//        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.categories))
    }

    private suspend fun restoreManga(mangaJson: JsonObject) {
        val manga = parser.fromJson<MangaImpl>(
            mangaJson.get(
                Backup.MANGA
            )
        )
        val chapters = parser.fromJson<List<ChapterImpl>>(
            mangaJson.get(Backup.CHAPTERS)
                ?: JsonArray()
        )
        val categories = parser.fromJson<List<String>>(
            mangaJson.get(Backup.CATEGORIES)
                ?: JsonArray()
        )
        val history = parser.fromJson<List<DHistory>>(
            mangaJson.get(Backup.HISTORY)
                ?: JsonArray()
        )
        val tracks = parser.fromJson<List<TrackImpl>>(
            mangaJson.get(Backup.TRACK)
                ?: JsonArray()
        )

        val source = try {
            getHttpSource(manga.source)
        } catch (e: NullPointerException) {
            null
        }
        val sourceName = sourceMapping[manga.source] ?: manga.source.toString()

        logger.debug("Restoring Manga: ${manga.title} from $sourceName")

        try {
            if (source != null) {
                restoreMangaData(manga, source, chapters, categories, history, tracks)
            } else {
                errors.add(Date() to "${manga.title} [$sourceName]: Source not found: $sourceName (${manga.source})")
            }
        } catch (e: Exception) {
            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
        }
    }

    /**
     * @param manga manga data from json
     * @param source source to get manga data from
     * @param chapters chapters data from json
     * @param categories categories data from json
     * @param history history data from json
     * @param tracks tracking data from json
     */
    private suspend fun restoreMangaData(
        manga: Manga,
        source: Source,
        chapters: List<Chapter>,
        categories: List<String>,
        history: List<DHistory>,
        tracks: List<Track>
    ) {
        fetchManga(source, manga)

//        updateChapters(source, fetchedManga, chapters)

//        backupManager.restoreCategoriesForManga(manga, categories)

//        backupManager.restoreHistoryForManga(history)

//        backupManager.restoreTrackForManga(manga, tracks)

//        updateTracking(fetchedManga, tracks)
    }

    /**
     * Fetches manga information
     *
     * @param source source of manga
     * @param manga manga that needs updating
     * @return Updated manga.
     */
    private suspend fun fetchManga(source: Source, manga: Manga): SManga {
        transaction {
            if (MangaTable.select { (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }.firstOrNull() == null) {
                MangaTable.insert {
                    it[url] = manga.url
                    it[title] = manga.title

                    it[sourceReference] = manga.source
                }
            }
            MangaTable.update({ (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }) {
                it[MangaTable.inLibrary] = true
            }
        }

        val fetchedManga = source.fetchMangaDetails(manga).awaitSingle()

        transaction {
            MangaTable.update({ (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }) {

                it[artist] = fetchedManga.artist
                it[author] = fetchedManga.author
                it[description] = fetchedManga.description
                it[genre] = fetchedManga.genre
                it[status] = fetchedManga.status
                if (fetchedManga.thumbnail_url != null && fetchedManga.thumbnail_url!!.isNotEmpty())
                    it[MangaTable.thumbnail_url] = fetchedManga.thumbnail_url

            }
        }

        return fetchedManga
    }
}
