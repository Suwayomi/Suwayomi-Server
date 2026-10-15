package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.graphql.types.toStatus
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator.ValidationResult
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator.validate
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupCategoryHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupGlobalMetaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupSettingsHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupSourceHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupUserSettingsHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.hasPermission
import java.io.InputStream
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

object ProtoBackupImport : ProtoBackupBase() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val logger = KotlinLogging.logger {}

    private val backupMutex = Mutex()

    sealed class BackupRestoreState {
        data object Idle : BackupRestoreState()

        data object Success : BackupRestoreState()

        /**
         * The restore finished but at least one manga could not be fully restored (the
         * failures are listed in [errors]). Only emitted for file restores: SyncManager's
         * terminal-state check predates this state and treats every non-Success terminal
         * as a failure, so sync-driven restores keep reporting plain [Success] — their
         * per-manga errors are already logged and returned in the ValidationResult.
         */
        data class SuccessWithErrors(
            val errors: List<String>,
        ) : BackupRestoreState()

        data object Failure : BackupRestoreState()

        data class RestoringCategories(
            val current: Int,
            val totalManga: Int,
        ) : BackupRestoreState()

        data class RestoringMeta(
            val current: Int,
            val totalManga: Int,
        ) : BackupRestoreState()

        data class RestoringSettings(
            val current: Int,
            val totalManga: Int,
        ) : BackupRestoreState()

        data class RestoringUserSettings(
            val current: Int,
            val totalManga: Int,
        ) : BackupRestoreState()

        data class RestoringManga(
            val current: Int,
            val totalManga: Int,
            val title: String,
        ) : BackupRestoreState()
    }

    // restoreId -> owning userId + state; a restore status is only visible to its owner
    private val backupRestoreIdToState = ConcurrentHashMap<String, Pair<Int, BackupRestoreState>>()

    val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    internal fun getRestoreState(id: String): BackupRestoreState? = backupRestoreIdToState[id]?.second

    /**
     * Returns the state only when [userId] owns the restore identified by [id], so one
     * user cannot observe another user's restore.
     */
    fun getRestoreState(
        userId: Int,
        id: String,
    ): BackupRestoreState? = backupRestoreIdToState[id]?.takeIf { it.first == userId }?.second

    private fun updateRestoreState(
        userId: Int,
        id: String,
        state: BackupRestoreState,
    ) {
        backupRestoreIdToState[id] = userId to state

        scope.launch {
            notifyFlow.emit(Unit)
        }
    }

    private fun cleanupRestoreState(id: String) {
        // defer the eviction to the existing scope instead of a bare Timer(): Timer spawns
        // a non-daemon thread that is never cancelled, so every restore used to leak one
        scope.launch {
            delay(60_000)
            logger.debug { "cleanupRestoreState: $id (${getRestoreState(id)?.toStatus()?.state})" }
            backupRestoreIdToState.remove(id)
        }
    }

    fun restore(
        userId: Int,
        sourceStream: InputStream,
        flags: BackupFlags,
        syncMode: SyncRestoreMode = SyncRestoreMode.NONE,
    ): String {
        // materialize on the caller thread: for REST/GraphQL uploads the stream belongs to
        // the request lifecycle (e.g. a Javalin UploadedFile part) and is invalidated once
        // the request returns, before the queued coroutine would get to read it
        val backupBytes = readBackupBytes(sourceStream, gzipped = !syncMode.isSync)
        return queueRestore(userId, flags, syncMode) { decode(backupBytes) }
    }

    fun restore(
        userId: Int,
        backup: Backup,
        flags: BackupFlags,
        syncMode: SyncRestoreMode = SyncRestoreMode.NONE,
    ): String = queueRestore(userId, flags, syncMode) { backup }

    @OptIn(DelicateCoroutinesApi::class)
    private fun queueRestore(
        userId: Int,
        flags: BackupFlags,
        syncMode: SyncRestoreMode,
        load: () -> Backup,
    ): String {
        // a UUID instead of a timestamp: not enumerable and unique even for queued restores
        val restoreId = UUID.randomUUID().toString()

        logger.info { "restore($restoreId): queued" }

        updateRestoreState(userId, restoreId, BackupRestoreState.Idle)

        GlobalScope.launch {
            runRestore(userId, restoreId, flags, syncMode, load)
        }

        return restoreId
    }

    suspend fun restoreLegacy(
        userId: Int,
        sourceStream: InputStream,
        restoreId: String = UUID.randomUUID().toString(),
        flags: BackupFlags = BackupFlags.DEFAULT,
        syncMode: SyncRestoreMode = SyncRestoreMode.NONE,
    ): ValidationResult = runRestore(userId, restoreId, flags, syncMode) { decode(sourceStream, syncMode) }

    private suspend fun runRestore(
        userId: Int,
        restoreId: String,
        flags: BackupFlags,
        syncMode: SyncRestoreMode,
        load: () -> Backup,
    ): ValidationResult =
        backupMutex.withLock {
            try {
                logger.info { "restore($restoreId): restoring..." }
                performRestore(userId, restoreId, load(), flags, syncMode)
            } catch (e: Throwable) {
                // a cancelled restore propagates; the caller's scope owns the cancellation
                if (e is CancellationException) {
                    throw e
                }
                // any Throwable (including Errors like OutOfMemoryError) must still settle
                // the state map, otherwise waiters would observe a non-terminal state forever
                logger.error(e) { "restore($restoreId): failed due to" }

                updateRestoreState(userId, restoreId, BackupRestoreState.Failure)
                ValidationResult(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            } finally {
                if (syncMode.isSync) {
                    clearSyncingFlags(userId)
                }
                logger.info { "restore($restoreId): finished with state ${getRestoreState(restoreId)?.toStatus()?.state}" }
                cleanupRestoreState(restoreId)
            }
        }

    private fun clearSyncingFlags(userId: Int) {
        transaction {
            MangaUserTable.update(
                {
                    MangaUserTable.isSyncing eq true and (MangaUserTable.user eq userId)
                },
            ) {
                it[MangaUserTable.isSyncing] = false
            }
            ChapterUserTable.update(
                {
                    ChapterUserTable.isSyncing eq true and (ChapterUserTable.user eq userId)
                },
            ) {
                it[ChapterUserTable.isSyncing] = false
            }
            CategoryTable.update(
                {
                    CategoryTable.isSyncing eq true and (CategoryTable.user eq userId)
                },
            ) {
                it[CategoryTable.isSyncing] = false
            }
        }
    }

    private fun decode(
        sourceStream: InputStream,
        syncMode: SyncRestoreMode,
    ): Backup = decode(readBackupBytes(sourceStream, gzipped = !syncMode.isSync))

    private fun decode(bytes: ByteArray): Backup = parser.decodeFromByteArray(Backup.serializer(), bytes)

    private fun performRestore(
        userId: Int,
        id: String,
        backup: Backup,
        flags: BackupFlags,
        syncMode: SyncRestoreMode,
    ): ValidationResult {
        val validationResult = validate(userId, backup)

        // only users with the MANAGE_SETTINGS permission can change the global server settings
        val canManageSettings = hasPermission(userId, UserPermission.MANAGE_SETTINGS)

        val restoreCategories = if (flags.includeCategories) 1 else 0
        val restoreMeta = if (flags.includeClientData) 1 else 0
        val restoreSettings = if (flags.includeServerSettings && canManageSettings) 1 else 0
        val restoreUserSettings = if (flags.includeUserSettings) 1 else 0
        val getRestoreAmount = { size: Int -> size + restoreCategories + restoreMeta + restoreSettings + restoreUserSettings }
        val restoreAmount = getRestoreAmount(if (flags.includeManga) backup.backupManga.size else 0)

        if (flags.includeServerSettings && canManageSettings) {
            updateRestoreState(
                userId,
                id,
                BackupRestoreState.RestoringSettings(restoreSettings, restoreAmount),
            )

            BackupSettingsHandler.restore(backup.serverSettings)
        }

        if (flags.includeUserSettings) {
            updateRestoreState(
                userId,
                id,
                BackupRestoreState.RestoringUserSettings(restoreSettings + restoreUserSettings, restoreAmount),
            )

            BackupUserSettingsHandler.restore(
                userId,
                backup.userSettings,
                backup.serverSettings,
            )
        }

        val categoryMapping =
            if (flags.includeCategories) {
                updateRestoreState(
                    userId,
                    id,
                    BackupRestoreState.RestoringCategories(
                        restoreSettings + restoreUserSettings + restoreCategories,
                        restoreAmount,
                    ),
                )
                BackupCategoryHandler.restore(userId, backup.backupCategories, syncMode)
            } else {
                emptyMap()
            }

        if (flags.includeClientData) {
            updateRestoreState(
                userId,
                id,
                BackupRestoreState.RestoringMeta(
                    restoreSettings + restoreUserSettings + restoreCategories + restoreMeta,
                    restoreAmount,
                ),
            )

            BackupGlobalMetaHandler.restore(userId, backup.meta)

            BackupSourceHandler.restore(userId, backup.backupSources)
        }

        // Store source mapping for error messages
        val sourceMapping = backup.getSourceMap()

        val errors = mutableListOf<Pair<Date, String>>()

        // Restore individual manga
        if (flags.includeManga) {
            backup.backupManga.forEachIndexed { index, manga ->
                updateRestoreState(
                    userId,
                    id,
                    BackupRestoreState.RestoringManga(
                        current = getRestoreAmount(index + 1),
                        totalManga = restoreAmount,
                        title = manga.title,
                    ),
                )

                BackupMangaHandler.restore(
                    userId,
                    backupManga = manga,
                    categoryMapping = categoryMapping,
                    sourceMapping = sourceMapping,
                    errors = errors,
                    flags = flags,
                    syncMode = syncMode,
                )
            }
        }

        logger.warn {
            """
            Restore Errors:
            ${errors.joinToString("\n") { "${it.first} - ${it.second}" }}
            Restore Summary:
            - Missing Sources:
                ${validationResult.missingSources.joinToString("\n                    ")}
            - Titles missing Sources:
                ${validationResult.mangasMissingSources.joinToString("\n                    ")}
            - Missing Trackers:
                ${validationResult.missingTrackers.joinToString("\n                    ")}
            """.trimIndent()
        }

        // a restore that finished but with per-manga failures stays a successful restore —
        // its errors are made visible instead of failing the whole operation. Sync restores
        // keep the plain Success terminal state (see SuccessWithErrors).
        updateRestoreState(
            userId,
            id,
            if (!syncMode.isSync && errors.isNotEmpty()) {
                BackupRestoreState.SuccessWithErrors(errors.map { it.second })
            } else {
                BackupRestoreState.Success
            },
        )

        return validationResult.copy(errors = errors.map { "${it.first} - ${it.second}" })
    }
}
