package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.model.dataclass.ScanlatorAliasDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.ScanlatorAliasTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import java.util.concurrent.ConcurrentHashMap

object ScanlatorAlias {
    private val cache = ConcurrentHashMap<String, String>()
    @Volatile private var cacheLoaded = false

    private fun ensureCache() {
        if (cacheLoaded) return
        synchronized(this) {
            if (cacheLoaded) return
            transaction {
                ScanlatorAliasTable.selectAll().forEach { row ->
                    cache[row[ScanlatorAliasTable.scanlator]] = row[ScanlatorAliasTable.displayName]
                }
            }
            cacheLoaded = true
        }
    }

    private fun invalidateCache() {
        cache.clear()
        cacheLoaded = false
    }

    /** Returns alias for given scanlator, or null if no alias is configured. */
    fun getAlias(scanlator: String?): String? {
        if (scanlator.isNullOrBlank()) return null
        ensureCache()
        return cache[scanlator]
    }

    /** Returns alias if set, otherwise the original scanlator (or null if scanlator was null). */
    fun resolve(scanlator: String?): String? = scanlator?.let { getAlias(it) ?: it }

    fun list(): List<ScanlatorAliasDataClass> =
        transaction {
            ScanlatorAliasTable
                .selectAll()
                .orderBy(ScanlatorAliasTable.scanlator)
                .map { ScanlatorAliasTable.toDataClass(it) }
        }

    fun get(id: Int): ScanlatorAliasDataClass? =
        transaction {
            ScanlatorAliasTable
                .selectAll()
                .where { ScanlatorAliasTable.id eq id }
                .firstOrNull()
                ?.let { ScanlatorAliasTable.toDataClass(it) }
        }

    fun getByScanlator(scanlator: String): ScanlatorAliasDataClass? =
        transaction {
            ScanlatorAliasTable
                .selectAll()
                .where { ScanlatorAliasTable.scanlator eq scanlator }
                .firstOrNull()
                ?.let { ScanlatorAliasTable.toDataClass(it) }
        }

    fun create(
        scanlator: String,
        displayName: String,
    ): ScanlatorAliasDataClass {
        require(scanlator.isNotBlank()) { "'scanlator' must not be blank" }
        require(displayName.isNotBlank()) { "'displayName' must not be blank" }

        val now = System.currentTimeMillis()
        val newId =
            transaction {
                require(
                    ScanlatorAliasTable
                        .selectAll()
                        .where { ScanlatorAliasTable.scanlator eq scanlator }
                        .empty(),
                ) {
                    "An alias for '$scanlator' already exists"
                }
                ScanlatorAliasTable
                    .insertAndGetId {
                        it[ScanlatorAliasTable.scanlator] = scanlator
                        it[ScanlatorAliasTable.displayName] = displayName
                        it[ScanlatorAliasTable.createdAt] = now
                        it[ScanlatorAliasTable.updatedAt] = now
                    }.value
            }
        invalidateCache()
        return get(newId) ?: error("Failed to load created scanlator alias")
    }

    fun update(
        id: Int,
        displayName: String,
    ): ScanlatorAliasDataClass {
        require(displayName.isNotBlank()) { "'displayName' must not be blank" }
        val now = System.currentTimeMillis()
        transaction {
            val updated =
                ScanlatorAliasTable.update({ ScanlatorAliasTable.id eq id }) {
                    it[ScanlatorAliasTable.displayName] = displayName
                    it[ScanlatorAliasTable.updatedAt] = now
                }
            require(updated > 0) { "ScanlatorAlias with id=$id not found" }
        }
        invalidateCache()
        return get(id) ?: error("Failed to reload updated scanlator alias")
    }

    data class DistinctScanlator(
        val scanlator: String,
        val chapterCount: Long,
        val currentAlias: String?,
    )

    /**
     * Lists every distinct, non-blank scanlator currently present in the
     * Chapter table, with chapter counts and the configured alias if any.
     * Useful for building a "configure aliases" settings UI without making
     * the user know exact scanlator strings up front.
     */
    fun listDistinctScanlators(inLibraryOnly: Boolean = false): List<DistinctScanlator> {
        ensureCache()
        val countCol = ChapterTable.id.count()
        return transaction {
            val rows =
                if (inLibraryOnly) {
                    (ChapterTable innerJoin MangaTable)
                        .select(ChapterTable.scanlator, countCol)
                        .where { (ChapterTable.scanlator neq null) and (MangaTable.inLibrary eq true) }
                        .groupBy(ChapterTable.scanlator)
                        .toList()
                } else {
                    ChapterTable
                        .select(ChapterTable.scanlator, countCol)
                        .where { ChapterTable.scanlator neq null }
                        .groupBy(ChapterTable.scanlator)
                        .toList()
                }
            rows
                .mapNotNull { row ->
                    val scan = row[ChapterTable.scanlator]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    DistinctScanlator(
                        scanlator = scan,
                        chapterCount = row[countCol],
                        currentAlias = cache[scan],
                    )
                }.sortedByDescending { it.chapterCount }
        }
    }

    fun delete(id: Int): Boolean {
        val removed =
            transaction {
                ScanlatorAliasTable.deleteWhere { ScanlatorAliasTable.id eq id }
            }
        if (removed > 0) invalidateCache()
        return removed > 0
    }
}
