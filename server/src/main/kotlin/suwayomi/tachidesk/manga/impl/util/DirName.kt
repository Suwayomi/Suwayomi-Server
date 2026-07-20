package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.util.SafePath
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock

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

private fun getMangaDir(mangaId: Int): String =
    transaction {
        val mangaEntry = MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
        val source = GetSource.getSourceOrStub(mangaEntry[MangaTable.sourceReference])

        getMangaDir(mangaEntry[MangaTable.title], source.toString())
    }

private fun getChapterDir(
    mangaId: Int,
    title: String,
    scanlator: String?,
): String {
    val chapterDir =
        SafePath.buildValidFilename(
            when {
                scanlator != null -> "${scanlator}_$title"
                else -> title
            },
        )

    return getMangaDir(mangaId) + "/" + chapterDir
}

private fun getChapterDir(
    mangaId: Int,
    chapterId: Int,
): String =
    transaction {
        val chapterEntry = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()

        // Get manga directory and combine with chapter directory
        // Note: This creates a nested transaction, but Exposed handles this with useNestedTransactions=true
        getChapterDir(mangaId, chapterEntry[ChapterTable.name], chapterEntry[ChapterTable.scanlator])
    }

fun getThumbnailDownloadPath(mangaId: Int): String = applicationDirs.thumbnailDownloadsRoot + "/$mangaId"

fun getMangaDownloadDir(
    title: String,
    sourceName: String,
): String = applicationDirs.mangaDownloadsRoot + "/" + getMangaDir(title, sourceName)

fun getMangaDownloadDir(mangaId: Int): String = applicationDirs.mangaDownloadsRoot + "/" + getMangaDir(mangaId)

fun getMangaCacheDir(
    title: String,
    sourceName: String,
): String = applicationDirs.tempMangaCacheRoot + "/" + getMangaDir(title, sourceName)

fun getChapterDownloadPath(
    mangaId: Int,
    title: String,
    scanlator: String?,
): String = applicationDirs.mangaDownloadsRoot + "/" + getChapterDir(mangaId, title, scanlator)

fun getChapterDownloadPath(
    mangaId: Int,
    chapterId: Int,
): String = applicationDirs.mangaDownloadsRoot + "/" + getChapterDir(mangaId, chapterId)

fun getChapterCbzPath(
    mangaId: Int,
    chapterId: Int,
): String = getChapterDownloadPath(mangaId, chapterId) + ".cbz"

fun getChapterCachePath(
    mangaId: Int,
    title: String,
    scanlator: String?,
): String = applicationDirs.tempMangaCacheRoot + "/" + getChapterDir(mangaId, title, scanlator)

fun getChapterCachePath(
    mangaId: Int,
    chapterId: Int,
): String = applicationDirs.tempMangaCacheRoot + "/" + getChapterDir(mangaId, chapterId)

private fun updateDir(
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

private fun updateDownloadDir(
    currentDownloadDir: String,
    newDownloadDir: String,
    currentCacheDir: String,
    newCacheDir: String,
): Boolean {
    val renamed = updateDir(currentDownloadDir, newDownloadDir)

    val tryToKeepCachedFilesUsable = renamed
    if (tryToKeepCachedFilesUsable) {
        updateDir(currentCacheDir, newCacheDir)
    }

    return renamed
}

private val mutexByManga = ConcurrentHashMap<Int, Mutex>()

suspend fun updateChapterDownloadDir(
    oldChapter: ChapterDataClass,
    newChapter: ChapterDataClass,
): Boolean {
    require(oldChapter.id == newChapter.id) { "Chapters must have the same id" }
    require(oldChapter.mangaId == newChapter.mangaId) { "Chapters must be from the same manga" }

    return mutexByManga.getOrPut(oldChapter.mangaId) { Mutex() }.withLock {
        val currentDownloadDir = getChapterDownloadPath(oldChapter.mangaId, oldChapter.name, oldChapter.scanlator)
        val newDownloadDir = getChapterDownloadPath(oldChapter.mangaId, newChapter.name, newChapter.scanlator)

        val currentCacheDir = getChapterCachePath(oldChapter.mangaId, oldChapter.name, oldChapter.scanlator)
        val newCacheDir = getChapterCachePath(oldChapter.mangaId, newChapter.name, newChapter.scanlator)

        updateDownloadDir(currentDownloadDir, newDownloadDir, currentCacheDir, newCacheDir)
    }
}

/** return value says if rename/move was successful */
suspend fun updateMangaDownloadDir(
    mangaId: Int,
    title: String,
    sourceName: String,
    newTitle: String,
): Boolean =
    mutexByManga.getOrPut(mangaId) { Mutex() }.withLock {
        val currentDownloadDir = getMangaDownloadDir(title, sourceName)
        val newDownloadDir = getMangaDownloadDir(newTitle, sourceName)

        val currentCacheDir = getMangaCacheDir(title, sourceName)
        val newCacheDir = getMangaCacheDir(newTitle, sourceName)

        updateDownloadDir(currentDownloadDir, newDownloadDir, currentCacheDir, newCacheDir)
    }
