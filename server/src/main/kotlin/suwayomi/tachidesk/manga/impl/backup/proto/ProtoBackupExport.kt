package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okio.Buffer
import okio.Sink
import okio.buffer
import okio.gzip
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.global.impl.GlobalMeta
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupHistory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSource
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupTracking
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

object ProtoBackupExport : ProtoBackupBase() {
    private val logger = KotlinLogging.logger { }
    private val applicationDirs: ApplicationDirs by injectLazy()
    private var backupSchedulerJobId: String = ""
    private const val LAST_AUTOMATED_BACKUP_KEY = "lastAutomatedBackup"
    private val preferences = Injekt.get<Application>().getSharedPreferences("server_util", Context.MODE_PRIVATE)
    private const val AUTO_BACKUP_FILENAME = "auto"

    init {
        serverConfig.subscribeTo(
            combine(serverConfig.backupInterval, serverConfig.backupTime) { interval, timeOfDay ->
                Pair(
                    interval,
                    timeOfDay,
                )
            },
            ::scheduleAutomatedBackupTask,
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleAutomatedBackupTask() {
        HAScheduler.descheduleCron(backupSchedulerJobId)

        val areAutomatedBackupsDisabled = serverConfig.backupInterval.value == 0
        if (areAutomatedBackupsDisabled) {
            return
        }

        val task = {
            try {
                cleanupAutomatedBackups()
                createAutomatedBackup()
                preferences.edit().putLong(LAST_AUTOMATED_BACKUP_KEY, System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                logger.error(e) { "scheduleAutomatedBackupTask: failed due to" }
            }
        }

        val (hour, minute) =
            serverConfig.backupTime.value
                .split(":")
                .map { it.toInt() }
        val backupHour = hour.coerceAtLeast(0).coerceAtMost(23)
        val backupMinute = minute.coerceAtLeast(0).coerceAtMost(59)
        val backupInterval =
            serverConfig.backupInterval.value.days
                .coerceAtLeast(1.days)

        // trigger last backup in case the server wasn't running on the scheduled time
        val lastAutomatedBackup = preferences.getLong(LAST_AUTOMATED_BACKUP_KEY, 0)
        val wasPreviousBackupTriggered =
            (System.currentTimeMillis() - lastAutomatedBackup) < backupInterval.inWholeMilliseconds
        if (!wasPreviousBackupTriggered) {
            GlobalScope.launch(Dispatchers.IO) {
                task()
            }
        }

        backupSchedulerJobId = HAScheduler.scheduleCron(task, "$backupMinute $backupHour */${backupInterval.inWholeDays} * *", "backup")
    }

    private fun createAutomatedBackup() {
        logger.info { "Creating automated backup..." }

        createBackup(
            BackupFlags(
                includeManga = true,
                includeCategories = true,
                includeChapters = true,
                includeTracking = true,
                includeHistory = true,
                includeClientData = true,
                includeServerSettings = true,
            ),
        ).use { input ->
            val automatedBackupDir = File(applicationDirs.automatedBackupRoot)
            automatedBackupDir.mkdirs()

            val backupFile = File(applicationDirs.automatedBackupRoot, Backup.getFilename(AUTO_BACKUP_FILENAME))

            backupFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun cleanupAutomatedBackups() {
        logger.debug { "Cleanup automated backups (ttl= ${serverConfig.backupTTL.value})" }

        val isCleanupDisabled = serverConfig.backupTTL.value == 0
        if (isCleanupDisabled) {
            return
        }

        val automatedBackupDir = File(applicationDirs.automatedBackupRoot)
        if (!automatedBackupDir.isDirectory) {
            return
        }

        automatedBackupDir.listFiles { file -> file.name.startsWith(Backup.getBasename(AUTO_BACKUP_FILENAME)) }?.forEach { file ->
            try {
                cleanupAutomatedBackupFile(file)
            } catch (e: Exception) {
                // ignore, will be retried on next cleanup
            }
        }
    }

    private fun cleanupAutomatedBackupFile(file: File) {
        if (!file.isFile) {
            return
        }

        val lastAccessTime = file.lastModified()
        val isTTLReached =
            System.currentTimeMillis() - lastAccessTime >=
                serverConfig.backupTTL.value.days
                    .coerceAtLeast(1.days)
                    .inWholeMilliseconds
        if (isTTLReached) {
            file.delete()
        }
    }

    fun createBackup(flags: BackupFlags): InputStream {
        // Create root object

        val databaseManga = transaction { MangaTable.selectAll().where { MangaTable.inLibrary eq true } }

        val backup: Backup =
            transaction {
                Backup(
                    backupManga(databaseManga, flags),
                    backupCategories(flags),
                    backupExtensionInfo(databaseManga, flags),
                    backupGlobalMeta(flags),
                    backupServerSettings(flags),
                )
            }

        val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)

        val byteStream = Buffer()
        (byteStream as Sink)
            .gzip()
            .buffer()
            .use { it.write(byteArray) }

        return byteStream.inputStream()
    }

    private fun backupManga(
        databaseManga: Query,
        flags: BackupFlags,
    ): List<BackupManga> =
        databaseManga.map { mangaRow ->
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
                            .map {
                                ChapterTable.toDataClass(it)
                            }
                    }
                if (flags.includeChapters) {
                    val chapterToMeta = Chapter.getChaptersMetaMaps(chapters.map { it.id })

                    backupManga.chapters =
                        chapters.map {
                            BackupChapter(
                                it.url,
                                it.name,
                                it.scanlator,
                                it.read,
                                it.bookmarked,
                                it.lastPageRead,
                                it.fetchedAt.seconds.inWholeMilliseconds,
                                it.uploadDate,
                                it.chapterNumber,
                                chapters.size - it.index,
                            ).apply {
                                if (flags.includeClientData) {
                                    this.meta = chapterToMeta[it.id] ?: emptyMap()
                                }
                            }
                        }
                }
                if (flags.includeHistory) {
                    backupManga.history =
                        chapters.mapNotNull {
                            if (it.lastReadAt > 0) {
                                BackupHistory(
                                    url = it.url,
                                    lastRead = it.lastReadAt.seconds.inWholeMilliseconds,
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
                    Track.getTrackRecordsByMangaId(mangaRow[MangaTable.id].value).mapNotNull {
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

//            if (flags.includeHistory) {
//                backupManga.history = TODO()
//            }

            backupManga
        }

    private fun backupCategories(flags: BackupFlags): List<BackupCategory> {
        val categories =
            CategoryTable
                .selectAll()
                .orderBy(CategoryTable.order to SortOrder.ASC)
                .map { CategoryTable.toDataClass(it) }
        val categoryToMeta = Category.getCategoriesMetaMaps(categories.map { it.id })

        return categories.map {
            BackupCategory(
                it.name,
                it.order,
                0, // not supported in Tachidesk
            ).apply {
                if (flags.includeClientData) {
                    this.meta = categoryToMeta[it.id] ?: emptyMap()
                }
            }
        }
    }

    private fun backupExtensionInfo(
        mangas: Query,
        flags: BackupFlags,
    ): List<BackupSource> {
        val inLibraryMangaSourceIds =
            mangas
                .asSequence()
                .map { it[MangaTable.sourceReference] }
                .distinct()
                .toList()
        val sources = SourceTable.selectAll().where { SourceTable.id inList inLibraryMangaSourceIds }
        val sourceToMeta = Source.getSourcesMetaMaps(sources.map { it[SourceTable.id].value })

        return inLibraryMangaSourceIds
            .map { mangaSourceId ->
                val source = sources.firstOrNull { it[SourceTable.id].value == mangaSourceId }
                BackupSource(
                    source?.get(SourceTable.name) ?: "",
                    mangaSourceId,
                ).apply {
                    if (flags.includeClientData) {
                        this.meta = sourceToMeta[mangaSourceId] ?: emptyMap()
                    }
                }
            }.toList()
    }

    private fun backupGlobalMeta(flags: BackupFlags): Map<String, String> {
        if (!flags.includeClientData) {
            return emptyMap()
        }

        return GlobalMeta.getMetaMap()
    }

    private fun backupServerSettings(flags: BackupFlags): BackupServerSettings? {
        if (!flags.includeServerSettings) {
            return null
        }

        return BackupServerSettings(
            ip = serverConfig.ip.value,
            port = serverConfig.port.value,
            // socks
            socksProxyEnabled = serverConfig.socksProxyEnabled.value,
            socksProxyVersion = serverConfig.socksProxyVersion.value,
            socksProxyHost = serverConfig.socksProxyHost.value,
            socksProxyPort = serverConfig.socksProxyPort.value,
            socksProxyUsername = serverConfig.socksProxyUsername.value,
            socksProxyPassword = serverConfig.socksProxyPassword.value,
            // webUI
            webUIFlavor = serverConfig.webUIFlavor.value,
            initialOpenInBrowserEnabled = serverConfig.initialOpenInBrowserEnabled.value,
            webUIInterface = serverConfig.webUIInterface.value,
            electronPath = serverConfig.electronPath.value,
            webUIChannel = serverConfig.webUIChannel.value,
            webUIUpdateCheckInterval = serverConfig.webUIUpdateCheckInterval.value,
            // downloader
            downloadAsCbz = serverConfig.downloadAsCbz.value,
            downloadsPath = serverConfig.downloadsPath.value,
            autoDownloadNewChapters = serverConfig.autoDownloadNewChapters.value,
            excludeEntryWithUnreadChapters = serverConfig.excludeEntryWithUnreadChapters.value,
            autoDownloadAheadLimit = 0, // deprecated
            autoDownloadNewChaptersLimit = serverConfig.autoDownloadNewChaptersLimit.value,
            autoDownloadIgnoreReUploads = serverConfig.autoDownloadIgnoreReUploads.value,
            // extension
            extensionRepos = serverConfig.extensionRepos.value,
            // requests
            maxSourcesInParallel = serverConfig.maxSourcesInParallel.value,
            // updater
            excludeUnreadChapters = serverConfig.excludeUnreadChapters.value,
            excludeNotStarted = serverConfig.excludeNotStarted.value,
            excludeCompleted = serverConfig.excludeCompleted.value,
            globalUpdateInterval = serverConfig.globalUpdateInterval.value,
            updateMangas = serverConfig.updateMangas.value,
            // Authentication
            basicAuthEnabled = serverConfig.basicAuthEnabled.value,
            basicAuthUsername = serverConfig.basicAuthUsername.value,
            basicAuthPassword = serverConfig.basicAuthPassword.value,
            // misc
            debugLogsEnabled = serverConfig.debugLogsEnabled.value,
            gqlDebugLogsEnabled = false, // deprecated
            systemTrayEnabled = serverConfig.systemTrayEnabled.value,
            maxLogFiles = serverConfig.maxLogFiles.value,
            maxLogFileSize = serverConfig.maxLogFileSize.value,
            maxLogFolderSize = serverConfig.maxLogFolderSize.value,
            // backup
            backupPath = serverConfig.backupPath.value,
            backupTime = serverConfig.backupTime.value,
            backupInterval = serverConfig.backupInterval.value,
            backupTTL = serverConfig.backupTTL.value,
            // local source
            localSourcePath = serverConfig.localSourcePath.value,
            // cloudflare bypass
            flareSolverrEnabled = serverConfig.flareSolverrEnabled.value,
            flareSolverrUrl = serverConfig.flareSolverrUrl.value,
            flareSolverrTimeout = serverConfig.flareSolverrTimeout.value,
            flareSolverrSessionName = serverConfig.flareSolverrSessionName.value,
            flareSolverrSessionTtl = serverConfig.flareSolverrSessionTtl.value,
            flareSolverrAsResponseFallback = serverConfig.flareSolverrAsResponseFallback.value,
            // opds
            opdsUseBinaryFileSizes = serverConfig.opdsUseBinaryFileSizes.value,
            opdsItemsPerPage = serverConfig.opdsItemsPerPage.value,
            opdsEnablePageReadProgress = serverConfig.opdsEnablePageReadProgress.value,
            opdsMarkAsReadOnDownload = serverConfig.opdsMarkAsReadOnDownload.value,
            opdsShowOnlyUnreadChapters = serverConfig.opdsShowOnlyUnreadChapters.value,
            opdsShowOnlyDownloadedChapters = serverConfig.opdsShowOnlyDownloadedChapters.value,
            opdsChapterSortOrder = serverConfig.opdsChapterSortOrder.value,
        )
    }
}
