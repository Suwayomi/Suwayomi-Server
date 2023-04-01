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
import suwayomi.tachidesk.graphql.types.ExtensionType
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.JavalinSetup.future

class ExtensionDataLoader : KotlinDataLoader<String, ExtensionType> {
    override val dataLoaderName = "ExtensionDataLoader"
    override fun getDataLoader(): DataLoader<String, ExtensionType> = DataLoaderFactory.newDataLoader { ids ->
        future {
            transaction {
                addLogger(StdOutSqlLogger)
                ExtensionTable.select { ExtensionTable.pkgName inList ids }
                    .map { ExtensionType(it) }
            }
        }
    }
}

class ExtensionForSourceDataLoader : KotlinDataLoader<Long, ExtensionType> {
    override val dataLoaderName = "ExtensionForSourceDataLoader"
    override fun getDataLoader(): DataLoader<Long, ExtensionType> = DataLoaderFactory.newDataLoader { ids ->
        future {
            transaction {
                addLogger(StdOutSqlLogger)
                ExtensionTable.innerJoin(SourceTable)
                    .select { SourceTable.id inList ids }
                    .map { ExtensionType(it) }
            }
        }
    }
}
