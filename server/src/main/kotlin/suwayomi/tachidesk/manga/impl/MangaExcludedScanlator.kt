package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.model.table.MangaExcludedScanlatorTable

object MangaExcludedScanlator {
    fun getExcludedScanlators(mangaId: Int): Set<String> =
        transaction {
            MangaExcludedScanlatorTable
                .selectAll()
                .where { MangaExcludedScanlatorTable.manga eq mangaId }
                .map { it[MangaExcludedScanlatorTable.scanlator] }
                .toSet()
        }

    fun setExcludedScanlators(
        mangaId: Int,
        scanlators: Set<String>,
    ) {
        transaction {
            val current =
                MangaExcludedScanlatorTable
                    .selectAll()
                    .where { MangaExcludedScanlatorTable.manga eq mangaId }
                    .map { it[MangaExcludedScanlatorTable.scanlator] }
                    .toSet()

            val toAdd = scanlators - current
            val toRemove = current - scanlators

            if (toAdd.isNotEmpty()) {
                MangaExcludedScanlatorTable.batchInsert(toAdd) { s ->
                    this[MangaExcludedScanlatorTable.manga] = mangaId
                    this[MangaExcludedScanlatorTable.scanlator] = s
                }
            }
            if (toRemove.isNotEmpty()) {
                MangaExcludedScanlatorTable.deleteWhere {
                    (manga eq mangaId) and (scanlator inList toRemove)
                }
            }
        }
    }
}
