package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder

object NotificationService {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonMime = "application/json; charset=utf-8".toMediaType()

    /**
     * Notify the user that new chapters have appeared for a manga.
     * Currently dispatches via Telegram only; the function is fire-and-forget
     * so callers in the update loop are not blocked by network latency.
     */
    fun notifyNewChapters(
        manga: MangaDataClass,
        newChapters: List<ChapterDataClass>,
    ) {
        if (newChapters.isEmpty()) return
        if (!serverConfig.telegramNotificationsEnabled.value) return
        scope.launch {
            runCatching { sendTelegram(manga, newChapters) }
                .onFailure { logger.warn(it) { "Failed to send notification for mangaId=${manga.id}" } }
        }
    }

    /**
     * Notify when a chapter has been successfully shipped to Kindle.
     * Fire-and-forget like notifyNewChapters.
     */
    fun notifyKindleSent(
        manga: MangaDataClass,
        chapterName: String,
    ) {
        if (!serverConfig.telegramNotificationsEnabled.value) return
        scope.launch {
            runCatching {
                sendTelegramRaw("📧 Sent to Kindle: *${manga.title}* — $chapterName")
            }.onFailure { logger.warn(it) { "Failed Telegram kindle-success message" } }
        }
    }

    /** Inverse of [notifyKindleSent] — for failures the user should see. */
    fun notifyKindleFailed(
        manga: MangaDataClass,
        chapterName: String,
        reason: String,
    ) {
        if (!serverConfig.telegramNotificationsEnabled.value) return
        scope.launch {
            runCatching {
                sendTelegramRaw("⚠️ Kindle send failed: *${manga.title}* — $chapterName\n$reason")
            }.onFailure { logger.warn(it) { "Failed Telegram kindle-failure message" } }
        }
    }

    /**
     * Send a one-shot test message so the user can verify their bot
     * credentials without waiting for an actual chapter update.
     */
    suspend fun sendTestMessage(): Boolean {
        val text = "Suwayomi-Enhanced notification test ✓"
        return runCatching { sendTelegramRaw(text) }
            .onFailure { logger.warn(it) { "Telegram test message failed" } }
            .isSuccess
    }

    private suspend fun sendTelegram(
        manga: MangaDataClass,
        newChapters: List<ChapterDataClass>,
    ) {
        val title = manga.title
        val count = newChapters.size
        val sample =
            newChapters
                .take(5)
                .joinToString("\n") { "• ${it.name}" }
        val more = if (count > 5) "\n… and ${count - 5} more" else ""
        val text = "📚 New chapter${if (count == 1) "" else "s"} for *$title*\n\n$sample$more"
        sendTelegramRaw(text)
    }

    private suspend fun sendTelegramRaw(text: String) {
        val token = serverConfig.telegramBotToken.value.trim()
        val chatId = serverConfig.telegramChatId.value.trim()
        require(token.isNotBlank()) { "Telegram bot token is not configured" }
        require(chatId.isNotBlank()) { "Telegram chat id is not configured" }

        val url = "https://api.telegram.org/bot$token/sendMessage"
        val payload =
            "{\"chat_id\":${jsonString(chatId)},\"text\":${jsonString(text)},\"parse_mode\":\"Markdown\"}"
        val response =
            network.client
                .newCall(POST(url, body = payload.toRequestBody(jsonMime)))
                .execute()
        response.use {
            if (!it.isSuccessful) {
                throw RuntimeException("Telegram returned HTTP ${it.code}: ${it.body.string()}")
            }
        }
    }

    private fun jsonString(value: String): String {
        // Minimal JSON string escape for the few fields we send.
        val escaped =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        return "\"$escaped\""
    }

    @Suppress("unused")
    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
