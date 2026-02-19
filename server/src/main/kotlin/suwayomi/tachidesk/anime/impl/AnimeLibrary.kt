package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.model.table.AnimeTable
import java.time.Instant

object AnimeLibrary {
    suspend fun addAnimeToLibrary(animeId: Int) {
        val animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }
        if (!animeEntry[AnimeTable.inLibrary]) {
            transaction {
                AnimeTable.update({ AnimeTable.id eq animeId }) {
                    it[inLibrary] = true
                    it[inLibraryAt] = Instant.now().epochSecond
                }
            }
        }
    }

    suspend fun removeAnimeFromLibrary(animeId: Int) {
        val animeEntry = transaction { AnimeTable.selectAll().where { AnimeTable.id eq animeId }.first() }
        if (animeEntry[AnimeTable.inLibrary]) {
            transaction {
                AnimeTable.update({ AnimeTable.id eq animeId }) {
                    it[inLibrary] = false
                }
            }
        }
    }
}
