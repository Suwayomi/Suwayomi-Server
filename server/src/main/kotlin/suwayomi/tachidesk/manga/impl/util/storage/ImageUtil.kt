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

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        try {
            val bytes = ByteArray(12)

            val length =
                if (stream.markSupported()) {
                    stream.mark(bytes.size)
                    stream.read(bytes, 0, bytes.size).also { stream.reset() }
                } else {
                    stream.read(bytes, 0, bytes.size)
                }

            if (length == -1) {
                return null
            }

            if (bytes.compareWith(charByteArrayOf(0xFF, 0xD8, 0xFF))) {
                return JPEG
            }
            if (bytes.compareWith(charByteArrayOf(0x89, 0x50, 0x4E, 0x47))) {
                return PNG
            }
            if (bytes.compareWith("GIF8".toByteArray())) {
                return GIF
            }
            if (bytes.compareWith("RIFF".toByteArray())) {
                return WEBP
            }
            if (bytes.copyOfRange(4, 12).compareWith("ftypavif".toByteArray())) {
                return AVIF
            }
            if (isHEIF(bytes)) {
                return HEIF
            }
            if (bytes.compareWith(charByteArrayOf(0xFF, 0x0A))) {
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

    private fun ByteArray.compareWith(magic: ByteArray): Boolean {
        return magic.indices.none { this[it] != magic[it] }
    }

    private fun charByteArrayOf(vararg bytes: Int): ByteArray {
        return ByteArray(bytes.size).apply {
            for (i in bytes.indices) {
                set(i, bytes[i].toByte())
            }
        }
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
    }
}
