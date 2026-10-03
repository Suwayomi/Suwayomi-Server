package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/**
 * Maps page indices between a chapter's source pages and its downloaded pages, which differ once
 * [TallImageSplitter] replaced a tall page `<page>.<ext>` with its parts `<page>.001.<ext>`,
 * `<page>.002.<ext>`, ...
 *
 * A page index stored or exchanged with the outside (a reading position, a backup) means a source
 * page while the chapter isn't downloaded and a downloaded page once it is, so it has to be mapped
 * whenever the chapter switches between the two. The mapping is read back from the downloaded
 * file names, so it needs no bookkeeping of its own.
 *
 * @param fileNames the downloaded chapter's page file names, in reading order
 */
class SplitPageLayout(
    fileNames: List<String>,
) {
    /** The source page index of each downloaded page */
    private val sourcePageOfFile: IntArray

    /** The index of the first downloaded page of each source page */
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

    /** The first downloaded page showing [sourceIndex], clamped to the chapter's pages */
    fun toDownloadedIndex(sourceIndex: Int): Int {
        if (firstFileOfSourcePage.isEmpty()) return sourceIndex
        return firstFileOfSourcePage[sourceIndex.coerceIn(0, firstFileOfSourcePage.lastIndex)]
    }

    /** The source page that [downloadedIndex] is (a part of), clamped to the chapter's pages */
    fun toSourceIndex(downloadedIndex: Int): Int {
        if (sourcePageOfFile.isEmpty()) return downloadedIndex
        return sourcePageOfFile[downloadedIndex.coerceIn(0, sourcePageOfFile.lastIndex)]
    }

    companion object {
        private val SPLIT_PART_NAME = Regex("""^(\d+)\.\d{3}\.[^.]+$""")

        /** The source page name of a split part (`"001"` for `"001.002.jpg"`), null for any other file */
        fun splitPageName(fileName: String): String? = SPLIT_PART_NAME.matchEntire(fileName)?.groupValues?.get(1)

        /** Whether [fileName] is one of the parts [TallImageSplitter] split page [pageName] into */
        fun isSplitPartOf(
            fileName: String,
            pageName: String,
        ): Boolean = splitPageName(fileName) == pageName
    }
}
