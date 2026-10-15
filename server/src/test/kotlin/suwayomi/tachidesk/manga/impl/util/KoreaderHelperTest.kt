package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies [KoreaderHelper] against KOReader's `util.partialMD5`
 * (frontend/util.lua: `for i = -1, 10` sampling `lshift(1024, 2*i)` bytes).
 *
 * LuaJIT's `bit.lshift` masks the shift count to its lower 5 bits, so
 * `lshift(1024, -2)` wraps to `lshift(1024, 30) == 0` — the first sample is at
 * offset 0, followed by 1024, 4096, …, 2^30 (the loop bound is inclusive).
 *
 * The fixtures use a period-251 pattern so it cannot alias a 256-based offset,
 * and the expected digests were produced independently of the implementation
 * under test (a straightforward absolute-seek reference port of the Lua
 * routine matching LuaJIT semantics) on the same fixtures.
 */
class KoreaderHelperTest {
    private fun content(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    private fun tempCbz(
        name: String,
        bytes: ByteArray,
    ): File =
        File
            .createTempFile(name, ".cbz")
            .apply {
                writeBytes(bytes)
                deleteOnExit()
            }

    @Test
    fun hashContentsMatchesKoreaderPartialMd5() {
        // 3000 bytes: samples at offsets 0 and 1024 only (4096 >= length).
        assertEquals(
            "1544ef7a46131a30cf328f55979db73d",
            KoreaderHelper.hashContents(tempCbz("koreader-a", content(3000))),
        )
    }

    @Test
    fun hashContentsMatchesKoreaderPartialMd5LargerFile() {
        // 20000 bytes: samples at offsets 0, 1024, 4096 and 16384. The period-251
        // fixture makes the windows distinct — a period-256 pattern would alias
        // offset 256 to offset 0 and could not discriminate the first offset.
        assertEquals(
            "04a4d10f5e5583d7173cce0782769cb1",
            KoreaderHelper.hashContents(tempCbz("koreader-b", content(20000))),
        )
    }

    @Test
    fun hashContentsSmallFileSamplesFromOffsetZero() {
        // The first sample is at offset 0, not 256: a 100-byte file hashes its
        // (whole) first window — KOReader never produces md5("") for a non-empty
        // file.
        assertEquals(
            "7acedd1a84a4cfcb6e7a16003242945e",
            KoreaderHelper.hashContents(tempCbz("koreader-c", ByteArray(100) { it.toByte() })),
        )
    }

    @Test
    fun hashContentsEmptyFileMatchesEmptyDigest() {
        // A 0-byte file reads nothing at offset 0 and hashes to md5("").
        assertEquals(
            "d41d8cd98f00b204e9800998ecf8427e",
            KoreaderHelper.hashContents(tempCbz("koreader-e", ByteArray(0))),
        )
    }

    @Test
    fun hashContentsRejectsNonArchiveExtension() {
        val file =
            File
                .createTempFile("koreader-d", ".txt")
                .apply {
                    writeBytes(content(3000))
                    deleteOnExit()
                }
        assertNull(KoreaderHelper.hashContents(file))
    }

    @Test
    fun hashStreamMatchesHashContents() {
        val bytes = content(3000)
        assertEquals(
            KoreaderHelper.hashContents(tempCbz("koreader-f", bytes)),
            KoreaderHelper.hashStream(ByteArrayInputStream(bytes), bytes.size.toLong()),
        )
    }

    @Test
    fun hashStreamMatchesKoreaderPartialMd5LargerFile() {
        // Exercises the stream path across several sample windows including skips.
        val bytes = content(20000)
        assertEquals(
            "04a4d10f5e5583d7173cce0782769cb1",
            KoreaderHelper.hashStream(ByteArrayInputStream(bytes), bytes.size.toLong()),
        )
    }

    @Test
    fun hashStreamEmptyInput() {
        assertEquals(
            "d41d8cd98f00b204e9800998ecf8427e",
            KoreaderHelper.hashStream(ByteArrayInputStream(ByteArray(0)), 0),
        )
    }
}
