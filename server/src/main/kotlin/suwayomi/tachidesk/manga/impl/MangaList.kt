package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrStub
import suwayomi.tachidesk.manga.model.dataclass.PagedMangaListDataClass
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import java.time.Instant

object MangaList {
    fun proxyThumbnailUrl(mangaId: Int): String = "/api/v1/manga/$mangaId/thumbnail"

    suspend fun getMangaList(
        sourceId: Long,
        pageNum: Int = 1,
        popular: Boolean,
    ): PagedMangaListDataClass {
        require(pageNum > 0) {
            "pageNum = $pageNum is not in valid range"
        }
        val source = getSourceOrStub(sourceId)
        val mangasPage =
            if (popular) {
                source.getPopularManga(pageNum)
            } else {
                if (source.supportsLatest) {
                    source.getLatestUpdates(pageNum)
                } else {
                    throw Exception("Source $source doesn't support latest")
                }
            }
        return mangasPage.processEntries(sourceId)
    }

    fun MangasPage.insertOrUpdate(sourceId: Long): List<Int> {
        val thumbnailCleanupTargets = linkedSetOf<Manga.ThumbnailCleanupTarget>()
        val mangaIds =
            transaction {
                val existingMangaUrlsToId =
                    MangaTable
                        .leftJoin(MangaUserTable)
                        .selectAll()
                        .where {
                            (MangaTable.sourceReference eq sourceId) and
                                (MangaTable.url inList mangas.map { it.url })
                        }.groupBy { it[MangaTable.url] }
                val existingMangaUrls = existingMangaUrlsToId.map { it.key }

                val mangasToInsert = mangas.filter { !existingMangaUrls.contains(it.url) }

                val insertedVersions = mangasToInsert.associate { it.url to System.currentTimeMillis() }
                val insertedMangaUrlsToId =
                    MangaTable
                        .batchInsert(mangasToInsert) {
                            this[MangaTable.url] = it.url
                            this[MangaTable.title] = it.title

                            this[MangaTable.artist] = it.artist
                            this[MangaTable.author] = it.author
                            this[MangaTable.description] = it.description
                            this[MangaTable.genre] = it.genre
                            this[MangaTable.status] = it.status
                            this[MangaTable.thumbnail_url] = it.thumbnail_url
                            this[MangaTable.thumbnailUrlVersion] = insertedVersions.getValue(it.url)
                            this[MangaTable.updateStrategy] = it.update_strategy.name
                            this[MangaTable.memo] = it.memo

                            this[MangaTable.sourceReference] = sourceId
                        }.associate { Pair(it[MangaTable.url], it[MangaTable.id].value) }

                insertedMangaUrlsToId.forEach { (_, id) ->
                    thumbnailCleanupTargets += Manga.ThumbnailCleanupTarget(id)
                }

                val mangaToUpdate =
                    mangas
                        .mapNotNull { sManga ->
                            existingMangaUrlsToId[sManga.url]?.let { sManga to it }
                        }.filterNot { (_, resultRows) ->
                            resultRows.any {
                                it.getOrNull(MangaUserTable.inLibrary) == true &&
                                    it[MangaTable.sourceReference] != LocalSource.ID
                            }
                        }

                if (mangaToUpdate.isNotEmpty()) {
                    // ORDER BY id is the row-lock order; judge bumps against these locked rows.
                    val mangaToUpdateIds = mangaToUpdate.map { (_, mangas) -> mangas.first()[MangaTable.id].value }
                    val lockedThumbnails =
                        MangaTable
                            .select(MangaTable.id, MangaTable.thumbnail_url, MangaTable.thumbnailUrlVersion)
                            .where { MangaTable.id inList mangaToUpdateIds }
                            .orderBy(MangaTable.id to SortOrder.ASC)
                            .forUpdate(ForUpdateOption.ForUpdate)
                            .associate {
                                it[MangaTable.id].value to
                                    (it[MangaTable.thumbnail_url] to it[MangaTable.thumbnailUrlVersion])
                            }
                    val coverChangedIds =
                        mangaToUpdate
                            .mapNotNull { (sManga, mangas) ->
                                val mangaId = mangas.first()[MangaTable.id].value
                                if (!sManga.thumbnail_url.isNullOrEmpty() && lockedThumbnails[mangaId]?.first != sManga.thumbnail_url) {
                                    mangaId
                                } else {
                                    null
                                }
                            }.toSet()
                    val unchangedCoverUpdates =
                        mangaToUpdate.filterNot { (_, mangas) ->
                            mangas.first()[MangaTable.id].value in coverChangedIds
                        }

                    if (unchangedCoverUpdates.isNotEmpty()) {
                        BatchUpdateStatement(MangaTable)
                            .apply {
                                unchangedCoverUpdates.forEach { (sManga, mangas) ->
                                    val manga = mangas.first()
                                    val mangaId = manga[MangaTable.id].value
                                    addBatch(EntityID(mangaId, MangaTable))
                                    assignBrowseMetadata(sManga, manga)
                                }
                            }.toExecutable()
                            .execute(this@transaction)
                    }

                    val changedCoverUpdates =
                        mangaToUpdate.filter { (_, mangas) ->
                            mangas.first()[MangaTable.id].value in coverChangedIds
                        }
                    if (changedCoverUpdates.isNotEmpty()) {
                        val lastFetched = Instant.now().epochSecond
                        BatchUpdateStatement(MangaTable)
                            .apply {
                                changedCoverUpdates.forEach { (sManga, mangas) ->
                                    val manga = mangas.first()
                                    val mangaId = manga[MangaTable.id].value
                                    val lockedVersion = lockedThumbnails.getValue(mangaId).second
                                    val newVersion = maxOf(lockedVersion + 1, System.currentTimeMillis())
                                    addBatch(EntityID(mangaId, MangaTable))
                                    assignBrowseMetadata(sManga, manga)
                                    this[MangaTable.thumbnail_url] = sManga.thumbnail_url
                                    this[MangaTable.thumbnailUrlLastFetched] = lastFetched
                                    this[MangaTable.thumbnailUrlVersion] = newVersion
                                    thumbnailCleanupTargets += Manga.ThumbnailCleanupTarget(mangaId, lockedVersion <= 0)
                                }
                            }.toExecutable()
                            .execute(this@transaction)
                    }
                }

                val mangaUrlsToId =
                    existingMangaUrlsToId
                        .mapValues { it.value.first()[MangaTable.id].value } + insertedMangaUrlsToId

                mangas.map { manga ->
                    mangaUrlsToId[manga.url]
                        ?: throw Exception("MangaList::insertOrGet($sourceId): Something went wrong inserting browsed source mangas")
                }
            }

        thumbnailCleanupTargets.forEach { Manga.invalidateThumbnailState(it.mangaId) }
        Manga.deleteStaleThumbnailFiles(thumbnailCleanupTargets)
        return mangaIds
    }

    private fun UpdateBuilder<*>.assignBrowseMetadata(
        sManga: SManga,
        manga: ResultRow,
    ) {
        this[MangaTable.title] = sManga.title
        this[MangaTable.artist] = sManga.artist ?: manga[MangaTable.artist]
        this[MangaTable.author] = sManga.author ?: manga[MangaTable.author]
        this[MangaTable.description] = sManga.description ?: manga[MangaTable.description]
        this[MangaTable.genre] = sManga.genre ?: manga[MangaTable.genre]
        this[MangaTable.status] = sManga.status
        this[MangaTable.updateStrategy] = sManga.update_strategy.name
        this[MangaTable.memo] = sManga.memo
    }

    fun MangasPage.processEntries(sourceId: Long): PagedMangaListDataClass {
        val mangasPage = this
        val mangaIds = insertOrUpdate(sourceId)
        val mangaList =
            transaction {
                MangaTable
                    .selectAll()
                    .where {
                        MangaTable.id inList mangaIds
                    }.map { MangaTable.toDataClass(it) }
            }
        return PagedMangaListDataClass(
            mangaList,
            mangasPage.hasNextPage,
        )
    }
}
