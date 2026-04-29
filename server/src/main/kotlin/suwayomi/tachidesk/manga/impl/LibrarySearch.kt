package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass

object LibrarySearch {
    /**
     * Cross-field text search over the user's library.
     *
     * Each provided non-blank query token must match at least one of the
     * selected fields (title, author, artist, description, genre). Tokens
     * are AND-combined so multiple tokens narrow results. Matching is
     * case-insensitive and uses substring (LIKE %term%) — no fuzzy logic.
     *
     * @param query the user-entered query string (will be split on whitespace).
     * @param inLibraryOnly when true, only mangas in the user's library are searched.
     */
    fun search(
        query: String,
        inLibraryOnly: Boolean = true,
        searchTitle: Boolean = true,
        searchAuthor: Boolean = true,
        searchArtist: Boolean = false,
        searchDescription: Boolean = false,
        searchGenre: Boolean = true,
        limit: Int = 200,
    ): List<MangaDataClass> {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        return transaction {
            var condition: Op<Boolean> = Op.TRUE
            if (inLibraryOnly) condition = condition and (MangaTable.inLibrary eq true)

            for (token in tokens) {
                val pattern = LikePattern("%$token%")
                val perToken = mutableListOf<Op<Boolean>>()
                if (searchTitle) perToken += MangaTable.title.lowerCase() like pattern
                if (searchAuthor) perToken += MangaTable.author.lowerCase() like pattern
                if (searchArtist) perToken += MangaTable.artist.lowerCase() like pattern
                if (searchDescription) perToken += MangaTable.description.lowerCase() like pattern
                if (searchGenre) perToken += MangaTable.genre.lowerCase() like pattern
                if (perToken.isEmpty()) continue
                val anyOf = perToken.reduce { acc, op -> acc or op }
                condition = condition and anyOf
            }

            MangaTable
                .selectAll()
                .where { condition }
                .limit(limit)
                .map { MangaTable.toDataClass(it) }
        }
    }
}
