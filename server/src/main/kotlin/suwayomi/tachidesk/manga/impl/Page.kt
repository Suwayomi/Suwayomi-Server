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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import libcore.net.MimeUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.DownloadConversion
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
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter

object Page {
    private val logger = KotlinLogging.logger {}

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
                return ChapterDownloadHelper.getImage(mangaId, chapterId, index)
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
                return pageStream() to (ImageUtil.findImageType { pageStream() }?.mime ?: "image/jpeg")
            }

            // is of directory format
            val imageFile = File(tachiyomiPage.imageUrl!!)
            return imageFile.inputStream() to (ImageUtil.findImageType { imageFile.inputStream() }?.mime ?: "image/jpeg")
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
        return getImageResponse(cacheSaveDir, fileName) {
            source.getImage(tachiyomiPage)
        }
    }

    suspend fun getPageImageServe(
        mangaId: Int,
        chapterIndex: Int,
        index: Int,
        format: String? = null,
    ): Pair<InputStream, String> {
        val (inputStream, mime) =
            getPageImage(
                mangaId = mangaId,
                chapterIndex = chapterIndex,
                index = index,
            )
        val conversions = serverConfig.serveConversions.value
        val defaultConversion = conversions["default"]
        val formatConversion = format?.let { DownloadConversion(target = it) }
        val conversion =
            formatConversion
                ?: conversions[mime]
                ?: defaultConversion
                ?: return inputStream to mime

        val converted =
            try {
                convertImageResponse(
                    image = inputStream,
                    mime = mime,
                    conversion = conversion,
                )
            } catch (e: Exception) {
                logger.error(e) { "Error while post-processing image" }
                // re-open cached image in case of an error, since conversion likely (partially) consumed the input stream
                // so it's likely not possible to serve it
                getPageImage(mangaId = mangaId, chapterIndex = chapterIndex, index = index)
            }
        return converted?.also { inputStream.close() } ?: (inputStream to mime)
    }

    suspend fun getPageImageDownload(
        mangaId: Int,
        chapterId: Int,
        index: Int,
        downloadCacheFolder: File,
        fileName: String,
        progressFlow: (StateFlow<Int>) -> Unit,
    ) {
        val (inputStream, mime) =
            getPageImage(
                mangaId = mangaId,
                chapterId = chapterId,
                index = index,
                progressFlow = progressFlow,
            )
        val conversions = serverConfig.downloadConversions.value
        if (conversions.isEmpty() || !downloadCacheFolder.exists()) {
            inputStream.close()
            return
        }
        val defaultConversion = conversions["default"]
        val conversion =
            conversions[mime]
                ?: defaultConversion
        if (conversion == null) {
            inputStream.close()
            return
        }

        try {
            val converted =
                try {
                    convertImageResponse(
                        image = inputStream,
                        mime = mime,
                        conversion = conversion,
                    )
                } catch (e: Exception) {
                    throw e
                } finally {
                    inputStream.close()
                }

            if (converted != null) {
                val (convertedStream, convertedMime) = converted
                val convertedExtension =
                    MimeUtils.guessExtensionFromMimeType(convertedMime)
                        ?: convertedMime.substringAfter('/')
                val convertedPage =
                    File(
                        downloadCacheFolder,
                        "$fileName.$convertedExtension",
                    )

                convertedPage.outputStream().use { outputStream ->
                    convertedStream.use { it.copyTo(outputStream) }
                }

                val extension =
                    MimeUtils.guessExtensionFromMimeType(mime)
                        ?: mime.substringAfter('/')
                if (extension != convertedExtension) {
                    File(
                        downloadCacheFolder,
                        "$fileName.$extension",
                    ).delete()
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error while post-processing image" }
        }
    }

    private suspend fun convertImageResponse(
        image: InputStream,
        mime: String,
        conversion: DownloadConversion,
    ): Pair<InputStream, String>? {
        // Apply HTTP post-process if configured (complementary with format conversion)
        if (ConversionUtil.isHttpPostProcess(conversion)) {
            try {
                val processedStream =
                    ConversionUtil
                        .imageHttpPostProcess(
                            inputStream = image,
                            mimeType = mime,
                            conversion = conversion,
                        )?.buffered()
                if (processedStream != null) {
                    val mime =
                        ImageUtil.findImageType(processedStream)?.mime
                            ?: "image/jpeg"

                    return processedStream to mime
                }
                throw Exception("HTTP-service did not return a usable stream")
            } catch (e: Exception) {
                // HTTP post-processing failed, continue with original image
                logger.warn(e) { "Error while post-processing image" }
                throw e
            }
        } else {
            if (mime == conversion.target) {
                return null
            }

            return convertToFormat(image, mime, conversion)
        }
    }

    private fun convertToFormat(
        inputStream: InputStream,
        sourceMimeType: String,
        target: DownloadConversion,
    ): Pair<InputStream, String>? {
        val outStream = ByteArrayOutputStream()
        val conversionWriter =
            getConversionWriter(
                target.target,
                target.compressionLevel,
            )
        if (conversionWriter == null) {
            logger.warn { "Conversion aborted: No reader for target format ${target.target}" }
            return null
        }

        val (writer, writerParams) = conversionWriter
        try {
            ImageIO.createImageOutputStream(outStream).use { o ->
                writer.setOutput(o)

                val inImage =
                    ConversionUtil.readImage(inputStream, sourceMimeType)
                        ?: throw NoSuchElementException("No conversion to ${target.target} possible")
                writer.write(null, IIOImage(inImage, null, null), writerParams)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Conversion aborted ($sourceMimeType -> ${target.target})" }
            throw e
        } finally {
            writer.dispose()
        }
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        return inStream.buffered() to target.target
    }

    private fun getConversionWriter(
        targetMime: String,
        compressionLevel: Double?,
    ): Pair<ImageWriter, ImageWriteParam>? {
        val writers = ImageIO.getImageWritersByMIMEType(targetMime)
        val writer =
            try {
                writers.next()
            } catch (_: NoSuchElementException) {
                return null
            }

        val writerParams = writer.defaultWriteParam
        compressionLevel?.let {
            writerParams.compressionMode = ImageWriteParam.MODE_EXPLICIT
            writerParams.compressionQuality = it.toFloat()
        }

        return writer to writerParams
    }

    /** converts 0 to "001" */
    fun getPageName(
        index: Int,
        pageCount: Int,
    ): String = String.format("%0${pageCount.toString().length.coerceAtLeast(3)}d", index + 1)
}
