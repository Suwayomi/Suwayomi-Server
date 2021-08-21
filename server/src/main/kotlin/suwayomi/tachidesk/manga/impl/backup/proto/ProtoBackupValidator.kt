package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okio.buffer
import okio.gzip
import okio.source
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.backup.AbstractBackupValidator
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSerializer
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.io.InputStream

object ProtoBackupValidator : AbstractBackupValidator() {
    fun validate(backup: Backup): ValidationResult {
        if (backup.backupManga.isEmpty()) {
            throw Exception("Backup does not contain any manga.")
        }

        val sources = backup.backupSources.map { it.sourceId to it.name }.toMap()

        val missingSources = transaction {
            sources
                .filter { SourceTable.select { SourceTable.id eq it.key }.firstOrNull() == null }
                .map { "${it.value} (${it.key})" }
                .sorted()
        }

//        val trackers = backup.backupManga
//            .flatMap { it.tracking }
//            .map { it.syncId }
//            .distinct()

        val missingTrackers = listOf("")
//        val missingTrackers = trackers
//            .mapNotNull { trackManager.getService(it) }
//            .filter { !it.isLogged }
//            .map { context.getString(it.nameRes()) }
//            .sorted()

        return ValidationResult(missingSources, missingTrackers)
    }

    suspend fun validate(sourceStream: InputStream): ValidationResult {
        val backupString = sourceStream.source().gzip().buffer().use { it.readByteArray() }
        val backup = ProtoBackupImport.parser.decodeFromByteArray(BackupSerializer, backupString)

        return validate(backup)
    }
}
