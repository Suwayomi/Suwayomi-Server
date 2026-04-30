/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.email.EmailSender
import suwayomi.tachidesk.manga.impl.kindle.KindleSendService
import suwayomi.tachidesk.manga.impl.kindle.MangaKindleConfig
import suwayomi.tachidesk.manga.impl.util.SecretBox
import suwayomi.tachidesk.server.serverConfig

class KindleMutation {
    data class SendChapterToKindleInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val destination: String? = null,
    )

    data class SendChapterToKindlePayload(
        val clientMutationId: String?,
        val queueEntryId: Int?,
        val alreadyQueued: Boolean,
    )

    @RequireAuth
    fun sendChapterToKindle(input: SendChapterToKindleInput): DataFetcherResult<SendChapterToKindlePayload?> =
        asDataFetcherResult {
            val (cmid, chapterId, destination) = input
            val id =
                KindleSendService.enqueue(
                    chapterId = chapterId,
                    trigger = KindleSendService.Trigger.MANUAL,
                    destination = destination,
                )
            SendChapterToKindlePayload(cmid, id, alreadyQueued = id == null)
        }

    data class CancelKindleQueueEntryInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class CancelKindleQueueEntryPayload(
        val clientMutationId: String?,
        val cancelled: Boolean,
    )

    @RequireAuth
    fun cancelKindleQueueEntry(input: CancelKindleQueueEntryInput): DataFetcherResult<CancelKindleQueueEntryPayload?> =
        asDataFetcherResult {
            CancelKindleQueueEntryPayload(input.clientMutationId, KindleSendService.cancel(input.id))
        }

    data class RetryKindleQueueEntryInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class RetryKindleQueueEntryPayload(
        val clientMutationId: String?,
        val retried: Boolean,
    )

    @RequireAuth
    fun retryKindleQueueEntry(input: RetryKindleQueueEntryInput): DataFetcherResult<RetryKindleQueueEntryPayload?> =
        asDataFetcherResult {
            RetryKindleQueueEntryPayload(input.clientMutationId, KindleSendService.retry(input.id))
        }

    data class SetMangaKindleConfigInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val autoSend: Boolean,
        val destination: String? = null,
    )

    data class SetMangaKindleConfigPayload(
        val clientMutationId: String?,
        val mangaId: Int,
        val autoSend: Boolean,
        val destination: String?,
    )

    @RequireAuth
    fun setMangaKindleConfig(input: SetMangaKindleConfigInput): DataFetcherResult<SetMangaKindleConfigPayload?> =
        asDataFetcherResult {
            val (cmid, mangaId, autoSend, destination) = input
            val saved = MangaKindleConfig.upsert(mangaId, autoSend, destination)
            SetMangaKindleConfigPayload(cmid, saved.mangaId, saved.autoSend, saved.destination)
        }

    data class SetSmtpPasswordInput(
        val clientMutationId: String? = null,
        /** Plaintext password from the WebUI. Server encrypts before persisting. */
        val password: String,
    )

    data class SetSmtpPasswordPayload(
        val clientMutationId: String?,
        val saved: Boolean,
    )

    @RequireAuth
    fun setSmtpPassword(input: SetSmtpPasswordInput): DataFetcherResult<SetSmtpPasswordPayload?> =
        asDataFetcherResult {
            // App passwords / SMTP credentials never legitimately contain
            // whitespace. Strip aggressively so paste-from-Google ('abcd efgh
            // ijkl mnop' grouped output) doesn't bite the user.
            val sanitized = input.password.replace("\\s".toRegex(), "")
            val encrypted = if (sanitized.isBlank()) "" else SecretBox.encrypt(sanitized)
            serverConfig.smtpPasswordEncrypted.value = encrypted
            SetSmtpPasswordPayload(input.clientMutationId, true)
        }

    data class SendTestEmailInput(
        val clientMutationId: String? = null,
        val destination: String? = null,
    )

    data class SendTestEmailPayload(
        val clientMutationId: String?,
        val sent: Boolean,
        val message: String?,
    )

    @RequireAuth
    fun sendTestEmail(input: SendTestEmailInput): DataFetcherResult<SendTestEmailPayload?> =
        asDataFetcherResult {
            val to = input.destination?.takeIf { it.isNotBlank() } ?: serverConfig.kindleEmail.value
            require(to.isNotBlank()) { "No destination email" }

            // Diagnostic: include the decrypted password length in the
            // error path so the user can confirm what's actually saved
            // (Google App Passwords are 16 chars). We never include the
            // value itself.
            val savedPwLen =
                runCatching {
                    SecretBox.decrypt(serverConfig.smtpPasswordEncrypted.value).length
                }.getOrDefault(-1)

            val (sent, message) =
                runCatching {
                    EmailSender.send(
                        toEmails = listOf(to),
                        subject = "Suwayomi-Enhanced SMTP test",
                        bodyText = "If you are reading this, your SMTP configuration is working.",
                    )
                }.fold(
                    onSuccess = { true to null },
                    onFailure = {
                        val raw = it.message ?: it.toString()
                        false to "$raw\n\nDiagnostic: stored password length=$savedPwLen (Gmail app password = 16)."
                    },
                )
            SendTestEmailPayload(input.clientMutationId, sent, message)
        }
}
