package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists

// Coordinates the page cache shared by live reads and chapter downloads
object PageCacheCoordinator {
    const val PROCESSED_MARKERS_DIR = ".processed"

    private class PageLock {
        val mutex = Mutex()
        var holders = 0
    }

    // Reference counted instead of an expiring cache, so a slow fetch can't lose its lock to an eviction
    private val locks = HashMap<String, PageLock>()

    // Not reentrant: never call it from a block already holding a page lock
    suspend fun <T> withPageLock(
        saveDir: String,
        fileName: String,
        block: suspend () -> T,
    ): T {
        val key = "$saveDir/$fileName"
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
    ) = Path(saveDir) / PROCESSED_MARKERS_DIR / fileName

    // On disk, so it survives a restart mid download and goes away with the cache
    fun isProcessed(
        saveDir: String,
        fileName: String,
    ): Boolean = markerFile(saveDir, fileName).exists()

    fun markProcessed(
        saveDir: String,
        fileName: String,
    ) {
        val marker = markerFile(saveDir, fileName)
        if (!marker.exists()) {
            marker.createParentDirectories().createFile()
        }
    }

    // The markers must not end up in the finished download
    @OptIn(ExperimentalPathApi::class)
    fun clearProcessedMarkers(saveDir: String) {
        (Path(saveDir) / PROCESSED_MARKERS_DIR).deleteRecursively()
    }
}
