package suwayomi.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import mu.KotlinLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.anime.impl.extension.Extension.getExtensionIconUrl
import suwayomi.anime.impl.util.GetAnimeHttpSource.getAnimeHttpSource
import suwayomi.anime.model.dataclass.AnimeSourceDataClass
import suwayomi.anime.model.table.AnimeExtensionTable
import suwayomi.anime.model.table.AnimeSourceTable

object Source {
    private val logger = KotlinLogging.logger {}

    fun getSourceList(): List<AnimeSourceDataClass> {
        return transaction {
            AnimeSourceTable.selectAll().map {
                AnimeSourceDataClass(
                    it[AnimeSourceTable.id].value.toString(),
                    it[AnimeSourceTable.name],
                    it[AnimeSourceTable.lang],
                    getExtensionIconUrl(AnimeExtensionTable.select { AnimeExtensionTable.id eq it[AnimeSourceTable.extension] }.first()[AnimeExtensionTable.apkName]),
                    getAnimeHttpSource(it[AnimeSourceTable.id].value).supportsLatest
                )
            }
        }
    }

    fun getAnimeSource(sourceId: Long): AnimeSourceDataClass {
        return transaction {
            val source = AnimeSourceTable.select { AnimeSourceTable.id eq sourceId }.firstOrNull()

            AnimeSourceDataClass(
                sourceId.toString(),
                source?.get(AnimeSourceTable.name),
                source?.get(AnimeSourceTable.lang),
                source?.let { AnimeExtensionTable.select { AnimeExtensionTable.id eq source[AnimeSourceTable.extension] }.first()[AnimeExtensionTable.iconUrl] },
                source?.let { getAnimeHttpSource(sourceId).supportsLatest }
            )
        }
    }
}
