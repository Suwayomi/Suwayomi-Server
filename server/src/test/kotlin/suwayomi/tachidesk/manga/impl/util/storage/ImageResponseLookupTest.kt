package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageResponseLookupTest {
    @TempDir
    lateinit var tempDir: File

    private val dir: String
        get() = tempDir.absolutePath

    private fun file(name: String) = File(tempDir, name).apply { writeText("image") }

    @Test
    fun findsAnImageWithAKnownExtension() {
        file("1.webp")

        assertEquals("$dir/1.webp", ImageResponse.findFileNameStartingWith(dir, "1"))
    }

    @Test
    fun findsAnImageWithAnUnusualExtension() {
        file("1.bmp")

        assertEquals("$dir/1.bmp", ImageResponse.findFileNameStartingWith(dir, "1"))
    }

    @Test
    fun prefersASavedImageOverALeftoverTmpFile() {
        file("1.tmp")
        file("1.png")

        assertEquals("$dir/1.png", ImageResponse.findFileNameStartingWith(dir, "1"))
    }

    @Test
    fun findsALeftoverTmpFileSoItIsDownloadedAgain() {
        file("1.tmp")

        assertEquals("$dir/1.tmp", ImageResponse.findFileNameStartingWith(dir, "1"))
    }

    @Test
    fun doesNotMatchAnotherImageSharingThePrefix() {
        file("10.jpg")
        file("1-cover.jpg")

        assertNull(ImageResponse.findFileNameStartingWith(dir, "1"))
    }

    @Test
    fun ignoresADirectoryNamedLikeTheImage() {
        File(tempDir, "1.jpg").mkdir()
        file("1.png")

        assertEquals("$dir/1.png", ImageResponse.findFileNameStartingWith(dir, "1"))
    }

    @Test
    fun returnsNullWhenTheDirectoryDoesNotExist() {
        assertNull(ImageResponse.findFileNameStartingWith("$dir/missing", "1"))
    }
}
