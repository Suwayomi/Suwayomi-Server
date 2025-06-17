package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.util.SafePath
import java.io.File

private val applicationDirs: ApplicationDirs by injectLazy()

private fun getMangaDir(mangaId: Int): String {
    val mangaEntry = getMangaEntry(mangaId)
    val source = GetCatalogueSource.getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])

    val sourceDir = SafePath.buildValidFilename(source.toString())
    val mangaDir = SafePath.buildValidFilename(mangaEntry[MangaTable.title])
    return "$sourceDir/$mangaDir"
}

private fun getChapterDir(
    mangaId: Int,
    chapterId: Int,
): String {
    val chapterEntry = transaction { ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first() }

    val sortValueComponents = chapterEntry[ChapterTable.chapter_number].toString().trim().split(".")
    var sortValue = "%06d".format(sortValueComponents[0].toInt())
    for (i in 1..sortValueComponents.lastIndex) {
        sortValue += "." + sortValueComponents[i].padStart(2, '0')
    }

    val chapterDir =
        SafePath.buildValidFilename(
            when {
                chapterEntry[ChapterTable.scanlator] != null ->
                    "$sortValue-${chapterEntry[ChapterTable.scanlator]}_${chapterEntry[ChapterTable.name]}"
                else -> "$sortValue-${chapterEntry[ChapterTable.name]}"
            },
        )

    return getMangaDir(mangaId) + "/$chapterDir"
}

fun getThumbnailDownloadPath(mangaId: Int): String = applicationDirs.thumbnailDownloadsRoot + "/$mangaId"

fun getMangaDownloadDir(mangaId: Int): String = applicationDirs.mangaDownloadsRoot + "/" + getMangaDir(mangaId)

fun getChapterDownloadPaths(
    mangaId: Int,
    chapterId: Int,
): List<String> {
    return buildList {
        add(applicationDirs.mangaDownloadsRoot + "/" + getChapterDir(mangaId, chapterId))
    }
}
//
//fun getChapterCbzPath(
//    mangaId: Int,
//    chapterId: Int,
//): List<String> {
//    return buildList {
//        getChapterDownloadPath(mangaId, chapterId).forEach {
//            add("${it}.cbz")
//        }
//    }
//}

fun getChapterCachePath(
    mangaId: Int,
    chapterId: Int,
): String = applicationDirs.tempMangaCacheRoot + "/" + getChapterDir(mangaId, chapterId)

/** return value says if rename/move was successful */
fun updateMangaDownloadDir(
    mangaId: Int,
    newTitle: String,
): Boolean {
    val mangaEntry = getMangaEntry(mangaId)
    val source = GetCatalogueSource.getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])

    val sourceDir = SafePath.buildValidFilename(source.toString())
    val mangaDir = SafePath.buildValidFilename(mangaEntry[MangaTable.title])

    val newMangaDir = SafePath.buildValidFilename(newTitle)

    val oldDir = "${applicationDirs.downloadsRoot}/$sourceDir/$mangaDir"
    val newDir = "${applicationDirs.downloadsRoot}/$sourceDir/$newMangaDir"

    val oldDirFile = File(oldDir)
    val newDirFile = File(newDir)

    return if (oldDirFile.exists()) {
        oldDirFile.renameTo(newDirFile)
    } else {
        true
    }
}

private fun getMangaEntry(mangaId: Int): ResultRow = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
