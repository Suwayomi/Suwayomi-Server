/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.email.EmailPresets
import suwayomi.tachidesk.manga.impl.kindle.KindleSendService
import suwayomi.tachidesk.manga.impl.kindle.MangaKindleConfig

class KindleQuery {
    data class KindleQueueEntry(
        val id: Int,
        val chapterId: Int,
        val mangaId: Int,
        val mangaTitle: String,
        val chapterName: String,
        val status: String,
        val attempts: Int,
        val triggerSource: String,
        val destination: String?,
        val lastError: String?,
        val enqueuedAt: Long,
        val lastAttemptAt: Long?,
        val nextAttemptAt: Long,
    )

    data class EmailPresetEntry(
        val id: String,
        val displayName: String,
        val host: String,
        val port: Int,
        val useStartTls: Boolean,
    )

    data class MangaKindleConfigEntry(
        val mangaId: Int,
        val autoSend: Boolean,
        val destination: String?,
    )

    @RequireAuth
    fun kindleQueue(): List<KindleQueueEntry> =
        KindleSendService.list().map {
            KindleQueueEntry(
                id = it.id,
                chapterId = it.chapterId,
                mangaId = it.mangaId,
                mangaTitle = it.mangaTitle,
                chapterName = it.chapterName,
                status = it.status.name,
                attempts = it.attempts,
                triggerSource = it.triggerSource.name,
                destination = it.destination,
                lastError = it.lastError,
                enqueuedAt = it.enqueuedAt,
                lastAttemptAt = it.lastAttemptAt,
                nextAttemptAt = it.nextAttemptAt,
            )
        }

    @RequireAuth
    fun emailPresets(): List<EmailPresetEntry> =
        EmailPresets.presets.map {
            EmailPresetEntry(it.id, it.displayName, it.host, it.port, it.useStartTls)
        }

    @RequireAuth
    fun mangaKindleConfig(mangaId: Int): MangaKindleConfigEntry =
        MangaKindleConfig.get(mangaId)?.let {
            MangaKindleConfigEntry(it.mangaId, it.autoSend, it.destination)
        } ?: MangaKindleConfigEntry(mangaId, autoSend = false, destination = null)
}
