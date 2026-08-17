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
import javax.imageio.ImageIO

/**
 * Splits overly tall "long strip" page images into several smaller images, similar to Mihon's
 * "split tall images" reader/downloader feature (see eu.kanade.tachiyomi's ImageUtil.splitTallImage).
 */
object TallImageSplitter {
    private val logger = KotlinLogging.logger {}

    /** Target height (px) for each split part. There's no device screen to size against on the server, so this is fixed. */
    private const val OPTIMAL_HEIGHT = 5000

    /** -1 so that a height exactly equal to a multiple of [OPTIMAL_HEIGHT] doesn't produce an extra empty part */
    internal fun calculatePartCount(imageHeight: Int): Int {
        require(imageHeight > 0) { "imageHeight must be positive" }
        return (imageHeight - 1) / OPTIMAL_HEIGHT + 1
    }

    internal fun shouldSplit(
        imageWidth: Int,
        imageHeight: Int,
    ): Boolean {
        require(imageWidth > 0) { "imageWidth must be positive" }
        return imageHeight > imageWidth * 3 && calculatePartCount(imageHeight) > 1
    }

    /**
     * Looks for a page file named `fileName.*` inside [directory] and, if it's a tall enough
     * still image, replaces it with several `fileName.NNN.jpg` files stacked in reading order.
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
                    if (!shouldSplit(width, height)) return

                    val partCount = calculatePartCount(height)
                    val partHeight = height / partCount
                    val image = reader.read(0)

                    val splitFiles = mutableListOf<File>()
                    try {
                        for (index in 0 until partCount) {
                            val topOffset = index * partHeight
                            var thisPartHeight = minOf(partHeight, height - topOffset)
                            if (index == partCount - 1) {
                                // last part absorbs the remainder so all parts sum up to the full height
                                thisPartHeight += height - (topOffset + thisPartHeight)
                            }

                            val splitFile = File(directory, "$fileName.${"%03d".format(index + 1)}.jpg")
                            writeAsJpeg(image, topOffset, thisPartHeight, width, splitFile)
                            splitFiles.add(splitFile)
                        }
                    } catch (e: Exception) {
                        splitFiles.forEach { it.delete() }
                        throw e
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

    private fun writeAsJpeg(
        source: BufferedImage,
        topOffset: Int,
        partHeight: Int,
        width: Int,
        outFile: File,
    ) {
        val subImage = source.getSubimage(0, topOffset, width, partHeight)
        // JPEG doesn't support an alpha channel, so flatten onto an opaque RGB image regardless of source format
        val rgbImage = BufferedImage(width, partHeight, BufferedImage.TYPE_INT_RGB)
        rgbImage.createGraphics().apply {
            drawImage(subImage, 0, 0, null)
            dispose()
        }
        if (!ImageIO.write(rgbImage, "jpg", outFile)) {
            throw IllegalStateException("No JPEG writer available")
        }
    }
}
