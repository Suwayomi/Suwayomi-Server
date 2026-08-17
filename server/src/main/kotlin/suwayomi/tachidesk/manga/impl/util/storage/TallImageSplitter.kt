package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter

/**
 * Splits overly tall "long strip" page images into several smaller images, similar to Mihon's
 * "split tall images" reader/downloader feature (see eu.kanade.tachiyomi's ImageUtil.splitTallImage).
 */
object TallImageSplitter {
    private val logger = KotlinLogging.logger {}

    /** Matches Mihon's `Bitmap.compress(JPEG, 100, ...)`, used only for the JPEG fallback path below */
    private const val JPEG_QUALITY = 1.0f

    /**
     * There's no device screen to size against on the server (Mihon targets `2 * screenHeight`),
     * so instead the target height is scaled off the image's own width: the height a very tall
     * 21:9 screen of that width would have, doubled just like Mihon's "2 screens" heuristic.
     */
    internal fun computeOptimalHeight(imageWidth: Int): Int {
        require(imageWidth > 0) { "imageWidth must be positive" }
        val singleScreenHeight = imageWidth * 21 / 9
        return singleScreenHeight * 2
    }

    /** -1 so it doesn't try to split when imageHeight == optimalImageHeight */
    internal fun calculatePartCount(
        imageHeight: Int,
        optimalImageHeight: Int,
    ): Int {
        require(imageHeight > 0) { "imageHeight must be positive" }
        require(optimalImageHeight > 0) { "optimalImageHeight must be positive" }
        return (imageHeight - 1) / optimalImageHeight + 1
    }

    internal fun shouldSplit(
        imageWidth: Int,
        imageHeight: Int,
        optimalImageHeight: Int,
    ): Boolean {
        require(imageWidth > 0) { "imageWidth must be positive" }
        return imageHeight > imageWidth * 3 && calculatePartCount(imageHeight, optimalImageHeight) > 1
    }

    /**
     * Looks for a page file named `fileName.*` inside [directory] and, if it's a tall enough
     * still image, replaces it with several `fileName.NNN` files stacked in reading order.
     *
     * Each part is written back in the exact same format as the source image whenever a writer
     * for it is available on the classpath (PNG stays lossless, WEBP stays WEBP, etc.), using
     * that format's own default encoding parameters. Only when no matching writer exists does
     * this fall back to Mihon's own choice of JPEG at 100% quality - there's no reliable way to
     * recover an arbitrary source JPEG's original quality setting either way.
     *
     * No-ops (and logs a warning) if anything goes wrong, leaving the original file untouched -
     * a failed split must never cause a page to go missing.
     */
    fun splitIfNeeded(
        directory: File,
        fileName: String,
    ) {
        val originalPath = ImageResponse.findFileNameStartingWith(directory.path, fileName) ?: return
        val originalFile = File(originalPath)

        try {
            ImageIO.createImageInputStream(originalFile).use { imageInputStream ->
                val readers = ImageIO.getImageReaders(imageInputStream)
                if (!readers.hasNext()) return
                val reader = readers.next()
                try {
                    reader.setInput(imageInputStream)

                    // animated images (e.g. GIF) must not be split
                    if (reader.getNumImages(true) > 1) return

                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    val optimalImageHeight = computeOptimalHeight(width)
                    if (!shouldSplit(width, height, optimalImageHeight)) return

                    val partCount = calculatePartCount(height, optimalImageHeight)
                    val partHeight = height / partCount
                    val image = reader.read(0)
                    val splitWriter = prepareWriter(image, reader)

                    val splitFiles = mutableListOf<File>()
                    try {
                        for (index in 0 until partCount) {
                            val topOffset = index * partHeight
                            var thisPartHeight = minOf(partHeight, height - topOffset)
                            if (index == partCount - 1) {
                                // last part absorbs the remainder so all parts sum up to the full height
                                thisPartHeight += height - (topOffset + thisPartHeight)
                            }

                            val splitFile = File(directory, "$fileName.${"%03d".format(index + 1)}.${splitWriter.extension}")
                            val subImage = image.getSubimage(0, topOffset, width, thisPartHeight)
                            val outputImage = if (splitWriter.flattenAlpha) flattenToOpaqueRgb(subImage) else subImage
                            writePart(splitWriter, outputImage, splitFile)
                            splitFiles.add(splitFile)
                        }
                    } catch (e: Exception) {
                        splitFiles.forEach { it.delete() }
                        throw e
                    } finally {
                        splitWriter.writer.dispose()
                    }

                    originalFile.delete()
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to split tall image: $originalPath" }
        }
    }

    private class SplitWriter(
        val writer: ImageWriter,
        val param: ImageWriteParam,
        val extension: String,
        val flattenAlpha: Boolean,
    )

    private fun prepareWriter(
        image: BufferedImage,
        reader: ImageReader,
    ): SplitWriter {
        val typeSpecifier = ImageTypeSpecifier.createFromRenderedImage(image)
        val nativeWriters = ImageIO.getImageWriters(typeSpecifier, reader.formatName)
        if (nativeWriters.hasNext()) {
            val writer = nativeWriters.next()
            val extension =
                reader.originatingProvider
                    ?.fileSuffixes
                    ?.firstOrNull()
                    ?.lowercase()
                    ?: reader.formatName.lowercase()
            return SplitWriter(writer, writer.defaultWriteParam, extension, flattenAlpha = false)
        }

        val jpegWriter = ImageIO.getImageWritersByFormatName("jpg").next()
        val jpegParam =
            jpegWriter.defaultWriteParam.apply {
                if (canWriteCompressed()) {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY
                }
            }
        return SplitWriter(jpegWriter, jpegParam, "jpg", flattenAlpha = true)
    }

    private fun writePart(
        splitWriter: SplitWriter,
        image: BufferedImage,
        outFile: File,
    ) {
        ImageIO.createImageOutputStream(outFile).use { output ->
            splitWriter.writer.output = output
            splitWriter.writer.write(null, IIOImage(image, null, null), splitWriter.param)
        }
    }

    private fun flattenToOpaqueRgb(image: BufferedImage): BufferedImage {
        // JPEG doesn't support an alpha channel, so flatten onto an opaque RGB image regardless of source format
        val rgbImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        rgbImage.createGraphics().apply {
            drawImage(image, 0, 0, null)
            dispose()
        }
        return rgbImage
    }
}
