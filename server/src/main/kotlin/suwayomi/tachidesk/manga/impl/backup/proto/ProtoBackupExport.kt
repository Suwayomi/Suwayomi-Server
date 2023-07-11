package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import mu.KotlinLogging
import okio.buffer
import okio.gzip
import okio.sink
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSerializer
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSource
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlin.time.Duration.Companion.days

object ProtoBackupExport : ProtoBackupBase() {
    private val logger = KotlinLogging.logger { }
    private val applicationDirs by DI.global.instance<ApplicationDirs>()
    private const val lastAutomatedBackupKey = "lastAutomatedBackupKey"
    private val preferences = Preferences.userNodeForPackage(ProtoBackupExport::class.java)

    private val backupTimer = Timer()
    private var currentAutomatedBackupTask: TimerTask? = null

    fun scheduleAutomatedBackupTask() {
        if (!serverConfig.automatedBackups) {
            currentAutomatedBackupTask?.cancel()
            return
        }

        val minInterval = 1.days
        val interval = serverConfig.backupInterval.days
        val backupInterval = interval.coerceAtLeast(minInterval).inWholeMilliseconds

        val lastAutomatedBackup = preferences.getLong(lastAutomatedBackupKey, 0)
        val initialDelay =
            backupInterval - (System.currentTimeMillis() - lastAutomatedBackup) % backupInterval

        currentAutomatedBackupTask?.cancel()
        currentAutomatedBackupTask = object : TimerTask() {
            override fun run() {
                cleanupAutomatedBackups()
                createAutomatedBackup()
                preferences.putLong(lastAutomatedBackupKey, System.currentTimeMillis())
            }
        }

        backupTimer.scheduleAtFixedRate(
            currentAutomatedBackupTask,
            initialDelay,
            backupInterval
        )
    }

    private fun createAutomatedBackup() {
        logger.info { "Creating automated backup..." }

        createBackup(
            BackupFlags(
                includeManga = true,
                includeCategories = true,
                includeChapters = true,
                includeTracking = true,
                includeHistory = true
            )
        ).use { input ->
            val automatedBackupDir = File(applicationDirs.automatedBackupRoot)
            automatedBackupDir.mkdirs()

            val backupFile = File(applicationDirs.automatedBackupRoot, getBackupFilename())

            backupFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun cleanupAutomatedBackups() {
        logger.debug { "Cleanup automated backups (ttl= ${serverConfig.backupTTL})" }

        val isCleanupDisabled = serverConfig.backupTTL == 0
        if (isCleanupDisabled) {
            return
        }

        val automatedBackupDir = File(applicationDirs.automatedBackupRoot)
        if (!automatedBackupDir.isDirectory) {
            return
        }

        automatedBackupDir.walkTopDown().forEach { file ->
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
            System.currentTimeMillis() - lastAccessTime >= serverConfig.backupTTL.days.inWholeMilliseconds
        if (isTTLReached) {
            file.delete()
        }
    }

    fun getBackupFilename(): String {
        val currentDate = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date())
        return "tachidesk_$currentDate.proto.gz"
    }

    fun createBackup(flags: BackupFlags): InputStream {
        // Create root object

        val databaseManga = transaction { MangaTable.select { MangaTable.inLibrary eq true } }

        val backup: Backup = transaction {
            Backup(
                backupManga(databaseManga, flags),
                backupCategories(),
                emptyList(),
                backupExtensionInfo(databaseManga)
            )
        }

        val byteArray = parser.encodeToByteArray(BackupSerializer, backup)

        val byteStream = ByteArrayOutputStream()
        byteStream.sink().gzip().buffer().use { it.write(byteArray) }

        return byteStream.toByteArray().inputStream()
    }

    private fun backupManga(databaseManga: Query, flags: BackupFlags): List<BackupManga> {
        return databaseManga.map { mangaRow ->
            val backupManga = BackupManga(
                source = mangaRow[MangaTable.sourceReference],
                url = mangaRow[MangaTable.url],
                title = mangaRow[MangaTable.title],
                artist = mangaRow[MangaTable.artist],
                author = mangaRow[MangaTable.author],
                description = mangaRow[MangaTable.description],
                genre = mangaRow[MangaTable.genre]?.split(", ") ?: emptyList(),
                status = MangaStatus.valueOf(mangaRow[MangaTable.status]).value,
                thumbnailUrl = mangaRow[MangaTable.thumbnail_url],
                dateAdded = TimeUnit.SECONDS.toMillis(mangaRow[MangaTable.inLibraryAt]),
                viewer = 0, // not supported in Tachidesk
                updateStrategy = UpdateStrategy.valueOf(mangaRow[MangaTable.updateStrategy])
            )

            val mangaId = mangaRow[MangaTable.id].value

            if (flags.includeChapters) {
                val chapters = transaction {
                    ChapterTable.select { ChapterTable.manga eq mangaId }
                        .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                        .map {
                            ChapterTable.toDataClass(it)
                        }
                }

                backupManga.chapters = chapters.map {
                    BackupChapter(
                        it.url,
                        it.name,
                        it.scanlator,
                        it.read,
                        it.bookmarked,
                        it.lastPageRead,
                        TimeUnit.SECONDS.toMillis(it.fetchedAt),
                        it.uploadDate,
                        it.chapterNumber,
                        chapters.size - it.index
                    )
                }
            }

            if (flags.includeCategories) {
                backupManga.categories = CategoryManga.getMangaCategories(mangaId).map { it.order }
            }

//            if(flags.includeTracking) {
//                backupManga.tracking = TODO()
//            }

//            if (flags.includeHistory) {
//                backupManga.history = TODO()
//            }

            backupManga
        }
    }

    private fun backupCategories(): List<BackupCategory> {
        return CategoryTable.selectAll().orderBy(CategoryTable.order to SortOrder.ASC).map {
            CategoryTable.toDataClass(it)
        }.map {
            BackupCategory(
                it.name,
                it.order,
                0 // not supported in Tachidesk
            )
        }
    }

    private fun backupExtensionInfo(mangas: Query): List<BackupSource> {
        return mangas
            .asSequence()
            .map { it[MangaTable.sourceReference] }
            .distinct()
            .map {
                val sourceRow = SourceTable.select { SourceTable.id eq it }.firstOrNull()
                BackupSource(
                    sourceRow?.get(SourceTable.name) ?: "",
                    it
                )
            }
            .toList()
    }
}
