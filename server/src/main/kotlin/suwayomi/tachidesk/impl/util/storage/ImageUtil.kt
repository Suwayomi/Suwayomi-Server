package suwayomi.tachidesk.impl.util.storage

import suwayomi.tachidesk.impl.util.storage.ImageUtil.ImageType.GIF
import suwayomi.tachidesk.impl.util.storage.ImageUtil.ImageType.JPG
import suwayomi.tachidesk.impl.util.storage.ImageUtil.ImageType.PNG
import suwayomi.tachidesk.impl.util.storage.ImageUtil.ImageType.WEBP
import java.io.InputStream

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// adopted from: https://github.com/tachiyomiorg/tachiyomi/blob/ff369010074b058bb734ce24c66508300e6e9ac6/app/src/main/java/eu/kanade/tachiyomi/util/system/ImageUtil.kt
object ImageUtil {

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        try {
            val bytes = ByteArray(8)

            val length = if (stream.markSupported()) {
                stream.mark(bytes.size)
                stream.read(bytes, 0, bytes.size).also { stream.reset() }
            } else {
                stream.read(bytes, 0, bytes.size)
            }

            if (length == -1) {
                return null
            }

            if (bytes.compareWith(charByteArrayOf(0xFF, 0xD8, 0xFF))) {
                return JPG
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
        } catch (e: Exception) {
        }
        return null
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

    enum class ImageType(val mime: String) {
        JPG("image/jpeg"),
        PNG("image/png"),
        GIF("image/gif"),
        WEBP("image/webp")
    }
}
