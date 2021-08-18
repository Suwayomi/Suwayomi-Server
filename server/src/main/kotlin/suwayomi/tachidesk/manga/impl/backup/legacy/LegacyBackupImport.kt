package suwayomi.tachidesk.manga.impl.backup.legacy

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Category.createCategory
import suwayomi.tachidesk.manga.impl.Category.getCategoryList
import suwayomi.tachidesk.manga.impl.backup.AbstractBackupValidator.ValidationResult
import suwayomi.tachidesk.manga.impl.backup.legacy.LegacyBackupValidator.validate
import suwayomi.tachidesk.manga.impl.backup.legacy.models.Backup
import suwayomi.tachidesk.manga.impl.backup.legacy.models.DHistory
import suwayomi.tachidesk.manga.impl.backup.models.CategoryImpl
import suwayomi.tachidesk.manga.impl.backup.models.Chapter
import suwayomi.tachidesk.manga.impl.backup.models.ChapterImpl
import suwayomi.tachidesk.manga.impl.backup.models.Manga
import suwayomi.tachidesk.manga.impl.backup.models.MangaImpl
import suwayomi.tachidesk.manga.impl.backup.models.Track
import suwayomi.tachidesk.manga.impl.backup.models.TrackImpl
import suwayomi.tachidesk.manga.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.manga.impl.util.lang.awaitSingle
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.io.InputStream
import java.util.Date

private val logger = KotlinLogging.logger {}

object LegacyBackupImport : LegacyBackupBase() {
    suspend fun performRestore(sourceStream: InputStream): ValidationResult {
        val reader = sourceStream.bufferedReader()
        val json = JsonParser.parseReader(reader).asJsonObject

        val validationResult = validate(json)

        val mangasJson = json.get(Backup.MANGAS).asJsonArray

        // Restore categories
        json.get(Backup.CATEGORIES)?.let { restoreCategories(it) }

        // Store source mapping for error messages
        sourceMapping = LegacyBackupValidator.getSourceMapping(json)

        // Restore individual manga
        mangasJson.forEach {
            restoreManga(it.asJsonObject)
        }

        logger.info {
            """
                Restore Errors:
                ${ errors.joinToString("\n") { "${it.first} - ${it.second}" } }
                Restore Summary:
                - Missing Sources:
                ${validationResult.missingSources.joinToString("\n")}
                - Missing Trackers:
                ${validationResult.missingTrackers.joinToString("\n")}
            """.trimIndent()
        }

        return validationResult
    }

    private fun restoreCategories(jsonCategories: JsonElement) {
        val backupCategories = parser.fromJson<List<CategoryImpl>>(jsonCategories)
        val dbCategories = getCategoryList()

        // Iterate over them and create missing categories
        backupCategories.forEach { category ->
            if (dbCategories.none { it.name == category.name }) {
                createCategory(category.name)
            }
        }
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
        } catch (e: NoSuchElementException) {
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
    @Suppress("UNUSED_PARAMETER")
    private suspend fun restoreMangaData(
        manga: Manga,
        source: Source,
        chapters: List<Chapter>,
        categories: List<String>,
        history: List<DHistory>,
        tracks: List<Track>
    ) {
        val fetchedManga = fetchManga(source, manga)

        updateChapters(source, fetchedManga, chapters)

        // TODO
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
        // make sure we have the manga record in library
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

        // update manga details
        val fetchedManga = source.fetchMangaDetails(manga).awaitSingle()
        transaction {
            MangaTable.update({ (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }) {

                it[artist] = fetchedManga.artist
                it[author] = fetchedManga.author
                it[description] = fetchedManga.description
                it[genre] = fetchedManga.genre
                it[status] = fetchedManga.status
                if (fetchedManga.thumbnail_url != null && fetchedManga.thumbnail_url.orEmpty().isNotEmpty())
                    it[MangaTable.thumbnail_url] = fetchedManga.thumbnail_url
            }
        }

        return fetchedManga
    }

    @Suppress("UNUSED_PARAMETER") // TODO: remove this suppress when update Chapters is written
    private fun updateChapters(source: Source, fetchedManga: SManga, chapters: List<Chapter>) {
        // TODO("Not yet implemented")
    }
}
