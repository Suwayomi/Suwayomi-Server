package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
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

    fun backup(flags: BackupFlags): List<BackupManga> =
        dbTransaction {
            if (!flags.includeManga) {
                return@dbTransaction emptyList()
            }

            val manga = MangaTable.selectAll().where { MangaTable.inLibrary eq true }.toList()

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
                        dateAdded = mangaRow[MangaTable.inLibraryAt].seconds.inWholeMilliseconds,
                        viewer = 0, // not supported in Tachidesk
                        updateStrategy = UpdateStrategy.valueOf(mangaRow[MangaTable.updateStrategy]),
                        lastModifiedAt = mangaRow[MangaTable.lastModifiedAt],
                        version = mangaRow[MangaTable.version],
                    )

                val mangaId = mangaRow[MangaTable.id].value

                if (flags.includeClientData) {
                    backupManga.meta = Manga.getMangaMetaMap(mangaId)
                }

                if (flags.includeChapters || flags.includeHistory) {
                    val chapters =
                        transaction {
                            ChapterTable
                                .selectAll()
                                .where { ChapterTable.manga eq mangaId }
                                .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                                .toList()
                        }

                    if (flags.includeChapters) {
                        val chapterToMeta =
                            Chapter.getChaptersMetaMaps(chapters.map { it[ChapterTable.id].value })

                        backupManga.chapters =
                            chapters.map {
                                BackupChapter(
                                    url = it[ChapterTable.url],
                                    name = it[ChapterTable.name],
                                    scanlator = it[ChapterTable.scanlator],
                                    read = it[ChapterTable.isRead],
                                    bookmark = it[ChapterTable.isBookmarked],
                                    lastPageRead = it[ChapterTable.lastPageRead],
                                    dateFetch = it[ChapterTable.fetchedAt].seconds.inWholeMilliseconds,
                                    dateUpload = it[ChapterTable.date_upload],
                                    chapterNumber = it[ChapterTable.chapter_number],
                                    sourceOrder = chapters.size - it[ChapterTable.sourceOrder],
                                    lastModifiedAt = it[ChapterTable.lastModifiedAt],
                                    version = it[ChapterTable.version],
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
                                if (it[ChapterTable.lastReadAt] > 0) {
                                    BackupHistory(
                                        url = it[ChapterTable.url],
                                        lastRead = it[ChapterTable.lastReadAt].seconds.inWholeMilliseconds,
                                    )
                                } else {
                                    null
                                }
                            }
                    }
                }

                if (flags.includeCategories) {
                    backupManga.categories = CategoryManga.getMangaCategories(mangaId).map { it.order }
                }

                if (flags.includeTracking) {
                    val tracks =
                        Tracker.getTrackRecordsByMangaId(mangaRow[MangaTable.id].value).mapNotNull {
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
            restoreMangaData(backupManga, chapters, dbCategoryIds, history, tracking, flags)
        } catch (e: Exception) {
            val sourceName = sourceMapping[backupManga.source] ?: backupManga.source.toString()
            errors.add(Date() to "${backupManga.title} [$sourceName]: ${e.message}")
        }
    }

    private fun restoreMangaData(
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

                                it[inLibrary] = manga.favorite

                                it[inLibraryAt] = manga.dateAdded.milliseconds.inWholeSeconds

                                it[lastModifiedAt] = manga.lastModifiedAt
                                it[version] = manga.version
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

                            it[inLibrary] = manga.favorite || dbManga[inLibrary]

                            it[inLibraryAt] = manga.dateAdded.milliseconds.inWholeSeconds

                            it[lastModifiedAt] = manga.lastModifiedAt
                            it[version] = manga.version
                        }

                        dbMangaId
                    }

                // delete thumbnail in case cached data still exists
                clearThumbnail(mangaId)

                if (flags.includeClientData && manga.meta.isNotEmpty()) {
                    modifyMangasMetas(mapOf(mangaId to manga.meta))
                }

                // merge chapter data
                if (flags.includeChapters || flags.includeHistory) {
                    restoreMangaChapterData(mangaId, restoreMode, chapters, history, flags)
                }

                // update categories
                if (flags.includeCategories) {
                    restoreMangaCategoryData(mangaId, categoryIds)
                }

                mangaId
            }

        if (flags.includeTracking) {
            restoreMangaTrackerData(mangaId, tracks)
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

                        this[ChapterTable.isRead] = chapter.read
                        this[ChapterTable.lastPageRead] = chapter.lastPageRead.coerceAtLeast(0)
                        this[ChapterTable.isBookmarked] = chapter.bookmark

                        this[ChapterTable.fetchedAt] = chapter.dateFetch.milliseconds.inWholeSeconds

                        if (flags.includeHistory) {
                            this[ChapterTable.lastReadAt] =
                                historyByChapter[chapter.url]?.maxOrNull()?.milliseconds?.inWholeSeconds ?: 0
                        }

                        this[ChapterTable.lastModifiedAt] = chapter.lastModifiedAt
                        this[ChapterTable.version] = chapter.version
                    }.map { it[ChapterTable.id].value }
            } else {
                emptyList()
            }

        if (chaptersToUpdateToDbChapter.isNotEmpty()) {
            BatchUpdateStatement(ChapterTable)
                .apply {
                    chaptersToUpdateToDbChapter.forEach { (backupChapter, dbChapter) ->
                        addBatch(EntityID(dbChapter[ChapterTable.id].value, ChapterTable))
                        if (flags.includeChapters) {
                            this[ChapterTable.isRead] = backupChapter.read || dbChapter[ChapterTable.isRead]
                            this[ChapterTable.lastPageRead] =
                                max(backupChapter.lastPageRead, dbChapter[ChapterTable.lastPageRead]).coerceAtLeast(0)
                            this[ChapterTable.isBookmarked] = backupChapter.bookmark || dbChapter[ChapterTable.isBookmarked]
                        }

                        if (flags.includeHistory) {
                            this[ChapterTable.lastReadAt] =
                                (historyByChapter[backupChapter.url]?.maxOrNull()?.milliseconds?.inWholeSeconds ?: 0)
                                    .coerceAtLeast(dbChapter[ChapterTable.lastReadAt])
                        }
                    }
                }.toExecutable()
                .execute(this@dbTransaction)
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

            modifyChaptersMetas(metaEntryByChapterId)
        }
    }

    private fun restoreMangaCategoryData(
        mangaId: Int,
        categoryIds: List<Int>,
    ) {
        CategoryManga.removeMangaFromAllCategories(mangaId)
        CategoryManga.addMangaToCategories(mangaId, categoryIds)
    }

    private fun restoreMangaTrackerData(
        mangaId: Int,
        tracks: List<BackupTracking>,
    ) {
        val dbTrackRecordsByTrackerId =
            Tracker
                .getTrackRecordsByMangaId(mangaId)
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

        Tracker.updateTrackRecords(existingTracks)
        Tracker.insertTrackRecords(newTracks)
    }

    private fun TrackRecordDataClass.forComparison() = this.copy(id = 0, mangaId = 0)
}
