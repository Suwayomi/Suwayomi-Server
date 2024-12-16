package suwayomi.tachidesk.manga.impl.track.tracker.model

import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupTracking
import suwayomi.tachidesk.manga.model.dataclass.TrackRecordDataClass
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.lastChapterRead
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.remoteUrl

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
    )

fun ResultRow.toTrack(): Track =
    Track.create(this[TrackRecordTable.trackerId]).also {
        it.id = this[TrackRecordTable.id].value
        it.manga_id = this[TrackRecordTable.mangaId].value
        it.media_id = this[TrackRecordTable.remoteId]
        it.library_id = this[TrackRecordTable.libraryId]
        it.title = this[TrackRecordTable.title]
        it.last_chapter_read = this[TrackRecordTable.lastChapterRead].toFloat()
        it.total_chapters = this[TrackRecordTable.totalChapters]
        it.status = this[TrackRecordTable.status]
        it.score = this[TrackRecordTable.score].toFloat()
        it.tracking_url = this[TrackRecordTable.remoteUrl]
        it.started_reading_date = this[TrackRecordTable.startDate]
        it.finished_reading_date = this[TrackRecordTable.finishDate]
    }

fun BackupTracking.toTrack(mangaId: Int): Track =
    Track.create(syncId).also {
        it.id = -1
        it.manga_id = mangaId
        it.media_id = mediaId
        it.library_id = libraryId
        it.title = title
        it.last_chapter_read = lastChapterRead
        it.total_chapters = totalChapters
        it.status = status
        it.score = score
        it.tracking_url = trackingUrl
        it.started_reading_date = startedReadingDate
        it.finished_reading_date = finishedReadingDate
    }

fun TrackRecordDataClass.toTrack(): Track =
    Track.create(trackerId).also {
        it.id = id
        it.manga_id = mangaId
        it.media_id = remoteId
        it.library_id = libraryId
        it.title = title
        it.last_chapter_read = lastChapterRead.toFloat()
        it.total_chapters = totalChapters
        it.status = status
        it.score = score.toFloat()
        it.tracking_url = remoteUrl
        it.started_reading_date = startDate
        it.finished_reading_date = finishDate
    }

fun Track.toTrackRecordDataClass(): TrackRecordDataClass =
    TrackRecordDataClass(
        id = id ?: -1,
        mangaId = manga_id,
        trackerId = sync_id,
        remoteId = media_id,
        libraryId = library_id,
        title = title,
        lastChapterRead = last_chapter_read.toDouble(),
        totalChapters = total_chapters,
        status = status,
        score = score.toDouble(),
        remoteUrl = tracking_url,
        startDate = started_reading_date,
        finishDate = finished_reading_date,
    )
