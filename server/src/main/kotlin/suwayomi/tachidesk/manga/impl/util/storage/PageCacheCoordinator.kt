package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes

/**
 * Coordinates concurrent access to the per-page cache files shared by the live "read a
 * not-yet-downloaded chapter" path ([ImageResponse.getImageResponse]) and the chapter download
 * job (`ChaptersFilesProvider.downloadImpl` / `Page.getPageImageDownload`).
 *
 * Both paths read/write the exact same cache directory and filename convention with no
 * coordination otherwise, which can result in one side observing the other's file mid-write, or
 * silently skipping post-processing (format conversion, image splitting) for a page that was
 * only ever fetched by a live read.
 */
object PageCacheCoordinator {
    private val mutexes: Cache<String, Mutex> =
        Cache
            .Builder<String, Mutex>()
            .expireAfterAccess(10.minutes)
            .build()

    private val processedMarkers: Cache<String, Boolean> =
        Cache
            .Builder<String, Boolean>()
            .expireAfterAccess(10.minutes)
            .build()

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
    ): T = mutexes.get(key(saveDir, fileName)) { Mutex() }.withLock { block() }

    /** Whether download-time post-processing has already been attempted for this page. */
    fun isProcessed(
        saveDir: String,
        fileName: String,
    ): Boolean = processedMarkers.get(key(saveDir, fileName)) == true

    /** Marks this page as having gone through download-time post-processing (successfully or not - it won't be retried). */
    fun markProcessed(
        saveDir: String,
        fileName: String,
    ) {
        processedMarkers.put(key(saveDir, fileName), true)
    }
}
