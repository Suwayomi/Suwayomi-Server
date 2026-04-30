package suwayomi.tachidesk.manga.impl.kindle

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.NotificationService
import suwayomi.tachidesk.manga.impl.ebook.EpubBuilder
import suwayomi.tachidesk.manga.impl.ebook.ImageRecompressor
import suwayomi.tachidesk.manga.impl.email.EmailSender
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.KindleSendQueueTable
import suwayomi.tachidesk.manga.model.table.MangaKindleConfigTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import java.time.Instant

/**
 * Domain service for the Kindle queue. The actual periodic dispatch
 * lives in [KindleSendWorker]; this object encapsulates the per-row
 * mechanics (enqueue, find next, mark sent/failed, build EPUB, etc.)
 * so the worker stays small.
 */
object KindleSendService {
    private val logger = KotlinLogging.logger {}

    enum class Status { PENDING, SENDING, SENT, FAILED, TOO_LARGE }
    enum class Trigger { AUTO, MANUAL }

    data class QueueRow(
        val id: Int,
        val chapterId: Int,
        val mangaId: Int,
        val mangaTitle: String,
        val chapterName: String,
        val status: Status,
        val attempts: Int,
        val triggerSource: Trigger,
        val destination: String?,
        val lastError: String?,
        val enqueuedAt: Long,
        val lastAttemptAt: Long?,
        val nextAttemptAt: Long,
    )

    /**
     * Push a chapter onto the queue. No-op if there's already a
     * non-terminal row (PENDING/SENDING) for the same chapter.
     */
    fun enqueue(
        chapterId: Int,
        trigger: Trigger,
        destination: String? = null,
    ): Int? {
        val now = Instant.now().toEpochMilli()
        return transaction {
            // Skip if already queued and not finished.
            val existing =
                KindleSendQueueTable
                    .selectAll()
                    .where { KindleSendQueueTable.chapterRef eq chapterId }
                    .firstOrNull { row ->
                        val s = row[KindleSendQueueTable.status]
                        s == Status.PENDING.name || s == Status.SENDING.name
                    }
            if (existing != null) return@transaction null

            KindleSendQueueTable
                .insertAndGetId {
                    it[chapterRef] = chapterId
                    it[status] = Status.PENDING.name
                    it[attempts] = 0
                    it[triggerSource] = trigger.name
                    it[KindleSendQueueTable.destination] = destination
                    it[enqueuedAt] = now
                    it[nextAttemptAt] = now
                }.value
        }
    }

    fun list(): List<QueueRow> =
        transaction {
            (KindleSendQueueTable innerJoin ChapterTable innerJoin MangaTable)
                .selectAll()
                .orderBy(KindleSendQueueTable.enqueuedAt)
                .map { row ->
                    QueueRow(
                        id = row[KindleSendQueueTable.id].value,
                        chapterId = row[KindleSendQueueTable.chapterRef].value,
                        mangaId = row[ChapterTable.manga].value,
                        mangaTitle = row[MangaTable.title],
                        chapterName = row[ChapterTable.name],
                        status = Status.valueOf(row[KindleSendQueueTable.status]),
                        attempts = row[KindleSendQueueTable.attempts],
                        triggerSource = Trigger.valueOf(row[KindleSendQueueTable.triggerSource]),
                        destination = row[KindleSendQueueTable.destination],
                        lastError = row[KindleSendQueueTable.lastError],
                        enqueuedAt = row[KindleSendQueueTable.enqueuedAt],
                        lastAttemptAt = row[KindleSendQueueTable.lastAttemptAt],
                        nextAttemptAt = row[KindleSendQueueTable.nextAttemptAt],
                    )
                }
        }

    fun cancel(id: Int): Boolean =
        transaction {
            KindleSendQueueTable.deleteWhere { KindleSendQueueTable.id eq id } > 0
        }

    fun retry(id: Int): Boolean {
        val now = Instant.now().toEpochMilli()
        return transaction {
            KindleSendQueueTable.update({ KindleSendQueueTable.id eq id }) {
                it[status] = Status.PENDING.name
                it[lastError] = null
                it[nextAttemptAt] = now
            } > 0
        }
    }

    /**
     * Reserves the next runnable row by flipping it to SENDING in a
     * single transaction. Returns null when nothing is due.
     */
    fun reserveNext(): Int? {
        val now = Instant.now().toEpochMilli()
        return transaction {
            val candidate =
                KindleSendQueueTable
                    .selectAll()
                    .where {
                        (KindleSendQueueTable.status eq Status.PENDING.name) and
                            (KindleSendQueueTable.nextAttemptAt lessEq now)
                    }.orderBy(KindleSendQueueTable.enqueuedAt)
                    .limit(1)
                    .firstOrNull() ?: return@transaction null
            val id = candidate[KindleSendQueueTable.id].value
            KindleSendQueueTable.update({ KindleSendQueueTable.id eq id }) {
                it[status] = Status.SENDING.name
                it[lastAttemptAt] = now
                it[attempts] = candidate[KindleSendQueueTable.attempts] + 1
            }
            id
        }
    }

    /**
     * Builds the EPUB for the chapter referenced by [queueId] and
     * sends it to Kindle. Updates the queue row to SENT/FAILED/TOO_LARGE
     * and emits a Telegram notification when configured.
     */
    suspend fun process(queueId: Int) {
        val info =
            transaction {
                (KindleSendQueueTable innerJoin ChapterTable innerJoin MangaTable)
                    .selectAll()
                    .where { KindleSendQueueTable.id eq queueId }
                    .firstOrNull()
                    ?.let { row ->
                        Triple(
                            row[KindleSendQueueTable.chapterRef].value,
                            row[ChapterTable.manga].value,
                            row[KindleSendQueueTable.destination],
                        )
                    }
            } ?: return
        val (chapterId, mangaId, queueDestination) = info

        val mangaData = transaction { MangaTable.toDataClass(MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()) }
        val chapterName =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.id eq chapterId }
                    .first()[ChapterTable.name]
            }
        val destination =
            queueDestination?.takeIf { it.isNotBlank() }
                ?: perMangaDestination(mangaId)
                ?: serverConfig.kindleEmail.value

        if (destination.isBlank()) {
            markFailed(queueId, "No Kindle email configured")
            return
        }

        val pages =
            try {
                EpubBuilder.pagesFromChapter(mangaId, chapterId)
            } catch (e: Exception) {
                markFailed(queueId, "Could not read chapter pages: ${e.message}")
                return
            }

        val rtl = serverConfig.ebookRtl.value
        var epubBytes = EpubBuilder.build(mangaData.title, mangaData.author, pages, rtl)

        val limit = serverConfig.smtpAttachmentLimitBytes.value
        if (epubBytes.size > limit) {
            val shrunk =
                ImageRecompressor.shrinkToFit(
                    pages = pages,
                    bookTitle = mangaData.title,
                    author = mangaData.author,
                    rtl = rtl,
                    limitBytes = limit,
                )
            if (shrunk == null) {
                markFailed(queueId, "Even after recompression the file exceeds ${limit} bytes", asTooLarge = true)
                notifySendFailed(mangaData, chapterName, "too large")
                return
            }
            epubBytes = shrunk
        }

        val filename = sanitizeFilename("${mangaData.title} - $chapterName.epub")

        try {
            EmailSender.send(
                toEmails = listOf(destination),
                subject = "${mangaData.title} - $chapterName",
                bodyText = "Sent by Suwayomi-Enhanced.",
                attachments =
                    listOf(
                        EmailSender.Attachment(
                            data = epubBytes,
                            filename = filename,
                            mime = "application/epub+zip",
                        ),
                    ),
            )
        } catch (e: Exception) {
            scheduleRetryOrFail(queueId, e.message ?: e.toString(), mangaData, chapterName)
            return
        }

        markSent(queueId)
        notifySendSuccess(mangaData, chapterName)
    }

    private fun perMangaDestination(mangaId: Int): String? =
        transaction {
            MangaKindleConfigTable
                .selectAll()
                .where { MangaKindleConfigTable.mangaRef eq mangaId }
                .firstOrNull()
                ?.get(MangaKindleConfigTable.destination)
                ?.takeIf { it.isNotBlank() }
        }

    private fun markSent(queueId: Int) {
        transaction {
            KindleSendQueueTable.update({ KindleSendQueueTable.id eq queueId }) {
                it[status] = Status.SENT.name
                it[lastError] = null
            }
        }
    }

    private fun markFailed(
        queueId: Int,
        reason: String,
        asTooLarge: Boolean = false,
    ) {
        transaction {
            KindleSendQueueTable.update({ KindleSendQueueTable.id eq queueId }) {
                it[status] = if (asTooLarge) Status.TOO_LARGE.name else Status.FAILED.name
                it[lastError] = reason.take(1000)
            }
        }
    }

    private fun scheduleRetryOrFail(
        queueId: Int,
        reason: String,
        manga: MangaDataClass,
        chapterName: String,
    ) {
        // Backoff steps: +5m, +30m, +1h. After 3 attempts -> FAILED.
        val backoffMillis =
            listOf(
                5L * 60_000,
                30L * 60_000,
                60L * 60_000,
            )
        transaction {
            val current =
                KindleSendQueueTable
                    .selectAll()
                    .where { KindleSendQueueTable.id eq queueId }
                    .first()
            val attempts = current[KindleSendQueueTable.attempts]
            val now = Instant.now().toEpochMilli()
            if (attempts >= backoffMillis.size) {
                KindleSendQueueTable.update({ KindleSendQueueTable.id eq queueId }) {
                    it[status] = Status.FAILED.name
                    it[lastError] = reason.take(1000)
                }
                logger.warn { "Kindle send queueId=$queueId exhausted retries: $reason" }
                NotificationService.runCatching {
                    /* notify outside tx — we'll do it post-tx */
                }
            } else {
                val delay = backoffMillis[(attempts - 1).coerceIn(0, backoffMillis.lastIndex)]
                KindleSendQueueTable.update({ KindleSendQueueTable.id eq queueId }) {
                    it[status] = Status.PENDING.name
                    it[lastError] = reason.take(1000)
                    it[nextAttemptAt] = now + delay
                }
                logger.info {
                    "Kindle send queueId=$queueId failed (attempt $attempts), retrying in ${delay / 60000}m"
                }
            }
        }
        // Notify on final failure.
        val finalRow =
            transaction {
                KindleSendQueueTable
                    .selectAll()
                    .where { KindleSendQueueTable.id eq queueId }
                    .firstOrNull()
            }
        if (finalRow != null && finalRow[KindleSendQueueTable.status] == Status.FAILED.name) {
            notifySendFailed(manga, chapterName, reason)
        }
    }

    private fun notifySendSuccess(
        manga: MangaDataClass,
        chapterName: String,
    ) {
        if (!serverConfig.notifyOnKindleSend.value) return
        if (!serverConfig.telegramNotificationsEnabled.value) return
        runCatching {
            NotificationService.notifyKindleSent(manga, chapterName)
        }.onFailure { logger.warn(it) { "Telegram kindle-success notify failed" } }
    }

    private fun notifySendFailed(
        manga: MangaDataClass,
        chapterName: String,
        reason: String,
    ) {
        if (!serverConfig.telegramNotificationsEnabled.value) return
        runCatching {
            NotificationService.notifyKindleFailed(manga, chapterName, reason)
        }.onFailure { logger.warn(it) { "Telegram kindle-failure notify failed" } }
    }

    private fun sanitizeFilename(name: String): String =
        name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(200)
            .ifBlank { "chapter.epub" }
}
