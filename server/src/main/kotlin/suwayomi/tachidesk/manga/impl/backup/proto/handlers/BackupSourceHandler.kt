package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.selectAll
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.Source.modifySourceMetas
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSource
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.database.dbTransaction

object BackupSourceHandler {
    fun backup(
        backupMangas: List<BackupManga>,
        flags: BackupFlags,
    ): List<BackupSource> =
        dbTransaction {
            val inLibraryMangaSourceIds =
                backupMangas
                    .asSequence()
                    .map { it.source }
                    .distinct()
                    .toList()
            val sources = SourceTable.selectAll().where { SourceTable.id inList inLibraryMangaSourceIds }
            val sourceToMeta =
                if (flags.includeClientData) {
                    Source.getSourcesMetaMaps(sources.map { it[SourceTable.id].value })
                } else {
                    emptyMap()
                }

            inLibraryMangaSourceIds
                .map { mangaSourceId ->
                    val source = sources.firstOrNull { it[SourceTable.id].value == mangaSourceId }
                    BackupSource(
                        source?.get(SourceTable.name) ?: "",
                        mangaSourceId,
                    ).apply {
                        if (flags.includeClientData) {
                            this.meta = sourceToMeta[mangaSourceId] ?: emptyMap()
                        }
                    }
                }.toList()
        }

    fun restore(backupSources: List<BackupSource>) {
        modifySourceMetas(backupSources.associateBy { it.sourceId }.mapValues { it.value.meta })
    }
}
