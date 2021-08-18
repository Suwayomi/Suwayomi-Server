package suwayomi.tachidesk.manga.impl.backup.legacy

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.google.gson.JsonObject
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.backup.AbstractBackupValidator
import suwayomi.tachidesk.manga.impl.backup.legacy.models.Backup
import suwayomi.tachidesk.manga.model.table.SourceTable

object LegacyBackupValidator : AbstractBackupValidator() {

    /**
     * Checks for critical backup file data.
     *
     * @throws Exception if version or manga cannot be found.
     * @return List of missing sources or missing trackers.
     */
    fun validate(json: JsonObject): ValidationResult {
        val version = json.get(Backup.VERSION)
        val mangasJson = json.get(Backup.MANGAS)
        if (version == null || mangasJson == null) {
            throw Exception("File is missing data.")
        }

        val mangas = mangasJson.asJsonArray
        if (mangas.size() == 0) {
            throw Exception("Backup does not contain any manga.")
        }

        val sources = getSourceMapping(json)
        val missingSources = transaction {
            sources
                .filter { SourceTable.select { SourceTable.id eq it.key }.firstOrNull() == null }
                .map { "${it.value} (${it.key})" }
                .sorted()
        }

//        val trackers = mangas
//            .filter { it.asJsonObject.has("track") }
//            .flatMap { it.asJsonObject["track"].asJsonArray }
//            .map { it.asJsonObject["s"].asInt }
//            .distinct()

        val missingTrackers = listOf("")
//        val missingTrackers = trackers
//                .mapNotNull { trackManager.getService(it) }
//                .filter { !it.isLogged }
//                .map { context.getString(it.nameRes()) }
//                .sorted()

        return ValidationResult(missingSources, missingTrackers)
    }

    fun getSourceMapping(json: JsonObject): Map<Long, String> {
        val extensionsMapping = json.get(Backup.EXTENSIONS) ?: return emptyMap()

        return extensionsMapping.asJsonArray
            .map {
                val items = it.asString.split(":")
                items[0].toLong() to items[1]
            }
            .toMap()
    }
}
