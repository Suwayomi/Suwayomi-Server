package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File

object LocalSourceTools {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()

    data class LocalEntry(
        val name: String,
        val type: String, // "folder" | "archive" | "other"
        val sizeBytes: Long,
        val itemCount: Int,
        val lastModified: Long,
    )

    /**
     * List the top-level entries currently in the configured local source
     * directory. Lets the user see what's on disk without booting up the
     * Tachiyomi LocalSource browse listing.
     */
    fun listEntries(): List<LocalEntry> {
        val root = File(applicationDirs.localMangaRoot)
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root
            .listFiles()
            .orEmpty()
            .map { f ->
                val type =
                    when {
                        f.isDirectory -> "folder"
                        f.extension.equals("cbz", ignoreCase = true) -> "archive"
                        f.extension.equals("zip", ignoreCase = true) -> "archive"
                        f.extension.equals("rar", ignoreCase = true) -> "archive"
                        f.extension.equals("cbr", ignoreCase = true) -> "archive"
                        f.extension.equals("epub", ignoreCase = true) -> "archive"
                        else -> "other"
                    }
                val itemCount =
                    if (f.isDirectory) {
                        f.listFiles()?.size ?: 0
                    } else {
                        0
                    }
                val sizeBytes =
                    if (f.isDirectory) {
                        f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else {
                        f.length()
                    }
                LocalEntry(
                    name = f.name,
                    type = type,
                    sizeBytes = sizeBytes,
                    itemCount = itemCount,
                    lastModified = f.lastModified(),
                )
            }.sortedByDescending { it.lastModified }
    }

    /**
     * Force the LocalSource extension to walk the directory and ingest any
     * new mangas/chapters into the database. Useful after the user dropped
     * new files in via Finder/SSH.
     */
    suspend fun rescan(): Int {
        val source =
            (GetCatalogueSource.getCatalogueSourceOrNull(LocalSource.ID) as? LocalSource)
                ?: error("LocalSource is not registered")
        var imported = 0
        var page = 1
        // Walk pages until source returns no more entries.
        while (true) {
            val result: MangasPage =
                runCatching { source.getSearchManga(page, "", FilterList()) }
                    .onFailure { logger.warn(it) { "LocalSource rescan failed at page=$page" } }
                    .getOrNull() ?: break
            if (result.mangas.isEmpty()) break
            imported += result.insertOrUpdate(LocalSource.ID).size
            if (!result.hasNextPage) break
            page += 1
            if (page > 50) break // hard safety bound
        }
        return imported
    }
}
