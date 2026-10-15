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
     * Windows reserved device names. A file whose base name (the part before
     * the first dot) matches one of these — case-insensitively — cannot be
     * created on Windows (e.g. "con", "con.txt", "COM1").
     */
    private val WINDOWS_RESERVED_NAMES: Set<String> =
        buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            for (i in 0..9) {
                add("COM$i")
                add("LPT$i")
            }
        }

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
        // iterate code points so surrogate pairs (e.g. emoji) stay intact while isolated
        // surrogates get replaced
        var index = 0
        while (index < name.length) {
            val codePoint = name.codePointAt(index)
            if (isValidFatFilenameCodePoint(codePoint)) {
                sb.appendCodePoint(codePoint)
            } else {
                sb.append('_')
            }
            index += Character.charCount(codePoint)
        }

        // truncation can leave a trailing "." or " " behind — both are illegal filename
        // endings on Windows
        val truncated = truncateFilename(sb.toString()).trim('.', ' ')
        if (truncated.isEmpty()) {
            return "(invalid)"
        }
        return disambiguateReservedName(truncated)
    }

    /**
     * Prefix Windows reserved names with "_" so the result is creatable on
     * every supported filesystem.
     */
    private fun disambiguateReservedName(filename: String): String {
        val baseName = filename.substringBefore('.')
        return if (baseName.uppercase() in WINDOWS_RESERVED_NAMES) {
            "_$filename"
        } else {
            filename
        }
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
     * Returns true if the given code point is a valid filename character, false otherwise.
     */
    private fun isValidFatFilenameCodePoint(codePoint: Int): Boolean {
        if (codePoint in 0x00..0x1f) {
            return false
        }
        // isolated UTF-16 surrogates are not real characters — they would otherwise be
        // written as invalid UTF-8
        if (codePoint in 0xD800..0xDFFF) {
            return false
        }
        // line/paragraph separators and bidi control characters (e.g. U+202E RLO) can
        // visually reverse filenames; isolate formatting (U+2066–U+2069) included
        if (codePoint in 0x2028..0x202E || codePoint in 0x2066..0x2069) {
            return false
        }
        return when (codePoint) {
            '"'.code, '*'.code, '/'.code, ':'.code, '<'.code, '>'.code, '?'.code, '\\'.code, '|'.code, 0x7f -> false
            else -> true
        }
    }
}
