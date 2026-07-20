package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.util.SafePath
import java.io.File
import java.nio.file.Files

private val applicationDirs: ApplicationDirs by injectLazy()

private val logger = KotlinLogging.logger { }

private fun getMangaDir(
    title: String,
    sourceName: String,
): String {
    val sourceDir = SafePath.buildValidFilename(sourceName)
    val mangaDir = SafePath.buildValidFilename(title)

    return "$sourceDir/$mangaDir"
}

private suspend fun getMangaDir(mangaId: Int): String =
    suspendTransaction {
        val mangaEntry = MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
        val source = GetSource.getSourceOrStub(mangaEntry[MangaTable.sourceReference])

        getMangaDir(mangaEntry[MangaTable.title], source.toString())
    }

private suspend fun getChapterDir(
    mangaId: Int,
    chapterId: Int,
): String =
    suspendTransaction {
        // Get chapter data and build chapter-specific directory name
        val chapterEntry = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()

        val chapterDir =
            SafePath.buildValidFilename(
                when {
                    chapterEntry[ChapterTable.scanlator] != null -> {
                        "${chapterEntry[ChapterTable.scanlator]}_${chapterEntry[ChapterTable.name]}"
                    }

                    else -> {
                        chapterEntry[ChapterTable.name]
                    }
                },
            )

        // Get manga directory and combine with chapter directory
        // Note: This creates a nested transaction, but Exposed handles this with useNestedTransactions=true
        getMangaDir(mangaId) + "/$chapterDir"
    }

fun getThumbnailDownloadPath(mangaId: Int): String = applicationDirs.thumbnailDownloadsRoot + "/$mangaId"

fun getMangaDownloadDir(
    title: String,
    sourceName: String,
): String = applicationDirs.mangaDownloadsRoot + "/" + getMangaDir(title, sourceName)

suspend fun getMangaDownloadDir(mangaId: Int): String = applicationDirs.mangaDownloadsRoot + "/" + getMangaDir(mangaId)

fun getMangaCacheDir(
    title: String,
    sourceName: String,
): String = applicationDirs.tempMangaCacheRoot + "/" + getMangaDir(title, sourceName)

suspend fun getChapterDownloadPath(
    mangaId: Int,
    chapterId: Int,
): String = applicationDirs.mangaDownloadsRoot + "/" + getChapterDir(mangaId, chapterId)

suspend fun getChapterCbzPath(
    mangaId: Int,
    chapterId: Int,
): String = getChapterDownloadPath(mangaId, chapterId) + ".cbz"

suspend fun getChapterCachePath(
    mangaId: Int,
    chapterId: Int,
): String = applicationDirs.tempMangaCacheRoot + "/" + getChapterDir(mangaId, chapterId)

private fun updateDownloadDir(
    currentDir: String,
    newDir: String,
): Boolean {
    val currentDirFile = File(currentDir)
    val newDirFile = File(newDir)

    if (!currentDirFile.exists()) {
        return true
    }

    return try {
        Files.move(currentDirFile.toPath(), newDirFile.toPath())

        if (currentDirFile.exists()) {
            return false
        }

        if (!newDirFile.exists()) {
            return false
        }

        return true
    } catch (e: Exception) {
        logger.error(e) { "updateDownloadDir: failed to rename download folder from \"$currentDir\" to \"$newDir\"" }
        false
    }
}

/** return value says if rename/move was successful */
fun updateMangaDownloadDir(
    title: String,
    sourceName: String,
    newTitle: String,
): Boolean {
    val currentDownloadDir = getMangaDownloadDir(title, sourceName)
    val newDownloadDir = getMangaDownloadDir(newTitle, sourceName)

    val renamed = updateDownloadDir(currentDownloadDir, newDownloadDir)

    val tryToKeepCachedFilesUsable = renamed
    if (tryToKeepCachedFilesUsable) {
        val currentCacheDir = getMangaCacheDir(title, sourceName)
        val newCacheDir = getMangaCacheDir(newTitle, sourceName)

        updateDownloadDir(currentCacheDir, newCacheDir)
    }

    return renamed
}
