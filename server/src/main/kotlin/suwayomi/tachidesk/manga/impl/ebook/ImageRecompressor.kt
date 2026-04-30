package suwayomi.tachidesk.manga.impl.ebook

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Reduces an EPUB's overall size by re-encoding its raster pages as
 * JPEG at progressively lower quality until the EPUB fits under the
 * SMTP attachment limit.
 *
 * Used as a fallback path when the first build of an EPUB exceeds the
 * configured limit. Returns null when no quality level is enough.
 */
object ImageRecompressor {
    private val logger = KotlinLogging.logger {}

    private val QUALITY_STEPS = floatArrayOf(0.80f, 0.65f, 0.50f, 0.35f)

    /**
     * Try shrinking [pages] until the assembled EPUB is at most
     * [limitBytes] big. The shrink only re-encodes formats we can
     * actually round-trip through ImageIO (JPEG, PNG, WEBP, BMP).
     * Other formats are left as-is.
     */
    fun shrinkToFit(
        pages: List<EpubBuilder.Page>,
        bookTitle: String,
        author: String?,
        rtl: Boolean,
        limitBytes: Int,
    ): ByteArray? {
        for (quality in QUALITY_STEPS) {
            val recompressed =
                pages.map { page ->
                    val recompressed = recompressJpeg(page.bytes, quality)
                    if (recompressed != null) {
                        EpubBuilder.Page(
                            bytes = recompressed,
                            mime = "image/jpeg",
                            name = page.name.substringBeforeLast('.') + ".jpg",
                        )
                    } else {
                        page
                    }
                }
            val epub = EpubBuilder.build(bookTitle, author, recompressed, rtl)
            logger.debug {
                "Recompress attempt q=$quality result=${epub.size}B limit=${limitBytes}B"
            }
            if (epub.size <= limitBytes) return epub
        }
        return null
    }

    private fun recompressJpeg(
        original: ByteArray,
        quality: Float,
    ): ByteArray? {
        val img: BufferedImage = ByteArrayInputStream(original).use { ImageIO.read(it) } ?: return null
        // Strip alpha if present to avoid JPEG writer failing.
        val rgb =
            if (img.type == BufferedImage.TYPE_INT_RGB) {
                img
            } else {
                val converted = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
                val g = converted.createGraphics()
                g.drawImage(img, 0, 0, java.awt.Color.WHITE, null)
                g.dispose()
                converted
            }
        val baos = ByteArrayOutputStream(original.size / 2)
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull() ?: return null
        try {
            ImageIO.createImageOutputStream(baos).use { output ->
                writer.output = output
                val params = writer.defaultWriteParam
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = quality
                writer.write(null, IIOImage(rgb, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return baos.toByteArray()
    }
}
