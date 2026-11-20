package xyz.nulldev.androidcompat.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// adopted from: https://github.com/tachiyomiorg/tachiyomi/blob/4cefbce7c34e724b409b6ba127f3c6c5c346ad8d/app/src/main/java/eu/kanade/tachiyomi/util/storage/DiskUtil.kt
object SafePath {
    private const val MAX_FILENAME_CHARS = 240
    private const val MAX_FILENAME_UTF8_BYTES = 240

    /**
     * Mutate the given filename to make it valid for a FAT filesystem,
     * replacing any invalid characters with "_". This method doesn't allow hidden files (starting
     * with a dot), but you can manually add it later.
     */
    fun buildValidFilename(origName: String): String {
        val name = origName.trim('.', ' ')
        if (name.isEmpty()) {
            return "(invalid)"
        }
        val sb = StringBuilder(name.length)
        name.forEach { c ->
            if (isValidFatFilenameChar(c)) {
                sb.append(c)
            } else {
                sb.append('_')
            }
        }

        return truncateFilename(sb.toString())
    }

    private fun truncateFilename(filename: String): String {
        // Keep a safety margin under common filesystem limits and satisfy both
        // character count and UTF-8 byte-length constraints.
        val output = StringBuilder(minOf(filename.length, MAX_FILENAME_CHARS))
        var usedBytes = 0
        var index = 0

        while (index < filename.length && output.length < MAX_FILENAME_CHARS) {
            val codePoint = Character.codePointAt(filename, index)
            val codePointBytes = utf8ByteCount(codePoint)

            if (usedBytes + codePointBytes > MAX_FILENAME_UTF8_BYTES) {
                break
            }

            output.appendCodePoint(codePoint)
            usedBytes += codePointBytes
            index += Character.charCount(codePoint)
        }

        return output.toString()
    }

    private fun utf8ByteCount(codePoint: Int): Int =
        when {
            codePoint <= 0x7f -> 1
            codePoint <= 0x7ff -> 2
            codePoint <= 0xffff -> 3
            else -> 4
        }

    /**
     * Returns true if the given character is a valid filename character, false otherwise.
     */
    private fun isValidFatFilenameChar(c: Char): Boolean {
        if (0x00.toChar() <= c && c <= 0x1f.toChar()) {
            return false
        }
        return when (c) {
            '"', '*', '/', ':', '<', '>', '?', '\\', '|', 0x7f.toChar() -> false
            else -> true
        }
    }
}
