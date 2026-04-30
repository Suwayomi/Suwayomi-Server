package suwayomi.tachidesk.manga.impl.kindle

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.model.dataclass.MangaKindleConfigDataClass
import suwayomi.tachidesk.manga.model.table.MangaKindleConfigTable
import suwayomi.tachidesk.server.serverConfig

/**
 * Enqueues newly-inserted chapters into the Kindle send queue when the
 * user opted in (per-manga or via the global auto-send toggle).
 */
object KindleAutoSend {
    private val logger = KotlinLogging.logger {}

    fun maybeEnqueue(
        mangaId: Int,
        chapterIds: List<Int>,
    ) {
        if (chapterIds.isEmpty()) return
        val cfg = MangaKindleConfig.get(mangaId)
        val effectiveAutoSend = cfg?.autoSend == true || serverConfig.kindleAutoSendEnabled.value
        if (!effectiveAutoSend) return
        chapterIds.forEach { chapterId ->
            runCatching {
                KindleSendService.enqueue(
                    chapterId = chapterId,
                    trigger = KindleSendService.Trigger.AUTO,
                    destination = cfg?.destination,
                )
            }.onFailure { logger.warn(it) { "Failed to enqueue chapter $chapterId" } }
        }
    }
}

/** Per-manga Kindle config (auto-send toggle + optional override email). */
object MangaKindleConfig {
    fun get(mangaId: Int): MangaKindleConfigDataClass? =
        transaction {
            MangaKindleConfigTable
                .selectAll()
                .where { MangaKindleConfigTable.mangaRef eq mangaId }
                .firstOrNull()
                ?.let {
                    MangaKindleConfigDataClass(
                        id = it[MangaKindleConfigTable.id].value,
                        mangaId = it[MangaKindleConfigTable.mangaRef].value,
                        autoSend = it[MangaKindleConfigTable.autoSend],
                        destination = it[MangaKindleConfigTable.destination],
                        createdAt = it[MangaKindleConfigTable.createdAt],
                        updatedAt = it[MangaKindleConfigTable.updatedAt],
                    )
                }
        }

    fun upsert(
        mangaId: Int,
        autoSend: Boolean,
        destination: String?,
    ): MangaKindleConfigDataClass {
        val now = System.currentTimeMillis()
        transaction {
            val existing =
                MangaKindleConfigTable
                    .selectAll()
                    .where { MangaKindleConfigTable.mangaRef eq mangaId }
                    .firstOrNull()
            if (existing == null) {
                MangaKindleConfigTable.insertAndGetId {
                    it[mangaRef] = mangaId
                    it[MangaKindleConfigTable.autoSend] = autoSend
                    it[MangaKindleConfigTable.destination] = destination?.takeIf { d -> d.isNotBlank() }
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } else {
                MangaKindleConfigTable.update({ MangaKindleConfigTable.mangaRef eq mangaId }) {
                    it[MangaKindleConfigTable.autoSend] = autoSend
                    it[MangaKindleConfigTable.destination] = destination?.takeIf { d -> d.isNotBlank() }
                    it[updatedAt] = now
                }
            }
        }
        return get(mangaId)!!
    }
}
