package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import mu.KotlinLogging
import okio.buffer
import okio.gzip
import okio.source
import suwayomi.tachidesk.manga.impl.backup.AbstractBackupValidator.ValidationResult
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator.validate
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSerializer
import java.io.InputStream

private val logger = KotlinLogging.logger {}

object ProtoBackupImport: ProtoBackupBase() {
    var restoreAmount = 0

    suspend fun performRestore(sourceStream: InputStream): ValidationResult {

        val backupString = sourceStream.source().gzip().buffer().use { it.readByteArray() }
        val backup = parser.decodeFromByteArray(BackupSerializer, backupString)

        val validationResult = validate(backup)

        restoreAmount = backup.backupManga.size + 1 // +1 for categories

        // Restore categories
        if (backup.backupCategories.isNotEmpty()) {
            restoreCategories(backup.backupCategories)
        }

        // Store source mapping for error messages
        sourceMapping = backup.backupSources.map { it.sourceId to it.name }.toMap()

        // Restore individual manga
        backup.backupManga.forEach {
            restoreManga(it, backup.backupCategories)
        }

        // TODO: optionally trigger online library + tracker update

        return validationResult
    }

    private fun restoreCategories(backupCategories: List<BackupCategory>) { // TODO
//        db.inTransaction {
//            backupManager.restoreCategories(backupCategories)
//        }
//
//        restoreProgress += 1
//        showRestoreProgress(restoreProgress, restoreAmount, context.getString(R.string.categories))
    }


    private fun restoreManga(backupManga: BackupManga, backupCategories: List<BackupCategory>) { // TODO
//        val manga = backupManga.getMangaImpl()
//        val chapters = backupManga.getChaptersImpl()
//        val categories = backupManga.categories
//        val history = backupManga.history
//        val tracks = backupManga.getTrackingImpl()
//
//        try {
//            restoreMangaData(manga, chapters, categories, history, tracks, backupCategories)
//        } catch (e: Exception) {
//            val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
//            errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
//        }
//
//        restoreProgress += 1
//        showRestoreProgress(restoreProgress, restoreAmount, manga.title)
    }
}