package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.AVIF
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.GIF
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.HEIF
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.JPEG
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.JXL
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.PNG
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.WEBP
import java.io.InputStream
import java.net.URLConnection

// adopted from: eu.kanade.tachiyomi.util.system.ImageUtil
object ImageUtil {
    fun isImage(
        name: String,
        openStream: (() -> InputStream)? = null,
    ): Boolean {
        val contentType =
            try {
                URLConnection.guessContentTypeFromName(name)
            } catch (e: Exception) {
                null
            } ?: openStream?.let { findImageType(it)?.mime }
        return contentType?.startsWith("image/") ?: false
    }

    fun findImageType(openStream: () -> InputStream): ImageType? = openStream().use { findImageType(it) }

    fun findImageType(stream: InputStream): ImageType? {
        try {
            val bytes = ByteArray(12)

            if (stream.markSupported()) {
                stream.mark(bytes.size)
            }
            // a single read() is not guaranteed to fill the buffer — loop until it is
            // full or EOF so magic checks never compare against zero padding
            var length = 0
            while (length < bytes.size) {
                val read = stream.read(bytes, length, bytes.size - length)
                if (read <= 0) break
                length += read
            }
            if (stream.markSupported()) {
                stream.reset()
            }

            if (length == 0) {
                return null
            }

            if (bytes.compareWith(charByteArrayOf(0xFF, 0xD8, 0xFF), length)) {
                return JPEG
            }
            if (bytes.compareWith(charByteArrayOf(0x89, 0x50, 0x4E, 0x47), length)) {
                return PNG
            }
            if (bytes.compareWith("GIF8".toByteArray(), length)) {
                return GIF
            }
            // RIFF is a container format — a bare "RIFF" prefix also matches WAV/AVI
            // files; the WEBP form-type at bytes 8..11 is required
            if (length >= 12 &&
                bytes.compareWith("RIFF".toByteArray(), length) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
            ) {
                return WEBP
            }
            if (length >= 12 && bytes.copyOfRange(4, 12).contentEquals("ftypavif".toByteArray())) {
                return AVIF
            }
            if (length >= 12 && isHEIF(bytes)) {
                return HEIF
            }
            if (bytes.compareWith(charByteArrayOf(0xFF, 0x0A), length)) {
                return JXL
            }
            if (bytes.compareWith(charByteArrayOf(0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20, 0x0D, 0x0A, 0x87, 0x0A), length)) {
                return JXL
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun isHEIF(bytes: ByteArray): Boolean {
        // ftypheic
        if (bytes[4] == 0x66.toByte() &&
            bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() &&
            bytes[7] == 0x70.toByte() &&
            bytes[8] == 0x68.toByte() &&
            bytes[9] == 0x65.toByte() &&
            bytes[10] == 0x69.toByte() &&
            bytes[11] == 0x63.toByte()
        ) {
            return true
        }

        // ftypmif1
        if (bytes[4] == 0x66.toByte() &&
            bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() &&
            bytes[7] == 0x70.toByte() &&
            bytes[8] == 0x6D.toByte() &&
            bytes[9] == 0x69.toByte() &&
            bytes[10] == 0x66.toByte() &&
            bytes[11] == 0x31.toByte()
        ) {
            return true
        }

        // ftypmsf1
        if (bytes[4] == 0x66.toByte() &&
            bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() &&
            bytes[7] == 0x70.toByte() &&
            bytes[8] == 0x6D.toByte() &&
            bytes[9] == 0x73.toByte() &&
            bytes[10] == 0x66.toByte() &&
            bytes[11] == 0x31.toByte()
        ) {
            return true
        }

        // ftypheis
        if (bytes[4] == 0x66.toByte() &&
            bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() &&
            bytes[7] == 0x70.toByte() &&
            bytes[8] == 0x68.toByte() &&
            bytes[9] == 0x65.toByte() &&
            bytes[10] == 0x69.toByte() &&
            bytes[11] == 0x73.toByte()
        ) {
            return true
        }

        // ftyphevc
        if (bytes[4] == 0x66.toByte() &&
            bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() &&
            bytes[7] == 0x70.toByte() &&
            bytes[8] == 0x68.toByte() &&
            bytes[9] == 0x65.toByte() &&
            bytes[10] == 0x76.toByte() &&
            bytes[11] == 0x63.toByte()
        ) {
            return true
        }
        return false
    }

    /**
     * [this] starts with [magic] — but only when at least [availableLength] bytes were
     * actually read; a short read must not let zero padding satisfy a magic check.
     */
    private fun ByteArray.compareWith(
        magic: ByteArray,
        availableLength: Int,
    ): Boolean = availableLength >= magic.size && magic.indices.none { this[it] != magic[it] }

    private fun charByteArrayOf(vararg bytes: Int): ByteArray =
        ByteArray(bytes.size).apply {
            for (i in bytes.indices) {
                set(i, bytes[i].toByte())
            }
        }

    enum class ImageType(
        val mime: String,
        val extension: String,
    ) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }
}
