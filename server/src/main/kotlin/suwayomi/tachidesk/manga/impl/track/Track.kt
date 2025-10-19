package suwayomi.tachidesk.manga.impl.track

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrack
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrackRecordDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaTrackerDataClass
import suwayomi.tachidesk.manga.model.dataclass.TrackSearchDataClass
import suwayomi.tachidesk.manga.model.dataclass.TrackerDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.finishDate
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.lastChapterRead
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.libraryId
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.mangaId
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.private
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.remoteId
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.remoteUrl
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.score
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.startDate
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.status
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.title
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.totalChapters
import suwayomi.tachidesk.manga.model.table.TrackRecordTable.trackerId
import suwayomi.tachidesk.manga.model.table.TrackSearchTable
import suwayomi.tachidesk.manga.model.table.insertAll
import suwayomi.tachidesk.server.generated.BuildConfig
import java.io.InputStream

object Track {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logger = KotlinLogging.logger {}

    fun getTrackerList(): List<TrackerDataClass> {
        val trackers = TrackerManager.services
        return trackers.map {
            val isLogin = it.isLoggedIn
            val authUrl = if (isLogin) null else it.authUrl()
            TrackerDataClass(
                id = it.id,
                name = it.name,
                icon = proxyThumbnailUrl(it.id),
                isLogin = isLogin,
                authUrl = authUrl,
            )
        }
    }

    suspend fun login(input: LoginInput) {
        val tracker = TrackerManager.getTracker(input.trackerId)!!
        if (input.callbackUrl != null) {
            tracker.authCallback(input.callbackUrl)
        } else {
            tracker.login(input.username ?: "", input.password ?: "")
        }
    }

    fun logout(input: LogoutInput) {
        val tracker = TrackerManager.getTracker(input.trackerId)!!
        tracker.logout()
    }

    fun proxyThumbnailUrl(trackerId: Int): String = "/api/v1/track/$trackerId/thumbnail"

    fun getTrackerThumbnail(trackerId: Int): Pair<InputStream, String> {
        val tracker = TrackerManager.getTracker(trackerId)!!
        val logo = BuildConfig::class.java.getResourceAsStream(tracker.getLogo())!!
        return logo to "image/png"
    }

    fun getTrackRecordsByMangaId(mangaId: Int): List<MangaTrackerDataClass> {
        val recordMap =
            transaction {
                TrackRecordTable
                    .selectAll()
                    .where { TrackRecordTable.mangaId eq mangaId }
                    .map { it.toTrackRecordDataClass() }
            }.associateBy { it.trackerId }

        val trackers = TrackerManager.services
        return trackers.map {
            val record = recordMap[it.id]
            if (record != null) {
                val track =
                    Track.create(it.id).also { t ->
                        t.score = record.score
                    }
                record.scoreString = it.displayScore(track)
            }
            MangaTrackerDataClass(
                id = it.id,
                name = it.name,
                icon = proxyThumbnailUrl(it.id),
                statusList = it.getStatusList(),
                statusTextMap = it.getStatusList().associateWith { k -> it.getStatus(k).orEmpty() },
                scoreList = it.getScoreList(),
                record = record,
            )
        }
    }

    suspend fun search(input: SearchInput): List<TrackSearchDataClass> {
        val tracker = TrackerManager.getTracker(input.trackerId)!!
        val list = tracker.search(input.title)
        return list.insertAll().map {
            TrackSearchDataClass(
                id = it[TrackSearchTable.id].value,
                trackerId = it[TrackSearchTable.trackerId],
                remoteId = it[TrackSearchTable.remoteId],
                libraryId = it[TrackSearchTable.libraryId],
                title = it[TrackSearchTable.title],
                lastChapterRead = it[TrackSearchTable.lastChapterRead],
                totalChapters = it[TrackSearchTable.totalChapters],
                trackingUrl = it[TrackSearchTable.trackingUrl],
                coverUrl = it[TrackSearchTable.coverUrl],
                summary = it[TrackSearchTable.summary],
                publishingStatus = it[TrackSearchTable.publishingStatus],
                publishingType = it[TrackSearchTable.publishingType],
                startDate = it[TrackSearchTable.startDate],
                status = it[TrackSearchTable.status],
                score = it[TrackSearchTable.score],
                scoreString = null,
                startedReadingDate = it[TrackSearchTable.startedReadingDate],
                finishedReadingDate = it[TrackSearchTable.finishedReadingDate],
                private = it[TrackSearchTable.private],
            )
        }
    }

    private fun ResultRow.toTrackFromSearch(mangaId: Int): Track =
        Track.create(this[TrackSearchTable.trackerId]).also {
            it.manga_id = mangaId
            it.remote_id = this[TrackSearchTable.remoteId]
            it.title = this[TrackSearchTable.title]
            it.total_chapters = this[TrackSearchTable.totalChapters]
            it.tracking_url = this[TrackSearchTable.trackingUrl]
        }

    suspend fun bind(
        mangaId: Int,
        trackerId: Int,
        remoteId: Long,
        private: Boolean,
    ) {
        val track =
            transaction {
                TrackSearchTable
                    .selectAll()
                    .where {
                        TrackSearchTable.trackerId eq trackerId and
                            (TrackSearchTable.remoteId eq remoteId)
                    }.firstOrNull()
                    ?.toTrackFromSearch(mangaId)
                    ?: TrackRecordTable
                        .selectAll()
                        .where {
                            (TrackRecordTable.trackerId eq trackerId) and
                                (TrackRecordTable.remoteId eq remoteId)
                        }.first()
                        .toTrack()
            }.apply {
                this.manga_id = mangaId
                this.private = private
            }

        val tracker = TrackerManager.getTracker(trackerId)!!

        val chapter = queryMaxReadChapter(mangaId)
        val hasReadChapters = chapter != null
        val chapterNumber = chapter?.get(ChapterTable.chapter_number)

        tracker.bind(track, hasReadChapters)
        val recordId = upsertTrackRecord(track)

        var lastChapterRead: Double? = null
        var startDate: Long? = null
        if (chapterNumber != null && chapterNumber > 0 && chapterNumber > track.last_chapter_read) {
            lastChapterRead = chapterNumber.toDouble()
        }
        if (track.started_reading_date <= 0) {
            val oldestChapter =
                transaction {
                    ChapterTable
                        .selectAll()
                        .where {
                            (ChapterTable.manga eq mangaId) and (ChapterTable.isRead eq true)
                        }.orderBy(ChapterTable.lastReadAt to SortOrder.ASC)
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

    suspend fun refresh(recordId: Int) {
        val recordDb =
            transaction {
                TrackRecordTable.selectAll().where { TrackRecordTable.id eq recordId }.first()
            }

        val tracker = TrackerManager.getTracker(recordDb[TrackRecordTable.trackerId])!!

        val track = recordDb.toTrack()
        tracker.refresh(track)
        upsertTrackRecord(track)
    }

    suspend fun unbind(
        recordId: Int,
        deleteRemoteTrack: Boolean? = false,
    ) {
        val recordDb =
            transaction {
                TrackRecordTable.selectAll().where { TrackRecordTable.id eq recordId }.first()
            }

        val tracker = TrackerManager.getTracker(recordDb[TrackRecordTable.trackerId])

        if (deleteRemoteTrack == true && tracker is DeletableTracker) {
            tracker.delete(recordDb.toTrack())
        }

        transaction {
            TrackRecordTable.deleteWhere { TrackRecordTable.id eq recordId }
        }
    }

    suspend fun update(input: UpdateInput) {
        if (input.unbind == true) {
            unbind(input.recordId)
            return
        }
        val recordDb =
            transaction {
                TrackRecordTable.selectAll().where { TrackRecordTable.id eq input.recordId }.first()
            }

        val tracker = TrackerManager.getTracker(recordDb[TrackRecordTable.trackerId])!!

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
            recordDb[TrackRecordTable.score] = score
        }
        if (input.startDate != null) {
            recordDb[TrackRecordTable.startDate] = input.startDate
        }
        if (input.finishDate != null) {
            recordDb[TrackRecordTable.finishDate] = input.finishDate
        }
        if (input.private != null) {
            recordDb[TrackRecordTable.private] = input.private
        }

        val track = recordDb.toTrack()
        tracker.update(track)

        upsertTrackRecord(track)
    }

    fun asyncTrackChapter(mangaIds: Set<Int>) {
        if (!TrackerManager.hasLoggedTracker()) {
            return
        }
        scope.launch {
            mangaIds.forEach {
                trackChapter(it)
            }
        }
    }

    suspend fun trackChapter(mangaId: Int) {
        val chapter = queryMaxReadChapter(mangaId)
        val chapterNumber = chapter?.get(ChapterTable.chapter_number)

        logger.info {
            "trackChapter(mangaId= $mangaId): maxReadChapter= #$chapterNumber ${chapter?.get(ChapterTable.name)}"
        }

        if (chapterNumber != null && chapterNumber > 0) {
            trackChapter(mangaId, chapterNumber.toDouble())
        }
    }

    private fun queryMaxReadChapter(mangaId: Int): ResultRow? =
        transaction {
            ChapterTable
                .selectAll()
                .where { (ChapterTable.manga eq mangaId) and (ChapterTable.isRead eq true) }
                .orderBy(ChapterTable.chapter_number to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }

    private suspend fun trackChapter(
        mangaId: Int,
        chapterNumber: Double,
    ) {
        val records =
            transaction {
                TrackRecordTable
                    .selectAll()
                    .where { TrackRecordTable.mangaId eq mangaId }
                    .toList()
            }

        records.forEach {
            try {
                trackChapterForTracker(it, chapterNumber)
            } catch (e: Exception) {
                KotlinLogging
                    .logger("${logger.name}::trackChapter(mangaId= $mangaId, chapterNumber= $chapterNumber)")
                    .error(e) { "failed due to" }
            }
        }
    }

    private suspend fun trackChapterForTracker(
        it: ResultRow,
        chapterNumber: Double,
    ) {
        val tracker = TrackerManager.getTracker(it[TrackRecordTable.trackerId]) ?: return
        val track = it.toTrack()

        val log =
            KotlinLogging.logger {
                "${logger.name}::trackChapterForTracker(chapterNumber= $chapterNumber, tracker= ${tracker.id}, recordId= ${track.id})"
            }
        log.debug { "called for $tracker, ${track.title} (recordId= ${track.id}, mangaId= ${track.manga_id})" }

        val localLastReadChapter = it[TrackRecordTable.lastChapterRead]

        if (localLastReadChapter == chapterNumber) {
            log.debug { "new chapter is the same as the local last read chapter" }
            return
        }

        if (!tracker.isLoggedIn) {
            upsertTrackRecord(track)
            return
        }

        tracker.refresh(track)
        upsertTrackRecord(track)

        val lastChapterRead = track.last_chapter_read

        log.debug { "remoteLastReadChapter= $lastChapterRead" }

        if (chapterNumber > lastChapterRead) {
            track.last_chapter_read = chapterNumber
            tracker.update(track, true)
            upsertTrackRecord(track)
        }
    }

    fun upsertTrackRecord(track: Track): Int =
        transaction {
            val existingRecord =
                TrackRecordTable
                    .selectAll()
                    .where {
                        (TrackRecordTable.mangaId eq track.manga_id) and
                            (TrackRecordTable.trackerId eq track.tracker_id)
                    }.singleOrNull()

            if (existingRecord != null) {
                track.id = existingRecord[TrackRecordTable.id].value
                updateTrackRecord(track)
                track.id!!
            } else {
                insertTrackRecord(track)
            }
        }

    fun updateTrackRecord(track: Track) = updateTrackRecords(listOf(track))

    fun updateTrackRecords(tracks: List<Track>) =
        transaction {
            if (tracks.isNotEmpty()) {
                BatchUpdateStatement(TrackRecordTable).apply {
                    tracks.forEach {
                        addBatch(EntityID(it.id!!, TrackRecordTable))
                        this[remoteId] = it.remote_id
                        this[libraryId] = it.library_id
                        this[title] = it.title
                        this[lastChapterRead] = it.last_chapter_read
                        this[totalChapters] = it.total_chapters
                        this[status] = it.status
                        this[score] = it.score
                        this[remoteUrl] = it.tracking_url
                        this[startDate] = it.started_reading_date
                        this[finishDate] = it.finished_reading_date
                        this[private] = it.private
                    }
                    execute(this@transaction)
                }
            }
        }

    fun insertTrackRecord(track: Track): Int = insertTrackRecords(listOf(track)).first()

    fun insertTrackRecords(tracks: List<Track>): List<Int> =
        transaction {
            TrackRecordTable
                .batchInsert(tracks) {
                    this[mangaId] = it.manga_id
                    this[trackerId] = it.tracker_id
                    this[remoteId] = it.remote_id
                    this[libraryId] = it.library_id
                    this[title] = it.title
                    this[lastChapterRead] = it.last_chapter_read
                    this[totalChapters] = it.total_chapters
                    this[status] = it.status
                    this[score] = it.score
                    this[remoteUrl] = it.tracking_url
                    this[startDate] = it.started_reading_date
                    this[finishDate] = it.finished_reading_date
                    this[private] = it.private
                }.map { it[TrackRecordTable.id].value }
        }

    @Serializable
    data class LoginInput(
        val trackerId: Int,
        val callbackUrl: String? = null,
        val username: String? = null,
        val password: String? = null,
    )

    @Serializable
    data class LogoutInput(
        val trackerId: Int,
    )

    @Serializable
    data class SearchInput(
        val trackerId: Int,
        val title: String,
    )

    @Serializable
    data class UpdateInput(
        val recordId: Int,
        val status: Int? = null,
        val lastChapterRead: Double? = null,
        val scoreString: String? = null,
        val startDate: Long? = null,
        val finishDate: Long? = null,
        val unbind: Boolean? = null,
        val private: Boolean? = null,
    )

    fun String.htmlDecode(): String = Jsoup.parse(this).wholeText()
}
