package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Coordinates concurrent access to the per-page cache files shared by the live "read a
 * not-yet-downloaded chapter" path ([ImageResponse.getImageResponse]) and the chapter download
 * job (`ChaptersFilesProvider.downloadImpl` / `Page.getPageImageDownload`).
 *
 * Both paths read/write the exact same cache directory and filename convention with no
 * coordination otherwise, which can result in one side observing the other's file mid-write, or
 * silently skipping post-processing (format conversion) for a page that was only ever fetched by
 * a live read.
 */
object PageCacheCoordinator {
    /** Name of the folder, inside a chapter's cache folder, holding one empty marker file per processed page */
    const val PROCESSED_MARKERS_DIR = ".processed"

    private class PageLock {
        val mutex = Mutex()
        var holders = 0
    }

    // Reference counted instead of an expiring cache: a lock is only dropped once nobody holds or
    // waits on it, so a slow fetch running under the lock can never lose it to an eviction.
    private val locks = HashMap<String, PageLock>()

    private fun key(
        saveDir: String,
        fileName: String,
    ) = "$saveDir/$fileName"

    /**
     * Runs [block] while holding the lock for this page slot. Not reentrant - never call this
     * from within a [block] that's already holding the same (or, transitively, any) page lock.
     */
    suspend fun <T> withPageLock(
        saveDir: String,
        fileName: String,
        block: suspend () -> T,
    ): T {
        val key = key(saveDir, fileName)
        val lock =
            synchronized(locks) {
                locks.getOrPut(key) { PageLock() }.apply { holders++ }
            }
        try {
            return lock.mutex.withLock { block() }
        } finally {
            synchronized(locks) {
                if (--lock.holders == 0) {
                    locks.remove(key)
                }
            }
        }
    }

    internal fun lockCount(): Int = synchronized(locks) { locks.size }

    private fun markerFile(
        saveDir: String,
        fileName: String,
    ) = File(File(saveDir, PROCESSED_MARKERS_DIR), fileName)

    /**
     * Whether download-time post-processing has already been attempted for this page.
     *
     * The marker lives on disk next to the cached page, so it survives a server restart in the
     * middle of a download and disappears together with the cached page when the cache is cleared.
     */
    fun isProcessed(
        saveDir: String,
        fileName: String,
    ): Boolean = markerFile(saveDir, fileName).exists()

    /** Marks this page as having gone through download-time post-processing (successfully or not - it won't be retried). */
    fun markProcessed(
        saveDir: String,
        fileName: String,
    ) {
        val marker = markerFile(saveDir, fileName)
        marker.parentFile.mkdirs()
        marker.createNewFile()
    }

    /** Drops the markers of a chapter's cache folder, which must not end up in the finished download. */
    fun clearProcessedMarkers(saveDir: String) {
        File(saveDir, PROCESSED_MARKERS_DIR).deleteRecursively()
    }
}
