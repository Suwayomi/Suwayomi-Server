package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar

object TrackSearchTable : IntIdTable() {
    val trackerId = integer("tracker_id")
    val remoteId = long("remote_id")
    val title = truncatingVarchar("title", 512)
    val totalChapters = integer("total_chapters")
    val trackingUrl = truncatingVarchar("tracking_url", 512)
    val coverUrl = truncatingVarchar("cover_url", 512)
    val summary = truncatingVarchar("summary", 4096)
    val publishingStatus = truncatingVarchar("publishing_status", 512)
    val publishingType = truncatingVarchar("publishing_type", 512)
    val startDate = truncatingVarchar("start_date", 128)
    val libraryId = long("library_id").nullable().default(null)
    val lastChapterRead = double("last_chapter_read").default(0.0)
    val status = integer("status").default(0)
    val score = double("score").default(0.0)
    val startedReadingDate = long("started_reading_date").default(0)
    val finishedReadingDate = long("finished_reading_date").default(0)
    val private = bool("private").default(false)
    val authors = truncatingVarchar("authors", 256).nullable().default(null)
    val artists = truncatingVarchar("artists", 256).nullable().default(null)
}

fun List<TrackSearch>.insertAll(): List<ResultRow> {
    if (isEmpty()) return emptyList()
    return transaction {
        val trackerIds = map { it.tracker_id }.toSet()
        val remoteIds = map { it.remote_id }.toSet()
        val existing =
            transaction {
                TrackSearchTable
                    .selectAll()
                    .where {
                        TrackSearchTable.trackerId inList trackerIds and
                            (TrackSearchTable.remoteId inList remoteIds)
                    }.toList()
            }

        val grouped = mutableMapOf<Boolean, MutableList<Pair<Int?, TrackSearch>>>()
        forEach { trackSearch ->
            val existingRow =
                existing.find {
                    it[TrackSearchTable.trackerId] == trackSearch.tracker_id &&
                        it[TrackSearchTable.remoteId] == trackSearch.remote_id
                }
            grouped
                .getOrPut(existingRow != null) { mutableListOf() }
                .add(existingRow?.get(TrackSearchTable.id)?.value to trackSearch)
        }
        val toUpdate = grouped[true]
        val toInsert = grouped[false]?.map { it.second }
        if (!toUpdate.isNullOrEmpty()) {
            BatchUpdateStatement(TrackSearchTable).apply {
                toUpdate.forEach { (id, trackSearch) ->
                    id ?: return@forEach
                    addBatch(EntityID(id, TrackSearchTable))
                    this[TrackSearchTable.title] = trackSearch.title
                    this[TrackSearchTable.totalChapters] = trackSearch.total_chapters
                    this[TrackSearchTable.trackingUrl] = trackSearch.tracking_url
                    this[TrackSearchTable.coverUrl] = trackSearch.cover_url
                    this[TrackSearchTable.summary] = trackSearch.summary
                    this[TrackSearchTable.publishingStatus] = trackSearch.publishing_status
                    this[TrackSearchTable.publishingType] = trackSearch.publishing_type
                    this[TrackSearchTable.startDate] = trackSearch.start_date
                    this[TrackSearchTable.libraryId] = trackSearch.library_id
                    this[TrackSearchTable.lastChapterRead] = trackSearch.last_chapter_read
                    this[TrackSearchTable.status] = trackSearch.status
                    this[TrackSearchTable.score] = trackSearch.score
                    this[TrackSearchTable.startedReadingDate] = trackSearch.started_reading_date
                    this[TrackSearchTable.finishedReadingDate] = trackSearch.finished_reading_date
                    this[TrackSearchTable.private] = trackSearch.private
                    this[TrackSearchTable.authors] = trackSearch.authors.ifEmpty { null }?.joinToString(",")
                    this[TrackSearchTable.artists] = trackSearch.artists.ifEmpty { null }?.joinToString(",")
                }
                execute(this@transaction)
            }
        }
        val insertedRows =
            if (!toInsert.isNullOrEmpty()) {
                TrackSearchTable.batchInsert(toInsert) {
                    this[TrackSearchTable.trackerId] = it.tracker_id
                    this[TrackSearchTable.remoteId] = it.remote_id
                    this[TrackSearchTable.title] = it.title
                    this[TrackSearchTable.totalChapters] = it.total_chapters
                    this[TrackSearchTable.trackingUrl] = it.tracking_url
                    this[TrackSearchTable.coverUrl] = it.cover_url
                    this[TrackSearchTable.summary] = it.summary
                    this[TrackSearchTable.publishingStatus] = it.publishing_status
                    this[TrackSearchTable.publishingType] = it.publishing_type
                    this[TrackSearchTable.startDate] = it.start_date
                    this[TrackSearchTable.libraryId] = it.library_id
                    this[TrackSearchTable.lastChapterRead] = it.last_chapter_read
                    this[TrackSearchTable.status] = it.status
                    this[TrackSearchTable.score] = it.score
                    this[TrackSearchTable.startedReadingDate] = it.started_reading_date
                    this[TrackSearchTable.finishedReadingDate] = it.finished_reading_date
                    this[TrackSearchTable.private] = it.private
                    this[TrackSearchTable.authors] = it.authors.ifEmpty { null }?.joinToString(",")
                    this[TrackSearchTable.artists] = it.artists.ifEmpty { null }?.joinToString(",")
                }
            } else {
                emptyList()
            }

        val updatedRows =
            toUpdate
                ?.mapNotNull { it.first }
                ?.let { ids ->
                    transaction { TrackSearchTable.selectAll().where { TrackSearchTable.id inList ids }.toList() }
                }.orEmpty()

        (insertedRows + updatedRows)
            .sortedBy { row ->
                indexOfFirst {
                    it.tracker_id == row[TrackSearchTable.trackerId] &&
                        it.remote_id == row[TrackSearchTable.remoteId]
                }
            }
    }
}
