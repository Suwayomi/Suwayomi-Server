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
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.SourceType
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.JavalinSetup.future

class SourceDataLoader : KotlinDataLoader<String, SourceType> {
    override val dataLoaderName = "SourceDataLoader"
    override fun getDataLoader(): DataLoader<String, SourceType> = DataLoaderFactory.newDataLoader<String, SourceType> { ids ->
        future {
            Source.getSourceList().filter { it.id in ids }
                .map { SourceType(it) }
        }
    }
}

class SourceForMangaDataLoader : KotlinDataLoader<Int, SourceType?> {
    override val dataLoaderName = "SourceForMangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, SourceType?> = DataLoaderFactory.newDataLoader<Int, SourceType?> { ids ->
        future {
            transaction {
                addLogger(StdOutSqlLogger)
                val mangaSourceMap = MangaTable
                    .select { MangaTable.id inList ids }
                    .associate { it[MangaTable.id].value to it[MangaTable.sourceReference] }

                val sourceIds = mangaSourceMap
                    .values
                    .distinct()
                    .map { it.toString() }

                val sources = Source.getSourceList()
                    .filter { it.id in sourceIds }
                    .map { SourceType(it) }
                    .associateBy { it.id }

                val mangaSourceTypeMap = mangaSourceMap.mapValues {
                    sources[it.value]
                }

                ids.map {
                    mangaSourceTypeMap[it]
                }
            }
        }
    }
}
