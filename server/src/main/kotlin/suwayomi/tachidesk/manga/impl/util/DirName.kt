package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.util.SafePath
import java.io.File
import java.nio.file.Files

private val applicationDirs: ApplicationDirs by injectLazy()

private fun getMangaDir(mangaId: Int): String =
    transaction {
        val mangaEntry = MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
        val source = GetCatalogueSource.getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])

        val sourceDir = SafePath.buildValidFilename(source.toString())
        val mangaDir = SafePath.buildValidFilename(mangaEntry[MangaTable.title])
        "$sourceDir/$mangaDir"
    }

private fun getChapterDir(
    mangaId: Int,
    chapterId: Int,
): String =
    transaction {
        // Get chapter data and build chapter-specific directory name
        val chapterEntry = ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()

        val chapterDir =
            SafePath.buildValidFilename(
                when {
                    chapterEntry[ChapterTable.scanlator] != null -> {
                        "${chapterEntry[ChapterTable.scanlator]}_${chapterEntry[ChapterTable.name]}"
                    }
                    else -> chapterEntry[ChapterTable.name]
                },
            )

        // Get manga directory and combine with chapter directory
        // Note: This creates a nested transaction, but Exposed handles this with useNestedTransactions=true
        getMangaDir(mangaId) + "/$chapterDir"
    }

fun getThumbnailDownloadPath(mangaId: Int): String = applicationDirs.thumbnailDownloadsRoot + "/$mangaId"

fun getMangaDownloadDir(mangaId: Int): String = applicationDirs.mangaDownloadsRoot + "/" + getMangaDir(mangaId)

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
    chapterId: Int,
): String = applicationDirs.tempMangaCacheRoot + "/" + getChapterDir(mangaId, chapterId)

/** return value says if rename/move was successful */
fun updateMangaDownloadDir(
    mangaId: Int,
    newTitle: String,
): Boolean {
    // Get current manga directory (uses its own transaction)
    val currentMangaDir = getMangaDir(mangaId)

    // Build new directory path
    val newMangaDir =
        transaction {
            val mangaEntry = MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
            val source = GetCatalogueSource.getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])
            val sourceDir = SafePath.buildValidFilename(source.toString())
            val newMangaDirName = SafePath.buildValidFilename(newTitle)
            "$sourceDir/$newMangaDirName"
        }

    val oldDir = "${applicationDirs.downloadsRoot}/$currentMangaDir"
    val newDir = "${applicationDirs.downloadsRoot}/$newMangaDir"

    val oldDirFile = File(oldDir)
    val newDirFile = File(newDir)

    if (!oldDirFile.exists()) {
        return true
    }

    return try {
        Files.move(oldDirFile.toPath(), newDirFile.toPath())
        true
    } catch (_: Exception) {
        false
    }
}
