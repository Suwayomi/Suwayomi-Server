package suwayomi.tachidesk.manga.impl.download.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jsoup.Jsoup
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.EpubArchiveProvider
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipFile

data class LnEpubChapterContent(
    val html: String,
    val customCss: String?,
    val customJs: String?,
)

object LnEpubStore {
    private val appDirs: ApplicationDirs
        get() = Injekt.get()

    val downloadsRoot: File
        get() = File(appDirs.downloadsRoot, ".ln")

    fun getMangaDir(mangaId: Int): File = File(downloadsRoot, mangaId.toString())

    fun getFile(
        mangaId: Int,
        chapterId: Int,
    ): File = File(getMangaDir(mangaId), "$chapterId.epub")

    fun resolveFile(
        mangaId: Int,
        chapterId: Int,
    ): File = getFile(mangaId, chapterId)

    fun resolveFile(
        mangaId: Long,
        chapterId: Long,
    ): File = resolveFile(mangaId.toInt(), chapterId.toInt())

    fun exists(
        mangaId: Int,
        chapterId: Int,
    ): Boolean = resolveFile(mangaId, chapterId).exists()

    fun existsValid(
        mangaId: Int,
        chapterId: Int,
    ): Boolean {
        val file = resolveFile(mangaId, chapterId)
        return file.exists() && file.isFile && file.length() > 0 && validateEpub(file)
    }

    fun createTempFile(
        mangaId: Int,
        chapterId: Int,
    ): File {
        val mangaDir = getMangaDir(mangaId)
        mangaDir.mkdirs()
        return File(mangaDir, ".tmp_${chapterId}_${UUID.randomUUID()}.epub")
    }

    fun validateEpub(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 0) return false
        return try {
            ZipFile(file).use { zip ->
                val mimetype =
                    zip.getEntry("mimetype")?.let {
                        zip.getInputStream(it).bufferedReader(Charsets.US_ASCII).use { r ->
                            r.readText().trim()
                        }
                    }
                if (mimetype != "application/epub+zip") return false
                val container = zip.getEntry("META-INF/container.xml") ?: zip.getEntry("META-INF\\container.xml") ?: return false
                if (container.size == 0L) return false
                zip.getEntry("OEBPS/chapter.xhtml") != null
            }
        } catch (_: Exception) {
            false
        }
    }

    fun readChapterHtml(
        mangaId: Int,
        chapterId: Int,
        resolveAssetsToDataUri: Boolean = true,
    ): String? = readChapterContent(mangaId, chapterId, resolveAssetsToDataUri)?.html

    fun readChapterContent(
        mangaId: Int,
        chapterId: Int,
        resolveAssetsToDataUri: Boolean = true,
    ): LnEpubChapterContent? {
        if (!existsValid(mangaId, chapterId)) return null
        val file = resolveFile(mangaId, chapterId)
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("OEBPS/chapter.xhtml") ?: return null
                val rawXhtml =
                    zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.readText()
                    }
                val doc = Jsoup.parse(rawXhtml)

                if (resolveAssetsToDataUri) {
                    var embeddedImageBytes = 0
                    doc.select("img[src]").forEach { img ->
                        val src = img.attr("src").trim()
                        if (!src.contains("://") && !src.startsWith("data:", ignoreCase = true) &&
                            !src.startsWith("blob:", ignoreCase = true)
                        ) {
                            val assetName = src.substringAfterLast('/')
                            val cleanedPath = src.removePrefix("./").removePrefix("/")
                            val assetEntry =
                                zip.getEntry("OEBPS/assets/$assetName")
                                    ?: zip.getEntry("assets/$assetName")
                                    ?: zip.getEntry("OEBPS/$cleanedPath")
                                    ?: zip.getEntry(cleanedPath)
                                    ?: zip.entries().asSequence().firstOrNull { e ->
                                        val entryName = e.name.replace('\\', '/')
                                        entryName.endsWith("/$assetName", ignoreCase = true) ||
                                            entryName.equals(assetName, ignoreCase = true)
                                    }

                            val remainingBytes = LnChapterDownloader.MAX_TOTAL_IMAGE_BYTES - embeddedImageBytes
                            val byteLimit = minOf(LnChapterDownloader.MAX_IMAGE_BYTES, remainingBytes)
                            if (assetEntry != null && byteLimit > 0 &&
                                (assetEntry.size == -1L || assetEntry.size in 1..byteLimit.toLong())
                            ) {
                                val bytes = zip.getInputStream(assetEntry).use { it.readNBytes(byteLimit + 1) }
                                if (bytes.isNotEmpty() && bytes.size <= byteLimit) {
                                    embeddedImageBytes += bytes.size
                                    val mimeType = getMimeTypeForAsset(assetName)
                                    val base64 = Base64.getEncoder().encodeToString(bytes)
                                    img.attr("src", "data:$mimeType;base64,$base64")
                                } else {
                                    img.removeAttr("src")
                                }
                            } else {
                                img.removeAttr("src")
                            }
                        }
                    }
                }

                doc.select("img").forEach { it.removeAttr("srcset") }
                doc.select("img[src]").forEach { img ->
                    val src = img.attr("src").trim()
                    if (src.startsWith("//") || (':' in src && !src.startsWith("data:", ignoreCase = true))) {
                        img.removeAttr("src")
                    }
                }

                LnEpubChapterContent(
                    html = doc.body().html(),
                    customCss =
                        zip.getEntry("OEBPS/custom.css")?.let { entry ->
                            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        },
                    customJs =
                        zip.getEntry("OEBPS/custom-js.txt")?.let { entry ->
                            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        },
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getMimeTypeForAsset(name: String): String =
        when (val ext = name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "webp", "gif", "avif" -> "image/${if (ext == "jpg") "jpeg" else ext}"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }

    fun delete(
        mangaId: Int,
        chapterId: Int,
    ): Boolean = runBlocking { EpubArchiveProvider(mangaId, chapterId).delete() }

    fun size(
        mangaId: Int,
        chapterId: Int,
    ): Long = resolveFile(mangaId, chapterId).takeIf { it.exists() }?.length() ?: 0L

    fun hash(file: File): String? = KoreaderHelper.hashContents(file)

    fun healChapterDownloadState(
        mangaId: Int,
        chapterId: Int,
    ): Boolean {
        val actualMangaId =
            transaction {
                val chapter = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.firstOrNull() ?: return@transaction null
                val id = chapter[ChapterTable.manga].value
                val manga = MangaTable.selectAll().where { MangaTable.id eq id }.firstOrNull()
                id.takeIf { manga?.get(MangaTable.contentType) == SourceContentType.LIGHT_NOVEL }
            } ?: return false

        val hasValidDownload = existsValid(actualMangaId, chapterId)
        val computedHash = if (hasValidDownload) hash(getFile(actualMangaId, chapterId)) else null

        transaction {
            val row = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.firstOrNull() ?: return@transaction
            val currentDownloaded = row[ChapterTable.isDownloaded]
            val currentHash = row[ChapterTable.koreaderHash]

            if (currentDownloaded != hasValidDownload || currentHash != computedHash) {
                ChapterTable.update({ ChapterTable.id eq chapterId }) {
                    it[isDownloaded] = hasValidDownload
                    it[koreaderHash] = computedHash
                }
            }
        }
        return hasValidDownload
    }
}
