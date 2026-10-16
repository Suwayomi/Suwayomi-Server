package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupCategoryHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupGlobalMetaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupSettingsHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupSourceHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupUserSettingsHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPOutputStream
import kotlin.time.Duration.Companion.days

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
                if (createAutomatedBackup()) {
                    preferences.edit().putLong(LAST_AUTOMATED_BACKUP_KEY, System.currentTimeMillis()).apply()
                }
            } catch (e: Exception) {
                logger.error(e) { "scheduleAutomatedBackupTask: failed due to" }
            }
        }

        val (backupHour, backupMinute) =
            serverConfig.backupTime.value
                .split(":")
                .map { it.toInt() }
        // the interval lands in a "*/n" day-of-month cron field; a persisted value that
        // predates the setting's max=31 (or an out-of-range DB write) must not produce an
        // invalid schedule — clamp to [1, 31] here as well (0 already returned above)
        val backupInterval =
            serverConfig.backupInterval.value
                .coerceIn(1, 31)
                .days

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

    /**
     * Creates one backup file per user. Returns true if at least one backup succeeded.
     */
    internal fun createAutomatedBackup(): Boolean {
        logger.info { "Creating automated backup..." }

        val users =
            transaction {
                UserAccountTable
                    .select(UserAccountTable.id, UserAccountTable.username)
                    .map { it[UserAccountTable.id].value to it[UserAccountTable.username] }
            }
        if (users.isEmpty()) {
            logger.warn { "No users found; skipping automated backup" }
            return false
        }

        val automatedBackupDir = File(applicationDirs.automatedBackupRoot)
        automatedBackupDir.mkdirs()

        var anySucceeded = false
        users.forEach { (userId, username) ->
            // the userId keeps names unique across users whose usernames sanitize to the
            // same string ("a/b" and "a?b" both become "a_b"); the tmp+rename dance makes
            // sure a failed/crashed backup never leaves a truncated .tachibk behind
            val backupFile =
                File(automatedBackupDir, Backup.getFilename("$AUTO_BACKUP_FILENAME.$userId.$username"))
            val tmpFile = File(automatedBackupDir, "${backupFile.name}.tmp")
            try {
                createBackup(userId, BackupFlags.fromServerConfig()).use { input ->
                    tmpFile.outputStream().use { output -> input.copyTo(output) }
                }
                try {
                    Files.move(tmpFile.toPath(), backupFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmpFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                logger.info { "Automated backup for user $userId complete" }
                anySucceeded = true
            } catch (e: Exception) {
                tmpFile.delete()
                logger.error(e) { "Automated backup for user $userId failed" }
            }
        }
        return anySucceeded
    }

    internal fun cleanupAutomatedBackups() {
        logger.debug { "Cleanup automated backups (ttl= ${serverConfig.backupTTL.value})" }

        val isCleanupDisabled = serverConfig.backupTTL.value == 0
        if (isCleanupDisabled) {
            return
        }

        val automatedBackupDir = File(applicationDirs.automatedBackupRoot)
        if (!automatedBackupDir.isDirectory) {
            return
        }

        // names are "org.suwayomi.tachidesk.auto.<userId>.<username>_…​.tachibk": match on
        // the exact "…​.auto." prefix so a manual backup merely *starting* with "auto"
        // cannot be TTL-deleted
        val autoBackupPrefix = "${Backup.getBasename()}.$AUTO_BACKUP_FILENAME."
        automatedBackupDir.listFiles { file -> file.name.startsWith(autoBackupPrefix) }?.forEach { file ->
            try {
                cleanupAutomatedBackupFile(file)
            } catch (_: Exception) {
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

    fun createBackup(
        userId: Int,
        flags: BackupFlags,
    ): InputStream {
        // Create root object
        val backup: Backup =
            transaction {
                val backupMangas = BackupMangaHandler.backup(userId, flags)
                Backup(
                    backupMangas,
                    BackupCategoryHandler.backup(userId, flags),
                    BackupSourceHandler.backup(userId, backupMangas, flags),
                    BackupGlobalMetaHandler.backup(userId, flags),
                    BackupSettingsHandler.backup(flags),
                    BackupUserSettingsHandler.backup(flags, userId),
                )
            }

        val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)

        // kotlinx-serialization has no streaming proto encoder, so the serialized
        // bytes must fully reside in memory anyway — but they are only retained in
        // their COMPRESSED form: write them through GZIPOutputStream directly instead
        // of keeping an uncompressed copy inside an okio Buffer on top of the encoded
        // array (proto→gzip compresses ~5-10×, so the previous shape effectively
        // doubled peak memory for large libraries).
        val compressed =
            ByteArrayOutputStream()
                .also { out -> GZIPOutputStream(out).use { gzip -> gzip.write(byteArray) } }
                .toByteArray()

        return compressed.inputStream()
    }
}
