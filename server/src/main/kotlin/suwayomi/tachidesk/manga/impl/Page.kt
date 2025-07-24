package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.StateFlow
import libcore.net.MimeUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.PageTable
import suwayomi.tachidesk.util.ConversionUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

object Page {
    /**
     * A page might have a imageUrl ready from the get go, or we might need to
     * go an extra step and call fetchImageUrl to get it.
     */
    suspend fun getTrueImageUrl(
        page: Page,
        source: HttpSource,
    ): String {
        if (page.imageUrl == null) {
            page.imageUrl = source.getImageUrl(page)
        }
        return page.imageUrl!!
    }

    suspend fun getPageImage(
        mangaId: Int,
        chapterIndex: Int,
        index: Int,
        format: String? = null,
        progressFlow: ((StateFlow<Int>) -> Unit)? = null,
    ): Pair<InputStream, String> {
        val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
        val chapterEntry =
            transaction {
                ChapterTable
                    .selectAll()
                    .where {
                        (ChapterTable.sourceOrder eq chapterIndex) and
                            (ChapterTable.manga eq mangaId)
                    }.first()
            }
        val chapterId = chapterEntry[ChapterTable.id].value

        try {
            if (chapterEntry[ChapterTable.isDownloaded]) {
                return convertImageResponse(ChapterDownloadHelper.getImage(mangaId, chapterId, index), format)
            }
        } catch (_: Exception) {
            // ignore and fetch again
        }

        val pageEntry =
            transaction {
                PageTable
                    .selectAll()
                    .where { (PageTable.chapter eq chapterId) }
                    .orderBy(PageTable.index to SortOrder.ASC)
                    .limit(1)
                    .offset(index.toLong())
                    .first()
            }
        val tachiyomiPage =
            Page(
                pageEntry[PageTable.index],
                pageEntry[PageTable.url] ?: "",
                if (pageEntry[PageTable.imageUrl].isNullOrBlank()) null else pageEntry[PageTable.imageUrl],
            )
        progressFlow?.invoke(tachiyomiPage.progress)

        // we treat Local source differently
        if (mangaEntry[MangaTable.sourceReference] == LocalSource.ID) {
            // is of archive format
            if (LocalSource.pageCache.containsKey(chapterEntry[ChapterTable.url])) {
                val pageStream = LocalSource.pageCache[chapterEntry[ChapterTable.url]]!![index]
                return convertImageResponse(pageStream() to (ImageUtil.findImageType { pageStream() }?.mime ?: "image/jpeg"), format)
            }

            // is of directory format
            val imageFile = File(tachiyomiPage.imageUrl!!)
            return convertImageResponse(
                imageFile.inputStream() to (ImageUtil.findImageType { imageFile.inputStream() }?.mime ?: "image/jpeg"),
                format,
            )
        }

        val source = getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])
        source as HttpSource

        if (pageEntry[PageTable.imageUrl].isNullOrBlank()) {
            try {
                val trueImageUrl = getTrueImageUrl(tachiyomiPage, source)
                if (trueImageUrl.length <= 2048) {
                    transaction {
                        PageTable.update({ (PageTable.chapter eq chapterId) and (PageTable.index eq index) }) {
                            it[imageUrl] = trueImageUrl
                        }
                    }
                }
                tachiyomiPage.imageUrl = trueImageUrl
            } catch (e: IllegalArgumentException) {
                // URL resolution failed - try to re-fetch page list to get original base64 data
                try {
                    val originalPageList = refetchPageListFromSource(mangaId, chapterId)
                    val originalPage = originalPageList.find { it.index == index }

                    if (originalPage?.imageUrl != null) {
                        if (originalPage.imageUrl!!.startsWith("data:")) {
                            // This is base64 image data, handle it directly
                            return convertImageResponse(
                                handleBase64ImageData(originalPage.imageUrl!!),
                                format,
                            )
                        } else if (originalPage.imageUrl!!.startsWith("http")) {
                            // This is a regular URL, extract the clean URL part (before any # metadata)
                            val cleanImageUrl = originalPage.imageUrl!!.split("#")[0]

                            // Create a new page with the clean URL and fetch the image directly
                            val cleanPage =
                                eu.kanade.tachiyomi.source.model
                                    .Page(originalPage.index, cleanImageUrl, cleanImageUrl)
                            val response = source.getImage(cleanPage)
                            val mimeType = response.headers["content-type"] ?: "image/webp"
                            return convertImageResponse(response.body.byteStream() to mimeType, format)
                        }
                    }
                } catch (fetchError: Exception) {
                    // Ignore fetch error and fall through to original error
                }
                throw Exception("Failed to resolve image URL for page $index: ${e.message}", e)
            }
        }

        val fileName = getPageName(index, chapterEntry[ChapterTable.pageCount])

        val cacheSaveDir = getChapterCachePath(mangaId, chapterId)

        // Note: don't care about invalidating cache because OS cache is not permanent
        return convertImageResponse(
            getImageResponse(cacheSaveDir, fileName) {
                source.getImage(tachiyomiPage)
            },
            format,
        )
    }

    private suspend fun convertImageResponse(
        image: Pair<InputStream, String>,
        format: String? = null,
    ): Pair<InputStream, String> {
        val imageExtension = MimeUtils.guessExtensionFromMimeType(image.second) ?: image.second.removePrefix("image/")

        val targetExtension =
            (if (format != imageExtension) format else null)
                ?: return image

        val outStream = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersBySuffix(targetExtension)
        val writer = writers.next()
        ImageIO.createImageOutputStream(outStream).use { o ->
            writer.setOutput(o)

            val inImage =
                ConversionUtil.readImage(image.first, image.second)
                    ?: throw NoSuchElementException("No conversion to $targetExtension possible")
            writer.write(inImage)
        }
        writer.dispose()
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        return Pair(inStream.buffered(), MimeUtils.guessMimeTypeFromExtension(targetExtension) ?: "image/$targetExtension")
    }

    /** converts 0 to "001" */
    fun getPageName(
        index: Int,
        pageCount: Int,
    ): String = String.format("%0${pageCount.toString().length.coerceAtLeast(3)}d", index + 1)

    /**
     * Re-fetch the page list from the source to get original data (including base64 imageUrl)
     */
    private suspend fun refetchPageListFromSource(
        mangaId: Int,
        chapterId: Int,
    ): List<eu.kanade.tachiyomi.source.model.Page> {
        val chapterEntry =
            transaction {
                ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()
            }
        val mangaEntry =
            transaction {
                MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
            }

        val source = getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference]) as HttpSource

        val sChapter =
            eu.kanade.tachiyomi.source.model.SChapter.create().apply {
                url = chapterEntry[ChapterTable.url]
                name = chapterEntry[ChapterTable.name]
                scanlator = chapterEntry[ChapterTable.scanlator]
                chapter_number = chapterEntry[ChapterTable.chapter_number]
                date_upload = chapterEntry[ChapterTable.date_upload]
            }

        return source.getPageList(sChapter)
    }

    /**
     * Handle base64 encoded image data directly
     */
    private fun handleBase64ImageData(base64Data: String): Pair<InputStream, String> {
        // Parse data URL format: data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD...
        val parts = base64Data.split(",")
        if (parts.size != 2 || !parts[0].startsWith("data:")) {
            throw IllegalArgumentException("Invalid base64 data URL format")
        }

        val mimeType = parts[0].substringAfter("data:").substringBefore(";")
        val base64Content = parts[1]

        val imageBytes =
            java.util.Base64
                .getDecoder()
                .decode(base64Content)
        return ByteArrayInputStream(imageBytes) to mimeType
    }
}
