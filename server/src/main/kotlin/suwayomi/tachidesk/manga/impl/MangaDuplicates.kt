package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.MangaTitleNormalizer
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass

object MangaDuplicates {
    /**
     * Returns mangas already in the library whose normalized title matches
     * the target manga's normalized title. The target manga itself is excluded.
     *
     * Author is compared as a tie-breaker only when both rows have one — a
     * shared normalized title alone is enough to flag a candidate.
     */
    fun findDuplicates(mangaId: Int): List<MangaDataClass> =
        transaction {
            val target =
                MangaTable
                    .selectAll()
                    .where { MangaTable.id eq mangaId }
                    .firstOrNull()
                    ?: return@transaction emptyList()

            val targetTitleNorm = MangaTitleNormalizer.normalize(target[MangaTable.title])
            if (targetTitleNorm.isBlank()) return@transaction emptyList()

            val targetAuthorNorm = MangaTitleNormalizer.normalize(target[MangaTable.author])

            MangaTable
                .selectAll()
                .where { (MangaTable.id neq mangaId) and (MangaTable.inLibrary eq true) }
                .filter { row ->
                    val titleMatch = MangaTitleNormalizer.normalize(row[MangaTable.title]) == targetTitleNorm
                    if (!titleMatch) return@filter false
                    val rowAuthorNorm = MangaTitleNormalizer.normalize(row[MangaTable.author])
                    // If both sides have an author, they must match too. If either side is blank, accept.
                    targetAuthorNorm.isBlank() || rowAuthorNorm.isBlank() || targetAuthorNorm == rowAuthorNorm
                }.map { MangaTable.toDataClass(it) }
        }
}
