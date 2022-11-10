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
import suwayomi.tachidesk.graphql.types.MangaType
import suwayomi.tachidesk.manga.model.table.MangaTable
import java.util.concurrent.CompletableFuture

class MangaDataLoader : KotlinDataLoader<Int, MangaType> {
    override val dataLoaderName = "MangaDataLoader"
    override fun getDataLoader(): DataLoader<Int, MangaType> = DataLoaderFactory.newDataLoader<Int, MangaType> { ids ->
        CompletableFuture.supplyAsync {
            transaction {
                addLogger(StdOutSqlLogger)
                MangaTable.select { MangaTable.id inList ids }
                    .map { MangaType(it) }
            }
        }
    }
}
