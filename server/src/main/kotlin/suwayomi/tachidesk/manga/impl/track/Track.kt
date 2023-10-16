package suwayomi.tachidesk.manga.impl.track

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrack
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrackRecordDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaTrackerDataClass
import suwayomi.tachidesk.manga.model.dataclass.TrackSearchDataClass
import suwayomi.tachidesk.manga.model.dataclass.TrackerDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable

object Track {
    private val trackerManager = TrackerManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logger = KotlinLogging.logger {}

    fun getTrackerList(): List<TrackerDataClass> {
        val trackers = trackerManager.services
        return trackers.map {
            val isLogin = it.isLoggedIn
            val authUrl = if (isLogin) null else it.authUrl()
            TrackerDataClass(
                id = it.id,
                name = it.name,
                icon = it.getLogo(),
                isLogin = isLogin,
                authUrl = authUrl,
            )
        }
    }

    suspend fun login(input: LoginInput) {
        requireNotNull(input.trackerId) { "trackerId is null" }
        val tracker = trackerManager.getTracker(input.trackerId)
        if (input.callbackUrl != null) {
            tracker?.authCallback(input.callbackUrl)
        } else {
            tracker?.login(input.username ?: "", input.password ?: "")
        }
    }

    fun logout(input: LogoutInput) {
        requireNotNull(input.trackerId) { "trackerId is null" }
        val tracker = trackerManager.getTracker(input.trackerId)
        tracker?.logout()
    }

    fun getTrackRecordsByMangaId(mangaId: Int): List<MangaTrackerDataClass> {
        if (!trackerManager.hasLoggedTracker()) {
            return emptyList()
        }
        val recordMap =
            transaction {
                TrackRecordTable.select { TrackRecordTable.mangaId eq mangaId }
                    .map { it.toTrackRecordDataClass() }
            }.associateBy { it.syncId.toLong() }

        val trackers = trackerManager.services
        return trackers
            .filter { it.isLoggedIn }
            .map {
                val record = recordMap[it.id]
                if (record != null) {
                    val track =
                        Track.create(it.id).also { t ->
                            t.score = record.score.toFloat()
                        }
                    record.scoreString = it.displayScore(track)
                }
                MangaTrackerDataClass(
                    id = it.id,
                    name = it.name,
                    icon = it.getLogo(),
                    statusList = it.getStatusList(),
                    statusTextMap = it.getStatusList().associateWith { k -> it.getStatus(k) ?: "" },
                    scoreList = it.getScoreList(),
                    record = record,
                )
            }
    }

    suspend fun search(input: SearchInput): List<TrackSearchDataClass> {
        requireNotNull(input.trackerId) { "trackerId is null" }
        requireNotNull(input.title) { "title is null" }
        val tracker = trackerManager.getTracker(input.trackerId)
        val list = tracker?.search(input.title)
        return list?.map {
            TrackSearchDataClass(
                id = it.id,
                mangaId = it.manga_id,
                syncId = it.sync_id,
                mediaId = it.media_id,
                libraryId = it.library_id,
                title = it.title,
                lastChapterRead = it.last_chapter_read,
                totalChapters = it.total_chapters,
                score = it.score,
                status = it.status,
                startedReadingDate = it.started_reading_date,
                finishedReadingDate = it.finished_reading_date,
                trackingUrl = it.tracking_url,
                coverUrl = it.cover_url,
                summary = it.summary,
                publishingStatus = it.publishing_status,
                publishingType = it.publishing_type,
                startDate = it.start_date,
            )
        } ?: emptyList()
    }

    suspend fun bind(input: TrackSearchDataClass) {
        val tracker = trackerManager.getTracker(input.syncId.toLong())

        val track = input.toTrack()

        val chapter = queryMaxReadChapter(input.mangaId.toInt())
        val hasReadChapters = chapter != null
        val chapterNumber = chapter?.get(ChapterTable.chapter_number)

        tracker?.bind(track, hasReadChapters)
        val recordId = upsertTrackRecord(track)

        var lastChapterRead: Double? = null
        var startDate: Long? = null
        if (chapterNumber != null && chapterNumber > 0) {
            lastChapterRead = chapterNumber.toDouble()
        }
        if (track.started_reading_date <= 0) {
            val oldestChapter =
                transaction {
                    ChapterTable.select {
                        (ChapterTable.manga eq input.mangaId.toInt()) and (ChapterTable.isRead eq true)
                    }
                        .orderBy(ChapterTable.lastReadAt to SortOrder.ASC)
                        .limit(1)
                        .firstOrNull()
                }
            if (oldestChapter != null) {
                startDate = oldestChapter[ChapterTable.lastReadAt] * 1000
            }
        }
        if (lastChapterRead != null || startDate != null) {
            val trackUpdate =
                UpdateInput(
                    recordId = recordId,
                    lastChapterRead = lastChapterRead,
                    startDate = startDate,
                )
            update(trackUpdate)
        }
    }

    suspend fun update(input: UpdateInput) {
        if (input.unbind == true) {
            transaction {
                TrackRecordTable.deleteWhere { TrackRecordTable.id eq input.recordId!! }
            }
            return
        }
        val recordDb =
            transaction {
                TrackRecordTable.select { TrackRecordTable.id eq input.recordId!! }
                    .firstOrNull()
            }
        requireNotNull(recordDb) { "record not exist" }

        val tracker = trackerManager.getTracker(recordDb[TrackRecordTable.syncId].toLong())
        requireNotNull(tracker) { "tracker not exist" }

        if (input.status != null) {
            recordDb[TrackRecordTable.status] = input.status
            if (input.status == tracker.getCompletionStatus() && recordDb[TrackRecordTable.totalChapters] != 0) {
                recordDb[TrackRecordTable.lastChapterRead] = recordDb[TrackRecordTable.totalChapters]
            }
        }
        if (input.lastChapterRead != null) {
            if (recordDb[TrackRecordTable.lastChapterRead] == 0.0 &&
                recordDb[TrackRecordTable.lastChapterRead] < input.lastChapterRead &&
                recordDb[TrackRecordTable.status] != tracker.getRereadingStatus()
            ) {
                recordDb[TrackRecordTable.status] = tracker.getReadingStatus()
            }
            recordDb[TrackRecordTable.lastChapterRead] = input.lastChapterRead
            if (recordDb[TrackRecordTable.totalChapters] != 0 &&
                input.lastChapterRead.toInt() == recordDb[TrackRecordTable.totalChapters]
            ) {
                recordDb[TrackRecordTable.status] = tracker.getCompletionStatus()
                recordDb[TrackRecordTable.finishDate] = System.currentTimeMillis()
            }
        }
        if (input.scoreString != null) {
            val score = tracker.indexToScore(tracker.getScoreList().indexOf(input.scoreString))
            recordDb[TrackRecordTable.score] = score.toDouble()
        }
        if (input.startDate != null) {
            recordDb[TrackRecordTable.startDate] = input.startDate
        }
        if (input.finishDate != null) {
            recordDb[TrackRecordTable.finishDate] = input.finishDate
        }

        val track = recordDb.toTrack()
        tracker.update(track)

        upsertTrackRecord(track)
    }

    fun asyncTrackChapter(mangaId: Int) {
        scope.launch {
            trackChapter(mangaId)
        }
    }

    private suspend fun trackChapter(mangaId: Int) {
        val chapter = queryMaxReadChapter(mangaId)
        val chapterNumber = chapter?.get(ChapterTable.chapter_number)
        logger.debug {
            "[Tracker]mangaId $mangaId chapter:${chapter?.get(ChapterTable.name)} " +
                "chapterNumber:$chapterNumber"
        }
        if (chapterNumber != null && chapterNumber > 0) {
            trackChapter(mangaId, chapterNumber.toDouble())
        }
    }

    private fun queryMaxReadChapter(mangaId: Int): ResultRow? {
        return transaction {
            ChapterTable.select { (ChapterTable.manga eq mangaId) and (ChapterTable.isRead eq true) }
                .orderBy(ChapterTable.chapter_number to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }
    }

    private suspend fun trackChapter(
        mangaId: Int,
        chapterNumber: Double,
    ) {
        if (!trackerManager.hasLoggedTracker()) {
            return
        }

        val records =
            transaction {
                TrackRecordTable.select { TrackRecordTable.mangaId eq mangaId }
                    .toList()
            }

        records.forEach {
            val tracker = trackerManager.getTracker(it[TrackRecordTable.syncId].toLong())
            val lastChapterRead = it[TrackRecordTable.lastChapterRead]
            val isLogin = tracker?.isLoggedIn == true
            logger.debug {
                "[Tracker]trackChapter id:${tracker?.id} login:$isLogin " +
                    "mangaId:$mangaId dbChapter:$lastChapterRead toChapter:$chapterNumber"
            }
            if (isLogin && chapterNumber > lastChapterRead) {
                it[TrackRecordTable.lastChapterRead] = chapterNumber
                val track = it.toTrack()
                tracker?.update(track, true)
                upsertTrackRecord(track)
            }
        }
    }

    private fun upsertTrackRecord(track: Track): Int {
        return transaction {
            val existingRecord =
                TrackRecordTable.select {
                    (TrackRecordTable.mangaId eq track.manga_id.toInt()) and
                        (TrackRecordTable.syncId eq track.sync_id)
                }
                    .singleOrNull()

            if (existingRecord != null) {
                TrackRecordTable.update({
                    (TrackRecordTable.mangaId eq track.manga_id.toInt()) and
                        (TrackRecordTable.syncId eq track.sync_id)
                }) {
                    it[remoteId] = track.media_id.toInt()
                    it[libraryId] = track.library_id?.toInt()
                    it[title] = track.title
                    it[lastChapterRead] = track.last_chapter_read.toDouble()
                    it[totalChapters] = track.total_chapters
                    it[status] = track.status
                    it[score] = track.score.toDouble()
                    it[remoteUrl] = track.tracking_url
                    it[startDate] = track.started_reading_date
                    it[finishDate] = track.finished_reading_date
                }
                existingRecord[TrackRecordTable.id].value
            } else {
                TrackRecordTable.insertAndGetId {
                    it[mangaId] = track.manga_id.toInt()
                    it[syncId] = track.sync_id
                    it[remoteId] = track.media_id.toInt()
                    it[libraryId] = track.library_id?.toInt()
                    it[title] = track.title
                    it[lastChapterRead] = track.last_chapter_read.toDouble()
                    it[totalChapters] = track.total_chapters
                    it[status] = track.status
                    it[score] = track.score.toDouble()
                    it[remoteUrl] = track.tracking_url
                    it[startDate] = track.started_reading_date
                    it[finishDate] = track.finished_reading_date
                }.value
            }
        }
    }

    @Serializable
    data class LoginInput(
        val trackerId: Long? = null,
        val callbackUrl: String? = null,
        val username: String? = null,
        val password: String? = null,
    )

    @Serializable
    data class LogoutInput(
        val trackerId: Long? = null,
    )

    @Serializable
    data class SearchInput(
        val trackerId: Long? = null,
        val title: String? = null,
    )

    @Serializable
    data class UpdateInput(
        val recordId: Int? = null,
        val status: Int? = null,
        val lastChapterRead: Double? = null,
        val scoreString: String? = null,
        val startDate: Long? = null,
        val finishDate: Long? = null,
        val unbind: Boolean? = null,
    )
}
