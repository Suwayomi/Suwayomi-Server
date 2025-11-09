package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.Page
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
import suwayomi.tachidesk.server.serverConfig
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
        chapterId: Int? = null,
        chapterIndex: Int? = null,
        index: Int,
        format: String? = null,
        progressFlow: ((StateFlow<Int>) -> Unit)? = null,
    ): Pair<InputStream, String> {
        val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
        val chapterEntry =
            transaction {
                if (chapterId != null) {
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.id eq chapterId }
                        .first()
                } else {
                    ChapterTable
                        .selectAll()
                        .where { ChapterTable.manga eq mangaId and (ChapterTable.sourceOrder eq chapterIndex!!) }
                        .first()
                }
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
                pageEntry[PageTable.url],
                pageEntry[PageTable.imageUrl],
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

        if (pageEntry[PageTable.imageUrl] == null) {
            val trueImageUrl = getTrueImageUrl(tachiyomiPage, source)
            transaction {
                PageTable.update({ (PageTable.chapter eq chapterId) and (PageTable.index eq index) }) {
                    it[imageUrl] = trueImageUrl
                }
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
        var currentImage = image.first
        var currentMimeType = image.second

        val conversions = serverConfig.downloadConversions.value
        val defaultConversion = conversions["default"]
        val conversion = conversions[currentMimeType] ?: defaultConversion

        // Apply HTTP post-process if configured (complementary with format conversion)
        if (conversion != null && ConversionUtil.isHttpPostProcess(conversion)) {
            try {
                val processedStream =
                    ConversionUtil
                        .imageHttpPostProcess(
                            inputStream = currentImage,
                            mimeType = currentMimeType,
                            targetUrl = conversion.target,
                        )?.buffered()
                if (processedStream != null) {
                    val mime =
                        ImageUtil.findImageType(processedStream)?.mime
                            ?: "image/jpeg"

                    // Update current image to post-processed version
                    currentImage = processedStream
                    currentMimeType = mime
                }
            } catch (_: Exception) {
                // HTTP post-processing failed, continue with original image
            }
        }

        // Apply format conversion if requested
        val imageExtension =
            MimeUtils.guessExtensionFromMimeType(currentMimeType)
                ?: currentMimeType.removePrefix("image/")
        val targetExtension =
            (if (format != imageExtension) format else null)
                ?: return currentImage to currentMimeType

        return convertToFormat(currentImage, currentMimeType, targetExtension)
    }

    private fun convertToFormat(
        inputStream: InputStream,
        sourceMimeType: String,
        targetExtension: String,
    ): Pair<InputStream, String> {
        val outStream = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersBySuffix(targetExtension)
        val writer = writers.next()
        ImageIO.createImageOutputStream(outStream).use { o ->
            writer.setOutput(o)

            val inImage =
                ConversionUtil.readImage(inputStream, sourceMimeType)
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
}
