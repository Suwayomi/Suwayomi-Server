package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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

    init {
        uniqueIndex(trackerId, remoteId)
    }
}

fun List<TrackSearch>.insertAll(): List<ResultRow> {
    if (isEmpty()) return emptyList()
    return transaction {
        TrackSearchTable
            .batchUpsert(this@insertAll, TrackSearchTable.trackerId, TrackSearchTable.remoteId) {
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
            }.sortedBy { row ->
                indexOfFirst {
                    it.tracker_id == row[TrackSearchTable.trackerId] &&
                        it.remote_id == row[TrackSearchTable.remoteId]
                }
            }
    }
}
