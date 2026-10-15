package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// Maps page indices between source pages and downloaded pages, where a split page is `<page>.001.<ext>`, ...
// Read back from the downloaded file names, in reading order, so it needs no bookkeeping
class SplitPageLayout(
    fileNames: List<String>,
) {
    private val sourcePageOfFile: IntArray

    private val firstFileOfSourcePage: IntArray

    init {
        val sourcePages = IntArray(fileNames.size)
        val firstFiles = mutableListOf<Int>()
        var previousSplitPage: String? = null
        fileNames.forEachIndexed { fileIndex, fileName ->
            val splitPage = splitPageName(fileName)
            if (splitPage == null || splitPage != previousSplitPage) {
                firstFiles.add(fileIndex)
            }
            sourcePages[fileIndex] = firstFiles.lastIndex
            previousSplitPage = splitPage
        }
        sourcePageOfFile = sourcePages
        firstFileOfSourcePage = firstFiles.toIntArray()
    }

    val downloadedPageCount: Int get() = sourcePageOfFile.size

    val sourcePageCount: Int get() = firstFileOfSourcePage.size

    val hasSplitPages: Boolean get() = downloadedPageCount != sourcePageCount

    fun toDownloadedIndex(sourceIndex: Int): Int {
        if (firstFileOfSourcePage.isEmpty()) return sourceIndex
        return firstFileOfSourcePage[sourceIndex.coerceIn(0, firstFileOfSourcePage.lastIndex)]
    }

    fun toSourceIndex(downloadedIndex: Int): Int {
        if (sourcePageOfFile.isEmpty()) return downloadedIndex
        return sourcePageOfFile[downloadedIndex.coerceIn(0, sourcePageOfFile.lastIndex)]
    }

    companion object {
        private val SPLIT_PART_NAME = Regex("""^(\d+)\.\d{3}\.[^.]+$""")

        // "001" for "001.002.jpg", null for any other file
        fun splitPageName(fileName: String): String? = SPLIT_PART_NAME.matchEntire(fileName)?.groupValues?.get(1)

        fun isSplitPartOf(
            fileName: String,
            pageName: String,
        ): Boolean = splitPageName(fileName) == pageName
    }
}
