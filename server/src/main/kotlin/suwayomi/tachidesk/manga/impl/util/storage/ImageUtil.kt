package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.GIF
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.JPG
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.PNG
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil.ImageType.WEBP
import java.io.InputStream
import java.net.URLConnection

// adopted from: eu.kanade.tachiyomi.util.system.ImageUtil
object ImageUtil {
    fun isImage(name: String, openStream: (() -> InputStream)? = null): Boolean {
        val contentType = try {
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
