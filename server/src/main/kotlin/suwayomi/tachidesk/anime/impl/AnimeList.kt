package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.anime.model.dataclass.PagedAnimeListDataClass
import suwayomi.tachidesk.anime.model.table.AnimeTable
import suwayomi.tachidesk.anime.model.table.toDataClass

object AnimeList {
    fun proxyThumbnailUrl(animeId: Int): String = "/api/v1/anime/$animeId/thumbnail"

    suspend fun getAnimeList(
        sourceId: Long,
        pageNum: Int = 1,
        popular: Boolean,
    ): PagedAnimeListDataClass {
        require(pageNum > 0) {
            "pageNum = $pageNum is not in valid range"
        }
        val source = getCatalogueSourceOrStub(sourceId)
        val animesPage =
            if (popular) {
                source.getPopularAnime(pageNum)
            } else {
                if (source.supportsLatest) {
                    source.getLatestUpdates(pageNum)
                } else {
                    throw Exception("Source $source doesn't support latest")
                }
            }
        return animesPage.processEntries(sourceId)
    }

    fun AnimesPage.insertOrUpdate(sourceId: Long): List<Int> =
        transaction {
            val existingAnimeUrlsToId =
                AnimeTable
                    .selectAll()
                    .where { (AnimeTable.sourceReference eq sourceId) and (AnimeTable.url inList animes.map { it.url }) }
                    .associateBy { it[AnimeTable.url] }

            val existingAnimeUrls = existingAnimeUrlsToId.keys

            val animesToInsert = animes.filter { !existingAnimeUrls.contains(it.url) }

            val insertedAnimeUrlsToId =
                AnimeTable
                    .batchInsert(animesToInsert) {
                        this[AnimeTable.url] = it.url
                        this[AnimeTable.title] = it.title

                        this[AnimeTable.artist] = it.artist
                        this[AnimeTable.author] = it.author
                        this[AnimeTable.description] = it.description
                        this[AnimeTable.genre] = it.genre
                        this[AnimeTable.status] = it.status
                        this[AnimeTable.thumbnail_url] = it.thumbnail_url
                        this[AnimeTable.background_url] = it.background_url
                        this[AnimeTable.updateStrategy] = it.update_strategy.name
                        this[AnimeTable.fetchType] = it.fetch_type.name
                        this[AnimeTable.seasonNumber] = it.season_number

                        this[AnimeTable.sourceReference] = sourceId
                    }.associate { Pair(it[AnimeTable.url], it[AnimeTable.id].value) }

            val animeToUpdate =
                animes.mapNotNull { sAnime ->
                    existingAnimeUrlsToId[sAnime.url]?.let { sAnime to it }
                }

            if (animeToUpdate.isNotEmpty()) {
                BatchUpdateStatement(AnimeTable).apply {
                    animeToUpdate.forEach { (sAnime, anime) ->
                        addBatch(EntityID(anime[AnimeTable.id].value, AnimeTable))
                        this[AnimeTable.title] = sAnime.title
                        this[AnimeTable.artist] = sAnime.artist ?: anime[AnimeTable.artist]
                        this[AnimeTable.author] = sAnime.author ?: anime[AnimeTable.author]
                        this[AnimeTable.description] = sAnime.description ?: anime[AnimeTable.description]
                        this[AnimeTable.genre] = sAnime.genre ?: anime[AnimeTable.genre]
                        this[AnimeTable.status] = sAnime.status
                        this[AnimeTable.thumbnail_url] = sAnime.thumbnail_url ?: anime[AnimeTable.thumbnail_url]
                        this[AnimeTable.background_url] = sAnime.background_url ?: anime[AnimeTable.background_url]
                        this[AnimeTable.updateStrategy] = sAnime.update_strategy.name
                        this[AnimeTable.fetchType] = sAnime.fetch_type.name
                        this[AnimeTable.seasonNumber] = sAnime.season_number
                    }
                    execute(this@transaction)
                }
            }

            val animeUrlsToId = existingAnimeUrlsToId.mapValues { it.value[AnimeTable.id].value } + insertedAnimeUrlsToId

            animes.map { anime ->
                animeUrlsToId[anime.url]
                    ?: throw Exception("AnimeList::insertOrUpdate($sourceId): Something went wrong inserting browsed source anime")
            }
        }

    fun AnimesPage.processEntries(sourceId: Long): PagedAnimeListDataClass {
        val animeList =
            transaction {
                val animeIds = insertOrUpdate(sourceId)
                AnimeTable.selectAll().where { AnimeTable.id inList animeIds }.map { AnimeTable.toDataClass(it) }
            }
        return PagedAnimeListDataClass(
            animeList,
            hasNextPage,
        )
    }
}
