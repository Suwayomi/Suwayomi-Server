package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter

// Splits tall long strip pages like Mihon's ImageUtil.splitTallImage
object TallImageSplitter {
    private val logger = KotlinLogging.logger {}

    // Like Mihon's Bitmap.compress(JPEG, 100), for every lossy format
    private const val FULL_QUALITY = 1.0f

    // No screen on the server (Mihon uses 2 * screenHeight): use 2 screens of a 21:9 screen as wide as the image
    internal fun computeOptimalHeight(imageWidth: Int): Int {
        require(imageWidth > 0) { "imageWidth must be positive" }
        val singleScreenHeight = imageWidth * 21 / 9
        return singleScreenHeight * 2
    }

    // -1 so an image exactly optimalImageHeight tall isn't split
    internal fun calculatePartCount(
        imageHeight: Int,
        optimalImageHeight: Int,
    ): Int {
        require(imageHeight > 0) { "imageHeight must be positive" }
        require(optimalImageHeight > 0) { "optimalImageHeight must be positive" }
        return (imageHeight - 1) / optimalImageHeight + 1
    }

    // Mihon's check, kept verbatim
    internal fun shouldSplit(
        imageWidth: Int,
        imageHeight: Int,
        optimalImageHeight: Int,
    ): Boolean {
        require(imageWidth > 0) { "imageWidth must be positive" }
        return imageHeight > imageWidth * 3 && calculatePartCount(imageHeight, optimalImageHeight) > 1
    }

    // Replaces a tall page with `<fileName>.NNN` parts in its own format, or JPEG without a writer for it.
    // A failed split leaves the page untouched
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
                    val splitWriter = prepareWriter(reader.getImageTypes(0).next(), reader, originalFile.extension)

                    val splitFiles = mutableListOf<File>()
                    // set once the reader turns out to decode the whole image whatever the region asked
                    var wholeImage: BufferedImage? = null
                    try {
                        for (index in 0 until partCount) {
                            val topOffset = index * partHeight
                            var thisPartHeight = minOf(partHeight, height - topOffset)
                            if (index == partCount - 1) {
                                // last part absorbs the remainder so all parts sum up to the full height
                                thisPartHeight += height - (topOffset + thisPartHeight)
                            }

                            val splitFile = File(directory, "$fileName.${"%03d".format(index + 1)}.${splitWriter.extension}")
                            val region = Rectangle(0, topOffset, width, thisPartHeight)
                            val part =
                                wholeImage?.let { copyRegion(it, region) }
                                    ?: run {
                                        // Decode only this part, like Mihon's BitmapRegionDecoder, so a whole
                                        // long strip never has to fit in memory at once
                                        val decoded = reader.read(0, reader.defaultReadParam.apply { sourceRegion = region })
                                        when {
                                            decoded.width == width && decoded.height == thisPartHeight -> {
                                                decoded
                                            }

                                            // usefulness' WEBP reader ignores the region: each part would be the whole page
                                            decoded.width == width && decoded.height == height -> {
                                                wholeImage = decoded
                                                copyRegion(decoded, region)
                                            }

                                            else -> {
                                                error("decoded ${decoded.width}x${decoded.height} for part $region of ${width}x$height")
                                            }
                                        }
                                    }
                            val outputImage = if (splitWriter.flattenAlpha) flattenToOpaqueRgb(part) else part
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
        typeSpecifier: ImageTypeSpecifier,
        reader: ImageReader,
        sourceExtension: String,
    ): SplitWriter {
        val nativeWriters = ImageIO.getImageWriters(typeSpecifier, reader.formatName)
        if (nativeWriters.hasNext()) {
            val writer = nativeWriters.next()
            // Keep the server's extension, the reader's first suffix depends on the plugin (TwelveMonkeys lists "wbp")
            return SplitWriter(writer, writer.fullQualityWriteParam(), sourceExtension.lowercase(), flattenAlpha = false)
        }

        val jpegWriter = ImageIO.getImageWritersByFormatName("jpg").next()
        return SplitWriter(jpegWriter, jpegWriter.fullQualityWriteParam(), "jpg", flattenAlpha = true)
    }

    // Lossy writers default to 0.75 quality, so use full quality like Mihon. Lossless ones keep their defaults
    private fun ImageWriter.fullQualityWriteParam(): ImageWriteParam {
        val param = defaultWriteParam
        if (!param.canWriteCompressed()) return param
        val isLossy =
            runCatching {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                // the WEBP writer offers "Lossy" and "Lossless" without preselecting either, encodes
                // lossy when none is set, and doesn't override isCompressionLossless (always true)
                if (param.compressionType == null && "Lossy" in param.compressionTypes.orEmpty()) {
                    param.compressionType = "Lossy"
                }
                param.compressionType == "Lossy" || !param.isCompressionLossless
            }.getOrDefault(false)
        if (!isLossy) return defaultWriteParam
        return param.apply { compressionQuality = FULL_QUALITY }
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

    // A sub image shares its parent's raster, which not every writer encodes from its own offset
    private fun copyRegion(
        image: BufferedImage,
        region: Rectangle,
    ): BufferedImage {
        val colorModel = image.colorModel
        val raster = colorModel.createCompatibleWritableRaster(region.width, region.height)
        image.getSubimage(region.x, region.y, region.width, region.height).copyData(raster)
        return BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied, null)
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
