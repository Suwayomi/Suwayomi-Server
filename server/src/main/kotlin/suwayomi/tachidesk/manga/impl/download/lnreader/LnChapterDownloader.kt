package suwayomi.tachidesk.manga.impl.download.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.extension.lnreader.LnNetworkGateway
import suwayomi.tachidesk.manga.impl.text.ChapterTextSanitizer
import suwayomi.tachidesk.manga.impl.text.ChapterTextSource
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toSChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.net.InetAddress
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LnChapterDownloader {
    private val logger = KotlinLogging.logger(LnChapterDownloader::class.java.name)

    const val MAX_IMAGES_PER_CHAPTER = 50
    const val MAX_IMAGE_BYTES = 15 * 1024 * 1024 // 15 MiB per image
    const val MAX_TOTAL_IMAGE_BYTES = 32 * 1024 * 1024 // 32 MiB per chapter
    private const val MAX_IMAGE_REDIRECTS = 5

    internal var customImageClient: OkHttpClient? = null

    const val MAX_LIVE_IMAGE_URL_LENGTH = 2_048
    const val MAX_LIVE_IMAGE_TOKEN_LENGTH = 64

    private val liveIllustrationSigningKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }

    private val defaultImageClient: OkHttpClient by lazy {
        Injekt
            .get<NetworkHelper>()
            .client
            .newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val imageClient: OkHttpClient
        get() = customImageClient ?: defaultImageClient

    suspend fun fetchLiveIllustration(
        chapterId: Int,
        imageUrl: String,
        token: String,
    ): LnEpubAsset {
        val hasHttpScheme =
            imageUrl.startsWith("http://", ignoreCase = true) ||
                imageUrl.startsWith("https://", ignoreCase = true)
        if (
            imageUrl.length > MAX_LIVE_IMAGE_URL_LENGTH ||
            token.length > MAX_LIVE_IMAGE_TOKEN_LENGTH ||
            !hasHttpScheme
        ) {
            throw IllegalArgumentException("Invalid live illustration URL")
        }
        if (!isValidLiveIllustrationToken(chapterId, imageUrl, token)) {
            throw IllegalArgumentException("Invalid live illustration token")
        }

        val mangaRow =
            transaction {
                val chapter =
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.id eq chapterId }
                        .firstOrNull()
                        ?: throw NoSuchElementException("Chapter $chapterId not found")
                val manga =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq chapter[ChapterTable.manga] }
                        .firstOrNull()
                        ?: throw NoSuchElementException("Manga ${chapter[ChapterTable.manga]} not found")
                manga
            }

        if (mangaRow[MangaTable.contentType] != SourceContentType.LIGHT_NOVEL) {
            throw IOException("Chapter $chapterId is not a light novel chapter")
        }

        val source = GetSource.getSourceOrNull(mangaRow[MangaTable.sourceReference])
        if (source !is ChapterTextSource) {
            throw IOException("Source ${mangaRow[MangaTable.sourceReference]} does not support light novel text")
        }

        val httpSource = source as? HttpSource
        return fetchIllustration(
            imageUrl = imageUrl,
            index = 1,
            baseUrl = httpSource?.baseUrl,
            headers = httpSource?.headers ?: Headers.Builder().build(),
            byteLimit = MAX_IMAGE_BYTES,
        )
    }

    fun createLiveIllustrationToken(
        chapterId: Int,
        imageUrl: String,
    ): String = Base64.getUrlEncoder().withoutPadding().encodeToString(liveIllustrationSignature(chapterId, imageUrl))

    private fun isValidLiveIllustrationToken(
        chapterId: Int,
        imageUrl: String,
        token: String,
    ): Boolean {
        val provided = runCatching { Base64.getUrlDecoder().decode(token) }.getOrNull() ?: return false
        return MessageDigest.isEqual(provided, liveIllustrationSignature(chapterId, imageUrl))
    }

    private fun liveIllustrationSignature(
        chapterId: Int,
        imageUrl: String,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(liveIllustrationSigningKey, "HmacSHA256"))
        return mac.doFinal("$chapterId\n$imageUrl".toByteArray(Charsets.UTF_8))
    }

    suspend fun download(
        download: DownloadQueueItem,
        source: ChapterTextSource,
        scope: CoroutineScope,
        step: suspend (DownloadQueueItem?, Boolean) -> Unit,
    ) {
        val chapterId = download.chapterId
        val mangaId = download.mangaId

        val (chapterRow, mangaRow) =
            transaction {
                val chapter = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()
                if (chapter[ChapterTable.pageCount] != 0) {
                    ChapterTable.update({ ChapterTable.id eq chapterId }) { it[pageCount] = 0 }
                }
                Pair(
                    chapter,
                    MangaTable.selectAll().where { MangaTable.id eq mangaId }.first(),
                )
            }

        // Novel prose downloads have 1 logical chapter unit
        download.pageCount = 1
        download.progress = 0f
        step(download, true)

        currentCoroutineContext().ensureActive()

        // Fetch raw text via ChapterTextSource
        val sChapter = chapterRow.toSChapter()
        val rawText = source.getChapterText(sChapter)

        currentCoroutineContext().ensureActive()

        // Normalize & sanitize chapter text
        val baseUrl = (source as? HttpSource)?.baseUrl
        val sanitizedHtml = ChapterTextSanitizer.sanitize(rawText.text, baseUrl)

        // Extract and fetch inline illustrations
        val doc = Jsoup.parseBodyFragment(sanitizedHtml)
        val imgElements = doc.select("img[src]")
        val uniqueUrls =
            imgElements
                .map { it.attr("src").trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_IMAGES_PER_CHAPTER)

        val assets = mutableListOf<LnEpubAsset>()
        val urlToAssetMap = mutableMapOf<String, String>()
        var totalImageBytes = 0

        val requestHeaders =
            (source as? HttpSource)?.headers
                ?: Headers.Builder().build()

        for ((index, imageUrl) in uniqueUrls.withIndex()) {
            currentCoroutineContext().ensureActive()

            val remainingBytes = MAX_TOTAL_IMAGE_BYTES - totalImageBytes
            val downloadedAsset =
                if (remainingBytes > 0) {
                    try {
                        fetchIllustration(
                            imageUrl = imageUrl,
                            index = index + 1,
                            baseUrl = baseUrl,
                            headers = requestHeaders,
                            byteLimit = minOf(MAX_IMAGE_BYTES, remainingBytes),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val reason =
                            if (e is IllustrationDownloadException) {
                                e.message ?: "Illustration download failed"
                            } else {
                                e.javaClass.simpleName.ifBlank { "Exception" }
                            }
                        logger.warn { "Skipping optional illustration for chapter $chapterId: $reason" }
                        null
                    }
                } else {
                    null
                }

            if (downloadedAsset != null) {
                assets.add(downloadedAsset)
                totalImageBytes += downloadedAsset.bytes.size
                urlToAssetMap[imageUrl] = downloadedAsset.name
            }

            download.progress = (index + 1).toFloat() / (uniqueUrls.size + 1)
            step(download, true)
        }

        // Rewrite <img> src to relative safe asset paths
        for (img in imgElements) {
            val originalSrc = img.attr("src").trim()
            val localName = urlToAssetMap[originalSrc]
            if (localName != null) {
                img.attr("src", "assets/$localName")
            } else {
                img.remove()
            }
            img.removeAttr("srcset")
        }

        // 5. Serialize into valid XHTML
        doc
            .outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset(Charsets.UTF_8)
            .prettyPrint(false)

        val xhtmlBody = doc.body().html()

        // 6. Build into temporary EPUB
        val tempFile = LnEpubStore.createTempFile(mangaId, chapterId)
        try {
            LnEpubBuilder.build(
                file = tempFile,
                mangaTitle = mangaRow[MangaTable.title],
                chapterTitle = chapterRow[ChapterTable.name],
                mangaId = mangaId,
                chapterId = chapterId,
                xhtmlBody = xhtmlBody,
                assets = assets,
                language = source.lang,
                customCss = rawText.customCss,
                customJs = rawText.customJs,
            )

            // 7. Validate temporary EPUB
            if (!LnEpubStore.validateEpub(tempFile)) {
                throw IllegalStateException("Generated EPUB for chapter $chapterId failed validation")
            }

            // 8. Atomically promote temp file to canonical target
            val targetFile = LnEpubStore.getFile(mangaId, chapterId)
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            // 9. Compute KOReader hash
            val newHash = KoreaderHelper.hashContents(targetFile)

            // 10. Transactional DB update
            transaction {
                val currentChapter = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()
                val oldHash = currentChapter[ChapterTable.koreaderHash]
                val currentMemo = currentChapter[ChapterTable.memo].toMutableMap()
                val existingText =
                    currentMemo["suwayomi.text"]?.let {
                        if (it is JsonObject) it else null
                    }

                // If hash changed, invalidate stale opaque KOReader progress
                if (existingText != null && oldHash != null && newHash != null && oldHash != newHash) {
                    val progressHash = existingText["koreaderProgressHash"]?.jsonPrimitive?.contentOrNull
                    if (progressHash != null && progressHash != newHash) {
                        val cleanedText =
                            buildJsonObject {
                                existingText.forEach { (k, v) ->
                                    if (k != "koreaderProgress" && k != "koreaderProgressHash") {
                                        put(k, v)
                                    }
                                }
                            }
                        currentMemo["suwayomi.text"] = cleanedText
                    }
                }

                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[isDownloaded] = true
                    if (newHash != null) {
                        it[koreaderHash] = newHash
                    }
                    it[memo] = JsonObject(currentMemo)
                }
            }

            // 11. Complete progress
            download.progress = download.pageCount.toFloat()
            step(download, true)
        } catch (e: Exception) {
            // Clean up temporary file on failure; previous valid targetFile remains untouched
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    private fun fetchIllustration(
        imageUrl: String,
        index: Int,
        baseUrl: String?,
        headers: Headers,
        byteLimit: Int,
    ): LnEpubAsset {
        if (byteLimit <= 0) throw IllustrationDownloadException("Illustration byte budget exhausted")

        // Handle inline data URI, rejecting oversized encoded data before allocating decoded bytes.
        if (imageUrl.startsWith("data:", ignoreCase = true)) {
            val commaIndex = imageUrl.indexOf(',')
            if (commaIndex > 0) {
                val meta = imageUrl.substring(5, commaIndex)
                if (meta.contains(";base64", ignoreCase = true)) {
                    val encoded = imageUrl.substring(commaIndex + 1).replace("\r", "").replace("\n", "")
                    val encodedLength = encoded.length
                    val maxEncodedLength = ((byteLimit.toLong() + 2) / 3) * 4
                    if (encodedLength > maxEncodedLength) {
                        throw IllustrationDownloadException("Inline illustration exceeds limit of $byteLimit bytes")
                    }
                    val mimeType = meta.substringBefore(';').trim().lowercase()
                    val bytes = Base64.getDecoder().decode(encoded)
                    if (bytes.size <= byteLimit) {
                        val ext =
                            getExtensionForMime(mimeType, bytes)
                                ?: throw IllustrationDownloadException("Unsupported MIME type in inline illustration")
                        val safeName = String.format("%04d.%s", index, ext)
                        return LnEpubAsset(safeName, LnEpubStore.getMimeTypeForAsset("image.$ext"), bytes)
                    }
                    throw IllustrationDownloadException("Inline illustration exceeds limit of $byteLimit bytes")
                }
            }
            throw IllustrationDownloadException("Malformed inline data URI illustration")
        }

        // Handle remote HTTP / HTTPS URL
        val httpUrl =
            imageUrl.toHttpUrlOrNull()
                ?: throw IllustrationDownloadException("Invalid HTTP URL for illustration")
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            throw IllustrationDownloadException("Unsupported illustration URL scheme '${httpUrl.scheme}'")
        }

        val sourceUrl = baseUrl?.toHttpUrlOrNull()
        val sourceOrigin =
            sourceUrl
                ?.newBuilder()
                ?.username("")
                ?.password("")
                ?.encodedPath("/")
                ?.query(null)
                ?.fragment(null)
                ?.build()
                ?.toString()
        var currentUrl = httpUrl
        var currentHeaders =
            if (sourceUrl != null && sameOrigin(sourceUrl, httpUrl)) {
                headers
            } else {
                safeCrossOriginHeaders(headers)
            }

        repeat(MAX_IMAGE_REDIRECTS + 1) { redirectCount ->
            val addresses =
                try {
                    InetAddress.getAllByName(currentUrl.host).toList()
                } catch (_: Exception) {
                    throw IllustrationDownloadException("DNS resolution failed for illustration")
                }
            if (addresses.isEmpty() || !addresses.all { LnNetworkGateway.isPermittedAddress(it) }) {
                throw IllustrationDownloadException("Illustration destination is not permitted")
            }

            val requestBuilder = Request.Builder().url(currentUrl)
            currentHeaders.forEach { (name, value) ->
                if (!name.equals("host", ignoreCase = true) &&
                    !name.equals("content-length", ignoreCase = true) &&
                    !name.equals("referer", ignoreCase = true)
                ) {
                    requestBuilder.header(name, value)
                }
            }
            if (sourceOrigin != null) requestBuilder.header("Referer", sourceOrigin)

            val pinnedClient =
                imageClient
                    .newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .dns(
                        Dns { hostname ->
                            if (!hostname.equals(currentUrl.host, ignoreCase = true)) {
                                throw IllustrationDownloadException("Unexpected DNS lookup for illustration")
                            }
                            addresses
                        },
                    ).build()

            pinnedClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isRedirect) {
                    if (redirectCount == MAX_IMAGE_REDIRECTS) throw IllustrationDownloadException("Too many illustration redirects")
                    val location =
                        response.header("Location") ?: throw IllustrationDownloadException("Illustration redirect has no location")
                    val target = currentUrl.resolve(location) ?: throw IllustrationDownloadException("Invalid illustration redirect target")
                    if (target.scheme != "http" && target.scheme != "https") {
                        throw IllustrationDownloadException("Unsupported illustration redirect scheme '${target.scheme}'")
                    }
                    if (!sameOrigin(currentUrl, target)) currentHeaders = safeCrossOriginHeaders(currentHeaders)
                    currentUrl = target
                } else {
                    if (!response.isSuccessful) {
                        throw IllustrationDownloadException("HTTP ${response.code} when downloading illustration")
                    }
                    val body = response.body
                    val contentLength = body.contentLength()
                    if (contentLength > byteLimit) {
                        throw IllustrationDownloadException(
                            "Illustration content length ($contentLength) exceeds limit of $byteLimit bytes",
                        )
                    }

                    val bytes = body.byteStream().use { it.readNBytes(byteLimit + 1) }
                    if (bytes.isEmpty() || bytes.size > byteLimit) {
                        throw IllustrationDownloadException("Illustration payload is empty or exceeds limit of $byteLimit bytes")
                    }

                    val contentType =
                        response
                            .header("Content-Type")
                            ?.substringBefore(';')
                            ?.trim()
                            ?.lowercase()
                    val ext =
                        getExtensionForMime(contentType, bytes)
                            ?: throw IllustrationDownloadException("Unsupported or invalid illustration MIME type")
                    val mimeType = LnEpubStore.getMimeTypeForAsset("image.$ext")
                    val safeName = String.format("%04d.%s", index, ext)
                    return LnEpubAsset(safeName, mimeType, bytes)
                }
            }
        }
        throw IllustrationDownloadException("Unable to download illustration")
    }

    private fun sameOrigin(
        first: HttpUrl,
        second: HttpUrl,
    ): Boolean = first.scheme == second.scheme && first.host == second.host && first.port == second.port

    private fun safeCrossOriginHeaders(headers: Headers): Headers =
        Headers
            .Builder()
            .also { safe ->
                headers.forEach { (name, value) ->
                    if (name.equals("Accept", true) || name.equals("Accept-Language", true) || name.equals("User-Agent", true)) {
                        safe.add(name, value)
                    }
                }
            }.build()

    private fun getExtensionForMime(
        mimeType: String?,
        bytes: ByteArray,
    ): String? {
        // Inspect magic bytes for verification
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "jpg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "webp"
        }
        if (bytes.size >= 4 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte()
        ) {
            return "gif"
        }
        if (bytes.size >= 16 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte() &&
            bytes[8] == 'a'.code.toByte() &&
            bytes[9] == 'v'.code.toByte() &&
            bytes[10] == 'i'.code.toByte() &&
            (bytes[11] == 'f'.code.toByte() || bytes[11] == 's'.code.toByte())
        ) {
            return "avif"
        }

        // Fallback to mimeType string
        return when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
    }
}

private class IllustrationDownloadException(
    message: String,
) : IOException(message)
