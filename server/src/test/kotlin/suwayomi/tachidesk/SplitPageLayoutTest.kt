package suwayomi.tachidesk

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.manga.impl.util.storage.SplitPageLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitPageLayoutTest {
    // source page 1 was split in 3, source page 3 in 2
    private val layout =
        SplitPageLayout(
            listOf("001.jpg", "002.001.jpg", "002.002.jpg", "002.003.jpg", "003.png", "004.001.webp", "004.002.webp"),
        )

    @Test
    fun countsSourceAndDownloadedPages() {
        assertEquals(7, layout.downloadedPageCount)
        assertEquals(4, layout.sourcePageCount)
        assertTrue(layout.hasSplitPages)
    }

    @Test
    fun mapsASourcePageToItsFirstPart() {
        assertEquals(listOf(0, 1, 4, 5), (0..3).map(layout::toDownloadedIndex))
    }

    @Test
    fun mapsEveryPartToItsSourcePage() {
        assertEquals(listOf(0, 1, 1, 1, 2, 3, 3), (0..6).map(layout::toSourceIndex))
    }

    @Test
    fun clampsIndicesOutsideTheChapter() {
        assertEquals(5, layout.toDownloadedIndex(10))
        assertEquals(3, layout.toSourceIndex(10))
        assertEquals(0, layout.toDownloadedIndex(-1))
    }

    @Test
    fun keepsIndicesOfAChapterWithoutSplitPages() {
        val unsplit = SplitPageLayout(listOf("001.jpg", "002.jpg", "003.jpg"))

        assertFalse(unsplit.hasSplitPages)
        assertEquals(listOf(0, 1, 2), (0..2).map(unsplit::toDownloadedIndex))
        assertEquals(listOf(0, 1, 2), (0..2).map(unsplit::toSourceIndex))
    }

    @Test
    fun doesNotGroupFilesThatAreNotSplitParts() {
        // e.g. an imported download, whose names don't follow "<page>.<part>.<ext>"
        val imported = SplitPageLayout(listOf("page.1.jpg", "page.2.jpg", "001.01.jpg", "001.02.jpg"))

        assertFalse(imported.hasSplitPages)
    }

    @Test
    fun recognizesSplitPartsOfAPage() {
        assertEquals("002", SplitPageLayout.splitPageName("002.003.jpg"))
        assertNull(SplitPageLayout.splitPageName("002.jpg"))
        assertNull(SplitPageLayout.splitPageName("002.tmp"))
        assertTrue(SplitPageLayout.isSplitPartOf("002.003.jpg", "002"))
        assertFalse(SplitPageLayout.isSplitPartOf("002.003.jpg", "003"))
        assertFalse(SplitPageLayout.isSplitPartOf("0020.001.jpg", "002"))
    }
}
