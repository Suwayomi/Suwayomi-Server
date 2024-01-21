package suwayomi.tachidesk.manga.impl.track.tracker.model

import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.model.dataclass.TrackRecordDataClass
import suwayomi.tachidesk.manga.model.table.TrackRecordTable

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
