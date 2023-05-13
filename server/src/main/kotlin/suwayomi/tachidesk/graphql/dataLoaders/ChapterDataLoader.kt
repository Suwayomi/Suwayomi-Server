/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterNodeList
import suwayomi.tachidesk.graphql.types.ChapterNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.future

class ChapterDataLoader : KotlinDataLoader<Int, ChapterType?> {
    override val dataLoaderName = "ChapterDataLoader"
    override fun getDataLoader(): DataLoader<Int, ChapterType?> = DataLoaderFactory.newDataLoader<Int, ChapterType> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val chapters = ChapterTable.select { ChapterTable.id inList ids }
                    .map { ChapterType(it) }
                    .associateBy { it.id }
                ids.map { chapters[it] }
            }
        }
    }
}

class ChaptersForMangaDataLoader : KotlinDataLoader<Int, ChapterNodeList> {
    override val dataLoaderName = "ChaptersForMangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, ChapterNodeList> = DataLoaderFactory.newDataLoader<Int, ChapterNodeList> { ids ->
        future {
            transaction {
                addLogger(Slf4jSqlDebugLogger)
                val chaptersByMangaId = ChapterTable.select { ChapterTable.manga inList ids }
                    .map { ChapterType(it) }
                    .groupBy { it.mangaId }
                ids.map { (chaptersByMangaId[it] ?: emptyList()).toNodeList() }
            }
        }
    }
}
