package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.graphql.server.primitives.inIds
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Chapter.modifyChaptersMetas
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.Manga.clearThumbnail
import suwayomi.tachidesk.manga.impl.Manga.modifyMangasMetas
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.SyncRestoreMode
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
import suwayomi.tachidesk.manga.model.table.updateStrategyOrDefault
import suwayomi.tachidesk.server.database.dbTransaction
import java.util.Date
import kotlin.math.max
import kotlin.time.Clock
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
        since: Long? = null,
    ): List<BackupManga> =
        dbTransaction {
            if (!flags.includeManga) {
                return@dbTransaction emptyList()
            }

            val changed =
                since?.let {
                    val chapterChanged =
                        ChapterTable
                            .getWithUserData(userId)
                            .select(ChapterTable.manga)
                            .where { ChapterUserTable.lastModifiedAt greaterEq it }
                    (MangaUserTable.lastModifiedAt greaterEq it) or (MangaTable.id inSubQuery chapterChanged)
                } ?: Op.TRUE
            val manga =
                MangaTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { (MangaUserTable.inLibrary eq true) and changed }
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
                        viewer = mangaRow[MangaUserTable.viewer],
                        viewer_flags = mangaRow[MangaUserTable.viewerFlags],
                        chapterFlags = mangaRow[MangaUserTable.chapterFlags],
                        updateStrategy = updateStrategyOrDefault(mangaRow[MangaTable.updateStrategy]),
                        lastModifiedAt = mangaRow[MangaUserTable.lastModifiedAt],
                        version = mangaRow[MangaUserTable.version],
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
                                    read = it.getOrNull(ChapterUserTable.isRead) ?: false,
                                    bookmark = it.getOrNull(ChapterUserTable.isBookmarked) ?: false,
                                    lastPageRead = it.getOrNull(ChapterUserTable.lastPageRead) ?: 0,
                                    dateFetch = it[ChapterTable.fetchedAt].seconds.inWholeMilliseconds,
                                    dateUpload = it[ChapterTable.date_upload],
                                    chapterNumber = it[ChapterTable.chapter_number],
                                    sourceOrder = chapters.size - it[ChapterTable.sourceOrder],
                                    lastModifiedAt = it.getOrNull(ChapterUserTable.lastModifiedAt) ?: 0,
                                    version = it.getOrNull(ChapterUserTable.version) ?: 0,
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
                                val lastReadAt = it.getOrNull(ChapterUserTable.lastReadAt) ?: 0
                                if (lastReadAt > 0) {
                                    BackupHistory(
                                        url = it[ChapterTable.url],
                                        lastRead = lastReadAt.seconds.inWholeMilliseconds,
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
                                // also fill the deprecated int field 3 when the id fits:
                                // Mihon/Tachiyomi readers ignore field 100 (mediaId)
                                @Suppress("DEPRECATION")
                                val mediaIdInt =
                                    it.record.remoteId
                                        .takeIf { id -> id in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
                                        ?.toInt() ?: 0
                                BackupTracking(
                                    syncId = it.record.trackerId,
                                    // forced not null so its compatible with 1.x backup system
                                    libraryId = it.record.libraryId ?: 0,
                                    mediaId = it.record.remoteId,
                                    mediaIdInt = mediaIdInt,
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
        syncMode: SyncRestoreMode = SyncRestoreMode.NONE,
    ) {
        val chapters = backupManga.chapters
        val categories = backupManga.categories
        val history = backupManga.history
        val tracking = backupManga.tracking

        val dbCategoryIds = categories.mapNotNull { categoryMapping[it] }

        try {
            restoreMangaData(userId, backupManga, chapters, dbCategoryIds, history, tracking, flags, syncMode, errors)
        } catch (e: Exception) {
            val sourceName = sourceMapping[backupManga.source] ?: backupManga.source.toString()
            errors.add(Date() to "${backupManga.title} [$sourceName]: ${e.message}")
        }
    }

    /**
     * Decodes the wire memo, treating absent/blank/invalid payloads and the default
     * empty JSON object as "no memo" so they can never wipe stored data.
     */
    private fun decodeMemo(memo: ByteArray): JsonObject? =
        memo
            .decodeToString()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Json.decodeFromString<JsonObject>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }

    private fun restoreMangaData(
        userId: Int,
        manga: BackupManga,
        chapters: List<BackupChapter>,
        categoryIds: List<Int>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        flags: BackupFlags,
        syncMode: SyncRestoreMode,
        errors: MutableList<Pair<Date, String>>,
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
        // a newer local copy wins the next upload; categories and tracking are part of the manga's version.
        // getWithUserData is a left join: a manga without a MANGAUSER row reports 0 rather
        // than failing the whole entry
        val keepLocalManga =
            syncMode == SyncRestoreMode.ADOPT && dbManga != null &&
                manga.version < (dbManga.getOrNull(MangaUserTable.version) ?: 0)

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
                                it[status] = MangaStatus.valueOf(manga.status).value
                                it[thumbnail_url] = manga.thumbnailUrl
                                it[updateStrategy] = manga.updateStrategy.name

                                it[sourceReference] = manga.source

                                it[initialized] = manga.initialized || manga.description != null

                                it[memo] = decodeMemo(manga.memo) ?: JsonObject(emptyMap())
                            }.value
                    } else if (keepLocalManga) {
                        dbManga[MangaTable.id].value
                    } else {
                        val dbMangaId = dbManga[MangaTable.id].value

                        // Merge manga data
                        MangaTable.update({ MangaTable.id eq dbMangaId }) {
                            it[artist] = manga.artist ?: dbManga[artist]
                            it[author] = manga.author ?: dbManga[author]
                            it[description] = manga.description ?: dbManga[description]
                            it[genre] = manga.genre.ifEmpty { null }?.joinToString() ?: dbManga[genre]
                            it[status] = MangaStatus.valueOf(manga.status).value
                            it[thumbnail_url] = manga.thumbnailUrl ?: dbManga[thumbnail_url]
                            it[updateStrategy] = manga.updateStrategy.name

                            it[initialized] = dbManga[initialized] || manga.initialized || manga.description != null

                            // an absent/default "{}" backup memo must not wipe the local one
                            it[memo] = decodeMemo(manga.memo) ?: dbManga[memo]
                        }

                        dbMangaId
                    }

                if (!keepLocalManga) {
                    // a newer local copy wins: keep its MANGAUSER row untouched
                    MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                        it[MangaUserTable.user] = userId
                        it[MangaUserTable.manga] = mangaId
                        it[inLibrary] =
                            if (syncMode == SyncRestoreMode.ADOPT) {
                                manga.favorite
                            } else {
                                dbManga?.getOrNull(MangaUserTable.inLibrary) == true || manga.favorite
                            }
                        it[inLibraryAt] = manga.dateAdded.milliseconds.inWholeSeconds

                        // outside ADOPT a zeroed backup must not wipe stored flags
                        if (syncMode == SyncRestoreMode.ADOPT || manga.viewer != 0) it[viewer] = manga.viewer
                        if (syncMode == SyncRestoreMode.ADOPT || manga.viewer_flags != null) {
                            it[viewerFlags] = manga.viewer_flags
                        }
                        if (syncMode == SyncRestoreMode.ADOPT || manga.chapterFlags != 0) {
                            it[chapterFlags] = manga.chapterFlags
                        }

                        if (syncMode == SyncRestoreMode.CONVERGE) {
                            it[lastModifiedAt] = Clock.System.now().epochSeconds
                            it[version] = max(manga.version, dbManga?.getOrNull(MangaUserTable.version) ?: 0) + 1
                        } else {
                            it[lastModifiedAt] = manga.lastModifiedAt
                            it[version] = manga.version
                        }
                        it[isSyncing] = syncMode.isSync
                    }
                }

                // delete thumbnail in case cached data still exists
                clearThumbnail(mangaId)

                // keepLocalManga means an older remote copy arrived: its meta (a versionless
                // blob) must not overwrite the newer local one
                if (flags.includeClientData && manga.meta.isNotEmpty() && !keepLocalManga) {
                    modifyMangasMetas(userId, mapOf(mangaId to manga.meta))
                }

                // merge chapter data
                if (flags.includeChapters || flags.includeHistory) {
                    restoreMangaChapterData(userId, mangaId, restoreMode, chapters, history, flags, syncMode, keepLocalManga)
                }

                // update categories
                if (flags.includeCategories && !keepLocalManga) {
                    restoreMangaCategoryData(userId, mangaId, categoryIds, syncMode)
                }

                mangaId
            }

        if (flags.includeTracking && !keepLocalManga) {
            restoreMangaTrackerData(userId, mangaId, manga.title, tracks, errors)
        }

        // TODO: insert/merge history
    }

    /**
     * Merges backup chapters that share a [BackupChapter.url] (e.g. the same chapter
     * published under different scanlator names) into one entry instead of silently
     * dropping every occurrence after the first — read/bookmark state and progress are
     * merged, the freshest bookkeeping wins.
     */
    private fun mergeDuplicateChapters(chapters: List<BackupChapter>): List<BackupChapter> =
        chapters
            .groupBy { it.url }
            .map { (_, sameUrlChapters) ->
                if (sameUrlChapters.size == 1) {
                    sameUrlChapters.first()
                } else {
                    sameUrlChapters.reduce { merged, next ->
                        merged.apply {
                            read = read || next.read
                            bookmark = bookmark || next.bookmark
                            lastPageRead = max(lastPageRead, next.lastPageRead)
                            version = max(version, next.version)
                            lastModifiedAt = max(lastModifiedAt, next.lastModifiedAt)
                            if (scanlator.isNullOrBlank()) scanlator = next.scanlator
                            meta = meta + next.meta
                            // prefer a real memo over the default empty object
                            if (decodeMemo(memo) == null) memo = next.memo
                        }
                    }
                }
            }

    private fun getMangaChapterToRestoreInfo(
        mangaId: Int,
        restoreMode: RestoreMode,
        deduplicatedChapters: List<BackupChapter>,
    ): Pair<List<BackupChapter>, List<Pair<BackupChapter, ResultRow>>> {
        if (restoreMode == RestoreMode.NEW) {
            return Pair(deduplicatedChapters, emptyList())
        }

        val dbChaptersByUrl = ChapterTable.selectAll().where { ChapterTable.manga eq mangaId }.associateBy { it[ChapterTable.url] }

        val (chaptersToUpdate, chaptersToInsert) = deduplicatedChapters.partition { dbChaptersByUrl.contains(it.url) }
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
        syncMode: SyncRestoreMode,
        keepLocalManga: Boolean = false,
    ) = dbTransaction {
        val deduplicatedChapters = mergeDuplicateChapters(chapters)
        val (chaptersToInsert, allChaptersToUpdate) =
            getMangaChapterToRestoreInfo(mangaId, restoreMode, deduplicatedChapters)
        val historyByChapter = history.groupBy({ it.url }, { it.lastRead })

        val existingUserData =
            ChapterUserTable
                .selectAll()
                .where {
                    (ChapterUserTable.user eq userId) and
                        // literal, chunked IN list: bound parameters hit the driver's
                        // variable limit on large backups
                        (ChapterUserTable.chapter inIds allChaptersToUpdate.map { it.second[ChapterTable.id].value })
                }.associate { it[ChapterUserTable.chapter].value to it }

        val chaptersToUpdateToDbChapter =
            if (syncMode == SyncRestoreMode.ADOPT) {
                allChaptersToUpdate.filter { (backupChapter, dbChapter) ->
                    backupChapter.version >=
                        (existingUserData[dbChapter[ChapterTable.id].value]?.get(ChapterUserTable.version) ?: 0)
                }
            } else {
                allChaptersToUpdate
            }

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

                            // the wire order indexes into the *full* backup chapter list,
                            // not the subset that happens to be new here
                            this[ChapterTable.sourceOrder] = deduplicatedChapters.size - chapter.sourceOrder
                            this[ChapterTable.manga] = mangaId

                            this[ChapterTable.fetchedAt] = chapter.dateFetch.milliseconds.inWholeSeconds

                            // same defensive decode as the manga-level memo:
                            // non-JSON bytes must not fail the whole manga
                            this[ChapterTable.memo] = decodeMemo(chapter.memo) ?: JsonObject(emptyMap())
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

                    this[ChapterUserTable.version] = chapter.version
                    this[ChapterUserTable.lastModifiedAt] = chapter.lastModifiedAt
                    this[ChapterUserTable.isSyncing] = syncMode.isSync

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
                    if (syncMode == SyncRestoreMode.ADOPT) {
                        this[ChapterUserTable.isRead] = backupChapter.read
                        this[ChapterUserTable.lastPageRead] = backupChapter.lastPageRead.coerceAtLeast(0)
                        this[ChapterUserTable.isBookmarked] = backupChapter.bookmark
                    } else {
                        this[ChapterUserTable.isRead] =
                            backupChapter.read || (userData?.get(ChapterUserTable.isRead) ?: false)
                        this[ChapterUserTable.lastPageRead] =
                            max(backupChapter.lastPageRead, userData?.get(ChapterUserTable.lastPageRead) ?: 0)
                                .coerceAtLeast(0)
                        this[ChapterUserTable.isBookmarked] =
                            backupChapter.bookmark || (userData?.get(ChapterUserTable.isBookmarked) ?: false)
                    }
                }

                if (flags.includeHistory) {
                    this[ChapterUserTable.lastReadAt] =
                        (historyByChapter[backupChapter.url]?.maxOrNull()?.milliseconds?.inWholeSeconds ?: 0)
                            .coerceAtLeast(userData?.get(ChapterUserTable.lastReadAt) ?: 0)
                }

                when (syncMode) {
                    SyncRestoreMode.ADOPT -> {
                        this[ChapterUserTable.lastModifiedAt] = backupChapter.lastModifiedAt
                        this[ChapterUserTable.version] = backupChapter.version
                        this[ChapterUserTable.isSyncing] = true
                    }

                    SyncRestoreMode.CONVERGE -> {
                        this[ChapterUserTable.lastModifiedAt] = Clock.System.now().epochSeconds
                        this[ChapterUserTable.version] =
                            max(backupChapter.version, userData?.get(ChapterUserTable.version) ?: 0) + 1
                        this[ChapterUserTable.isSyncing] = true
                    }

                    SyncRestoreMode.NONE -> {
                        val localVersion = userData?.get(ChapterUserTable.version) ?: 0
                        this[ChapterUserTable.version] = max(backupChapter.version, localVersion)
                        // lastModifiedAt follows the version winner so a newer local row
                        // does not get stamped with the older backup timestamp
                        this[ChapterUserTable.lastModifiedAt] =
                            if (backupChapter.version >= localVersion) {
                                backupChapter.lastModifiedAt
                            } else {
                                userData?.get(ChapterUserTable.lastModifiedAt) ?: 0
                            }
                        // a file restore does not participate in sync; the row must not be
                        // left flagged as in-flight
                        this[ChapterUserTable.isSyncing] = false
                    }
                }
            }
        }

        // see the manga-level note: versionless client metas must not overwrite newer data
        if (flags.includeClientData && !keepLocalManga) {
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
        syncMode: SyncRestoreMode,
    ) {
        // CONVERGE keeps the union so a local-only link survives and wins the next upload
        if (syncMode != SyncRestoreMode.CONVERGE) {
            CategoryManga.removeMangaFromAllCategories(userId, mangaId)
        }
        CategoryManga.addMangaToCategories(userId, mangaId, categoryIds)
    }

    private fun restoreMangaTrackerData(
        userId: Int,
        mangaId: Int,
        mangaTitle: String,
        tracks: List<BackupTracking>,
        errors: MutableList<Pair<Date, String>>,
    ) {
        val dbTrackRecordsByTrackerId =
            Tracker
                .getTrackRecordsByMangaId(userId, mangaId)
                .mapNotNull { it.record?.toTrack() }
                .associateBy { it.tracker_id }

        val unsupportedTrackerIds = mutableListOf<Int>()

        val (existingTracks, newTracks) =
            tracks
                .mapNotNull { backupTrack ->
                    val track = backupTrack.toTrack(mangaId)

                    val isUnsupportedTracker = TrackerManager.getTracker(track.tracker_id) == null
                    if (isUnsupportedTracker) {
                        // no implementation on this server: report the dropped record
                        // instead of silently losing the user's tracking data
                        unsupportedTrackerIds += track.tracker_id
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
                        it.title = track.title
                        // reading progress only moves forward on merge
                        it.last_chapter_read = max(dbTrack.last_chapter_read, track.last_chapter_read)
                        it.total_chapters = track.total_chapters
                        it.status = track.status
                        it.score = track.score
                        it.tracking_url = track.tracking_url
                        it.started_reading_date = track.started_reading_date
                        it.finished_reading_date = track.finished_reading_date
                        it.private = track.private
                    }
                }.partition { (it.id ?: -1) > 0 }

        if (unsupportedTrackerIds.isNotEmpty()) {
            errors.add(
                Date() to
                    "$mangaTitle: skipped tracking records for unsupported trackers " +
                    unsupportedTrackerIds.sorted().joinToString(", "),
            )
        }

        Tracker.updateTrackRecords(userId, existingTracks)
        Tracker.insertTrackRecords(userId, newTracks)
    }

    private fun TrackRecordDataClass.forComparison() = this.copy(id = 0, mangaId = 0)
}
