package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Chapter.modifyChaptersMetas
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.Manga.clearThumbnail
import suwayomi.tachidesk.manga.impl.Manga.modifyMangasMetas
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupHistory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupTracking
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrack
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrackRecordDataClass
import suwayomi.tachidesk.manga.model.dataclass.TrackRecordDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.database.dbTransaction
import java.util.Date
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import suwayomi.tachidesk.manga.impl.track.Track as Tracker

object BackupMangaHandler {
    private enum class RestoreMode {
        NEW,
        EXISTING,
    }

    fun backup(
        userId: Int,
        flags: BackupFlags,
    ): List<BackupManga> =
        dbTransaction {
            if (!flags.includeManga) {
                return@dbTransaction emptyList()
            }

            val manga =
                MangaTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { MangaUserTable.inLibrary eq true }
                    .toList()

            manga.map { mangaRow ->
                val backupManga =
                    BackupManga(
                        source = mangaRow[MangaTable.sourceReference],
                        url = mangaRow[MangaTable.url],
                        title = mangaRow[MangaTable.title],
                        artist = mangaRow[MangaTable.artist],
                        author = mangaRow[MangaTable.author],
                        description = mangaRow[MangaTable.description],
                        genre = mangaRow[MangaTable.genre]?.split(", ") ?: emptyList(),
                        status = MangaStatus.valueOf(mangaRow[MangaTable.status]).value,
                        thumbnailUrl = mangaRow[MangaTable.thumbnail_url],
                        dateAdded = mangaRow[MangaUserTable.inLibraryAt].seconds.inWholeMilliseconds,
                        viewer = 0, // not supported in Tachidesk
                        updateStrategy = UpdateStrategy.valueOf(mangaRow[MangaTable.updateStrategy]),
                        lastModifiedAt = mangaRow[MangaTable.lastModifiedAt],
                        version = mangaRow[MangaTable.version],
                        initialized = mangaRow[MangaTable.initialized],
                        memo = Json.encodeToString(mangaRow[MangaTable.memo]).encodeToByteArray(),
                    )

                val mangaId = mangaRow[MangaTable.id].value

                if (flags.includeClientData) {
                    backupManga.meta = Manga.getMangaMetaMap(userId, mangaId)
                }

                if (flags.includeChapters || flags.includeHistory) {
                    val chapters =
                        transaction {
                            ChapterTable
                                .getWithUserData(userId)
                                .selectAll()
                                .where { ChapterTable.manga eq mangaId }
                                .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                                .toList()
                        }

                    if (flags.includeChapters) {
                        val chapterToMeta =
                            Chapter.getChaptersMetaMaps(userId, chapters.map { it[ChapterTable.id].value })

                        backupManga.chapters =
                            chapters.map {
                                BackupChapter(
                                    url = it[ChapterTable.url],
                                    name = it[ChapterTable.name],
                                    scanlator = it[ChapterTable.scanlator],
                                    read = it[ChapterUserTable.isRead],
                                    bookmark = it[ChapterUserTable.isBookmarked],
                                    lastPageRead = it[ChapterUserTable.lastPageRead],
                                    dateFetch = it[ChapterTable.fetchedAt].seconds.inWholeMilliseconds,
                                    dateUpload = it[ChapterTable.date_upload],
                                    chapterNumber = it[ChapterTable.chapter_number],
                                    sourceOrder = chapters.size - it[ChapterTable.sourceOrder],
                                    lastModifiedAt = it[ChapterTable.lastModifiedAt],
                                    version = it[ChapterTable.version],
                                    memo = Json.encodeToString(it[ChapterTable.memo]).encodeToByteArray(),
                                ).apply {
                                    if (flags.includeClientData) {
                                        this.meta = chapterToMeta[it[ChapterTable.id].value] ?: emptyMap()
                                    }
                                }
                            }
                    }
                    if (flags.includeHistory) {
                        backupManga.history =
                            chapters.mapNotNull {
                                if (it[ChapterUserTable.lastReadAt] > 0) {
                                    BackupHistory(
                                        url = it[ChapterTable.url],
                                        lastRead = it[ChapterUserTable.lastReadAt].seconds.inWholeMilliseconds,
                                    )
                                } else {
                                    null
                                }
                            }
                    }
                }

                if (flags.includeCategories) {
                    backupManga.categories = CategoryManga.getMangaCategories(userId, mangaId).map { it.order }
                }

                if (flags.includeTracking) {
                    val tracks =
                        Tracker.getTrackRecordsByMangaId(userId, mangaRow[MangaTable.id].value).mapNotNull {
                            if (it.record == null) {
                                null
                            } else {
                                BackupTracking(
                                    syncId = it.record.trackerId,
                                    // forced not null so its compatible with 1.x backup system
                                    libraryId = it.record.libraryId ?: 0,
                                    mediaId = it.record.remoteId,
                                    title = it.record.title,
                                    lastChapterRead = it.record.lastChapterRead.toFloat(),
                                    totalChapters = it.record.totalChapters,
                                    score = it.record.score.toFloat(),
                                    status = it.record.status,
                                    startedReadingDate = it.record.startDate,
                                    finishedReadingDate = it.record.finishDate,
                                    trackingUrl = it.record.remoteUrl,
                                    private = it.record.private,
                                )
                            }
                        }
                    if (tracks.isNotEmpty()) {
                        backupManga.tracking = tracks
                    }
                }

                backupManga
            }
        }

    fun restore(
        userId: Int,
        backupManga: BackupManga,
        categoryMapping: Map<Int, Int>,
        sourceMapping: Map<Long, String>,
        errors: MutableList<Pair<Date, String>>,
        flags: BackupFlags,
    ) {
        val chapters = backupManga.chapters
        val categories = backupManga.categories
        val history = backupManga.history
        val tracking = backupManga.tracking

        val dbCategoryIds = categories.mapNotNull { categoryMapping[it] }

        try {
            restoreMangaData(userId, backupManga, chapters, dbCategoryIds, history, tracking, flags)
        } catch (e: Exception) {
            val sourceName = sourceMapping[backupManga.source] ?: backupManga.source.toString()
            errors.add(Date() to "${backupManga.title} [$sourceName]: ${e.message}")
        }
    }

    private fun restoreMangaData(
        userId: Int,
        manga: BackupManga,
        chapters: List<BackupChapter>,
        categoryIds: List<Int>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        flags: BackupFlags,
    ) {
        val dbManga =
            transaction {
                MangaTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { (MangaTable.url eq manga.url) and (MangaTable.sourceReference eq manga.source) }
                    .firstOrNull()
            }
        val restoreMode = if (dbManga != null) RestoreMode.EXISTING else RestoreMode.NEW

        val mangaId =
            transaction {
                val mangaId =
                    if (dbManga == null) {
                        // insert manga to database
                        MangaTable
                            .insertAndGetId {
                                it[url] = manga.url
                                it[title] = manga.title

                                it[artist] = manga.artist
                                it[author] = manga.author
                                it[description] = manga.description
                                it[genre] = manga.genre.joinToString()
                                it[status] = manga.status
                                it[thumbnail_url] = manga.thumbnailUrl
                                it[updateStrategy] = manga.updateStrategy.name

                                it[sourceReference] = manga.source

                                it[initialized] = manga.description != null

                                it[lastModifiedAt] = manga.lastModifiedAt
                                it[version] = manga.version
                                it[memo] = Json.decodeFromString<JsonObject>(manga.memo.decodeToString())
                            }.value
                    } else {
                        val dbMangaId = dbManga[MangaTable.id].value

                        // Merge manga data
                        MangaTable.update({ MangaTable.id eq dbMangaId }) {
                            it[artist] = manga.artist ?: dbManga[artist]
                            it[author] = manga.author ?: dbManga[author]
                            it[description] = manga.description ?: dbManga[description]
                            it[genre] = manga.genre.ifEmpty { null }?.joinToString() ?: dbManga[genre]
                            it[status] = manga.status
                            it[thumbnail_url] = manga.thumbnailUrl ?: dbManga[thumbnail_url]
                            it[updateStrategy] = manga.updateStrategy.name

                            it[initialized] = dbManga[initialized] || manga.description != null

                            it[lastModifiedAt] = manga.lastModifiedAt
                            it[version] = manga.version
                            it[memo] = Json.decodeFromString<JsonObject>(manga.memo.decodeToString())
                        }

                        dbMangaId
                    }

                MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                    it[MangaUserTable.user] = userId
                    it[MangaUserTable.manga] = mangaId
                    it[inLibrary] = dbManga?.get(MangaUserTable.inLibrary) == true ||
                        manga.favorite
                    it[inLibraryAt] = dbManga?.get(MangaUserTable.inLibraryAt)
                        ?: manga.dateAdded.milliseconds.inWholeSeconds
                }

                // delete thumbnail in case cached data still exists
                clearThumbnail(mangaId)

                if (flags.includeClientData && manga.meta.isNotEmpty()) {
                    modifyMangasMetas(userId, mapOf(mangaId to manga.meta))
                }

                // merge chapter data
                if (flags.includeChapters || flags.includeHistory) {
                    restoreMangaChapterData(userId, mangaId, restoreMode, chapters, history, flags)
                }

                // update categories
                if (flags.includeCategories) {
                    restoreMangaCategoryData(userId, mangaId, categoryIds)
                }

                mangaId
            }

        if (flags.includeTracking) {
            restoreMangaTrackerData(userId, mangaId, tracks)
        }

        // TODO: insert/merge history
    }

    private fun getMangaChapterToRestoreInfo(
        mangaId: Int,
        restoreMode: RestoreMode,
        chapters: List<BackupChapter>,
    ): Pair<List<BackupChapter>, List<Pair<BackupChapter, ResultRow>>> {
        val uniqueChapters = chapters.distinctBy { it.url }

        if (restoreMode == RestoreMode.NEW) {
            return Pair(uniqueChapters, emptyList())
        }

        val dbChaptersByUrl = ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.associateBy { it[ChapterTable.url] }

        val (chaptersToUpdate, chaptersToInsert) = uniqueChapters.partition { dbChaptersByUrl.contains(it.url) }
        val chaptersToUpdateToDbChapter = chaptersToUpdate.map { it to dbChaptersByUrl[it.url]!! }

        return chaptersToInsert to chaptersToUpdateToDbChapter
    }

    private fun restoreMangaChapterData(
        userId: Int,
        mangaId: Int,
        restoreMode: RestoreMode,
        chapters: List<BackupChapter>,
        history: List<BackupHistory>,
        flags: BackupFlags,
    ) = dbTransaction {
        val (chaptersToInsert, chaptersToUpdateToDbChapter) = getMangaChapterToRestoreInfo(mangaId, restoreMode, chapters)
        val historyByChapter = history.groupBy({ it.url }, { it.lastRead })

        val insertedChapterIds =
            if (flags.includeChapters) {
                val insertedIds =
                    ChapterTable
                        .batchInsert(chaptersToInsert) { chapter ->
                            this[ChapterTable.url] = chapter.url
                            this[ChapterTable.name] = chapter.name
                            if (chapter.dateUpload == 0L) {
                                this[ChapterTable.date_upload] = chapter.dateFetch
                            } else {
                                this[ChapterTable.date_upload] = chapter.dateUpload
                            }
                            this[ChapterTable.chapter_number] = chapter.chapterNumber
                            this[ChapterTable.scanlator] = chapter.scanlator

                            this[ChapterTable.sourceOrder] = chaptersToInsert.size - chapter.sourceOrder
                            this[ChapterTable.manga] = mangaId

                            this[ChapterTable.fetchedAt] = chapter.dateFetch.milliseconds.inWholeSeconds

                            this[ChapterTable.lastModifiedAt] = chapter.lastModifiedAt
                            this[ChapterTable.version] = chapter.version
                            this[ChapterTable.memo] = Json.decodeFromString<JsonObject>(chapter.memo.decodeToString())
                        }.map { it[ChapterTable.id].value }

                ChapterUserTable.batchUpsert(
                    insertedIds.zip(chaptersToInsert),
                    ChapterUserTable.user,
                    ChapterUserTable.chapter,
                ) { (dbChapterId, chapter) ->
                    this[ChapterUserTable.chapter] = dbChapterId
                    this[ChapterUserTable.user] = userId
                    this[ChapterUserTable.isRead] = chapter.read
                    this[ChapterUserTable.lastPageRead] = chapter.lastPageRead.coerceAtLeast(0)
                    this[ChapterUserTable.isBookmarked] = chapter.bookmark

                    if (flags.includeHistory) {
                        this[ChapterUserTable.lastReadAt] =
                            historyByChapter[chapter.url]?.maxOrNull()?.milliseconds?.inWholeSeconds ?: 0
                    }
                }

                insertedIds
            } else {
                emptyList()
            }

        if (chaptersToUpdateToDbChapter.isNotEmpty()) {
            val existingUserData =
                ChapterUserTable
                    .selectAll()
                    .where {
                        (ChapterUserTable.user eq userId) and
                            (ChapterUserTable.chapter inList chaptersToUpdateToDbChapter.map { it.second[ChapterTable.id].value })
                    }.associate { it[ChapterUserTable.chapter].value to it }

            ChapterUserTable.batchUpsert(
                chaptersToUpdateToDbChapter,
                ChapterUserTable.user,
                ChapterUserTable.chapter,
            ) { (backupChapter, dbChapter) ->
                val dbChapterId = dbChapter[ChapterTable.id].value
                val userData = existingUserData[dbChapterId]

                this[ChapterUserTable.chapter] = dbChapterId
                this[ChapterUserTable.user] = userId
                if (flags.includeChapters) {
                    this[ChapterUserTable.isRead] =
                        backupChapter.read || (userData?.get(ChapterUserTable.isRead) ?: false)
                    this[ChapterUserTable.lastPageRead] =
                        max(backupChapter.lastPageRead, userData?.get(ChapterUserTable.lastPageRead) ?: 0)
                            .coerceAtLeast(0)
                    this[ChapterUserTable.isBookmarked] =
                        backupChapter.bookmark || (userData?.get(ChapterUserTable.isBookmarked) ?: false)
                }

                if (flags.includeHistory) {
                    this[ChapterUserTable.lastReadAt] =
                        (historyByChapter[backupChapter.url]?.maxOrNull()?.milliseconds?.inWholeSeconds ?: 0)
                            .coerceAtLeast(userData?.get(ChapterUserTable.lastReadAt) ?: 0)
                }
            }
        }

        if (flags.includeClientData) {
            val chaptersToInsertByChapterId = insertedChapterIds.zip(chaptersToInsert)
            val chapterToUpdateByChapterId =
                chaptersToUpdateToDbChapter.map { (backupChapter, dbChapter) ->
                    dbChapter[ChapterTable.id].value to
                        backupChapter
                }
            val metaEntryByChapterId =
                (chaptersToInsertByChapterId + chapterToUpdateByChapterId)
                    .associate { (chapterId, backupChapter) ->
                        chapterId to backupChapter.meta
                    }

            modifyChaptersMetas(userId, metaEntryByChapterId)
        }
    }

    private fun restoreMangaCategoryData(
        userId: Int,
        mangaId: Int,
        categoryIds: List<Int>,
    ) {
        CategoryManga.removeMangaFromAllCategories(userId, mangaId)
        CategoryManga.addMangaToCategories(userId, mangaId, categoryIds)
    }

    private fun restoreMangaTrackerData(
        userId: Int,
        mangaId: Int,
        tracks: List<BackupTracking>,
    ) {
        val dbTrackRecordsByTrackerId =
            Tracker
                .getTrackRecordsByMangaId(userId, mangaId)
                .mapNotNull { it.record?.toTrack() }
                .associateBy { it.tracker_id }

        val (existingTracks, newTracks) =
            tracks
                .mapNotNull { backupTrack ->
                    val track = backupTrack.toTrack(mangaId)

                    val isUnsupportedTracker = TrackerManager.getTracker(track.tracker_id) == null
                    if (isUnsupportedTracker) {
                        return@mapNotNull null
                    }

                    val dbTrack =
                        dbTrackRecordsByTrackerId[backupTrack.syncId]
                            ?: // new track
                            return@mapNotNull track

                    if (track.toTrackRecordDataClass().forComparison() == dbTrack.toTrackRecordDataClass().forComparison()) {
                        return@mapNotNull null
                    }

                    dbTrack.also {
                        it.remote_id = track.remote_id
                        it.library_id = track.library_id
                        it.last_chapter_read = max(dbTrack.last_chapter_read, track.last_chapter_read)
                    }
                }.partition { (it.id ?: -1) > 0 }

        Tracker.updateTrackRecords(userId, existingTracks)
        Tracker.insertTrackRecords(userId, newTracks)
    }

    private fun TrackRecordDataClass.forComparison() = this.copy(id = 0, mangaId = 0)
}
