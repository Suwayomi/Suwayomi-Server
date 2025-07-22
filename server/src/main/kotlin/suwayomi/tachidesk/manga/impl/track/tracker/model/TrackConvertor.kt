package suwayomi.tachidesk.manga.impl.track.tracker.model

import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupTracking
import suwayomi.tachidesk.manga.model.dataclass.TrackRecordDataClass
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.lastChapterRead
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.remoteUrl
import suwayomi.tachidesk.manga.model.table.TrackSearchTable

fun ResultRow.toTrackRecordDataClass(): TrackRecordDataClass =
    TrackRecordDataClass(
        id = this[TrackRecordTable.id].value,
        mangaId = this[TrackRecordTable.mangaId].value,
        trackerId = this[TrackRecordTable.trackerId],
        remoteId = this[TrackRecordTable.remoteId],
        libraryId = this[TrackRecordTable.libraryId],
        title = this[TrackRecordTable.title],
        lastChapterRead = this[TrackRecordTable.lastChapterRead],
        totalChapters = this[TrackRecordTable.totalChapters],
        status = this[TrackRecordTable.status],
        score = this[TrackRecordTable.score],
        remoteUrl = this[TrackRecordTable.remoteUrl],
        startDate = this[TrackRecordTable.startDate],
        finishDate = this[TrackRecordTable.finishDate],
        private = this[TrackRecordTable.private],
    )

fun ResultRow.toTrack(): Track =
    Track.create(this[TrackRecordTable.trackerId]).also {
        it.id = this[TrackRecordTable.id].value
        it.manga_id = this[TrackRecordTable.mangaId].value
        it.remote_id = this[TrackRecordTable.remoteId]
        it.library_id = this[TrackRecordTable.libraryId]
        it.title = this[TrackRecordTable.title]
        it.last_chapter_read = this[TrackRecordTable.lastChapterRead]
        it.total_chapters = this[TrackRecordTable.totalChapters]
        it.status = this[TrackRecordTable.status]
        it.score = this[TrackRecordTable.score]
        it.tracking_url = this[TrackRecordTable.remoteUrl]
        it.started_reading_date = this[TrackRecordTable.startDate]
        it.finished_reading_date = this[TrackRecordTable.finishDate]
        it.private = this[TrackRecordTable.private]
    }

fun ResultRow.toTrackSearch(): TrackSearch =
    TrackSearch.create(this[TrackSearchTable.trackerId]).also {
        it.id = this[TrackSearchTable.id].value
        it.remote_id = this[TrackSearchTable.remoteId]
        it.library_id = this[TrackSearchTable.libraryId]
        it.title = this[TrackSearchTable.title]
        it.last_chapter_read = this[TrackSearchTable.lastChapterRead]
        it.total_chapters = this[TrackSearchTable.totalChapters]
        it.status = this[TrackSearchTable.status]
        it.score = this[TrackSearchTable.score]
        it.tracking_url = this[TrackSearchTable.trackingUrl]
        it.started_reading_date = this[TrackSearchTable.startedReadingDate]
        it.finished_reading_date = this[TrackSearchTable.finishedReadingDate]
        it.private = this[TrackSearchTable.private]
        it.authors = this[TrackSearchTable.authors]?.split(",").orEmpty()
        it.artists = this[TrackSearchTable.artists]?.split(",").orEmpty()
        it.cover_url = this[TrackSearchTable.coverUrl]
        it.summary = this[TrackSearchTable.summary]
        it.publishing_status = this[TrackSearchTable.publishingStatus]
        it.publishing_type = this[TrackSearchTable.publishingType]
        it.start_date = this[TrackSearchTable.startDate]
    }

fun BackupTracking.toTrack(mangaId: Int): Track =
    Track.create(syncId).also {
        it.id = -1
        it.manga_id = mangaId
        it.remote_id = mediaId
        it.library_id = libraryId
        it.title = title
        it.last_chapter_read = lastChapterRead.toDouble()
        it.total_chapters = totalChapters
        it.status = status
        it.score = score.toDouble()
        it.tracking_url = trackingUrl
        it.started_reading_date = startedReadingDate
        it.finished_reading_date = finishedReadingDate
        it.private = private
    }

fun TrackRecordDataClass.toTrack(): Track =
    Track.create(trackerId).also {
        it.id = id
        it.manga_id = mangaId
        it.remote_id = remoteId
        it.library_id = libraryId
        it.title = title
        it.last_chapter_read = lastChapterRead
        it.total_chapters = totalChapters
        it.status = status
        it.score = score
        it.tracking_url = remoteUrl
        it.started_reading_date = startDate
        it.finished_reading_date = finishDate
        it.private = private
    }

fun Track.toTrackRecordDataClass(): TrackRecordDataClass =
    TrackRecordDataClass(
        id = id ?: -1,
        mangaId = manga_id,
        trackerId = tracker_id,
        remoteId = remote_id,
        libraryId = library_id,
        title = title,
        lastChapterRead = last_chapter_read.toDouble(),
        totalChapters = total_chapters,
        status = status,
        score = score.toDouble(),
        remoteUrl = tracking_url,
        startDate = started_reading_date,
        finishDate = finished_reading_date,
        private = private,
    )
