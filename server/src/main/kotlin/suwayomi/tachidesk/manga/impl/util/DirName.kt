package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.impl.util.storage.SafePath
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File

private val applicationDirs by DI.global.instance<ApplicationDirs>()

fun getMangaDir(mangaId: Int): String {
    val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
    val source = GetCatalogueSource.getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])

    val sourceDir = source.toString()
    val mangaDir = SafePath.buildValidFilename(mangaEntry[MangaTable.title])

    return "${applicationDirs.mangaRoot}/$sourceDir/$mangaDir"
}

fun getChapterDir(mangaId: Int, chapterId: Int): String {
    val chapterEntry = transaction { ChapterTable.select { ChapterTable.id eq chapterId }.first() }

    val chapterDir = SafePath.buildValidFilename(
        when {
            chapterEntry[ChapterTable.scanlator] != null -> "${chapterEntry[ChapterTable.scanlator]}_${chapterEntry[ChapterTable.name]}"
            else -> chapterEntry[ChapterTable.name]
        }
    )

    return getMangaDir(mangaId) + "/$chapterDir"
}

/** return value says if rename/move was successful */
fun updateMangaDownloadDir(mangaId: Int, newTitle: String): Boolean {
    val mangaEntry = transaction { MangaTable.select { MangaTable.id eq mangaId }.first() }
    val source = GetCatalogueSource.getCatalogueSourceOrStub(mangaEntry[MangaTable.sourceReference])

    val sourceDir = source.toString()
    val mangaDir = SafePath.buildValidFilename(mangaEntry[MangaTable.title])

    val newMangaDir = SafePath.buildValidFilename(newTitle)

    val oldDir = "${applicationDirs.mangaRoot}/$sourceDir/$mangaDir"
    val newDir = "${applicationDirs.mangaRoot}/$sourceDir/$newMangaDir"

    val oldDirFile = File(oldDir)
    val newDirFile = File(newDir)

    return if (oldDirFile.exists())
        oldDirFile.renameTo(newDirFile)
    else true
}
