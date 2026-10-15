package suwayomi.tachidesk.global.impl.sync

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.StartSyncResult
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSource
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for the per-user [SyncManager].
 *
 * Each user has their own sync account and enablement. These tests verify the per-user enablement
 * and the per-user sync state without exercising the network (the disabled paths return before any
 * HTTP call).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncManagerTest : ApplicationTest() {
    private var userId: Int = 0
    private var userId2: Int = 0

    @BeforeEach
    fun setUp() {
        userId = createUser("syncmgr_a")
        userId2 = createUser("syncmgr_b")
    }

    @AfterEach
    fun tearDown() {
        userSettings.resetAll(userId)
        userSettings.resetAll(userId2)
        transaction {
            UserAccountTable.deleteWhere { (UserAccountTable.id eq userId) or (UserAccountTable.id eq userId2) }
        }
    }

    private fun createUser(username: String): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = username
                    it[UserAccountTable.password] = "password"
                }.value
        }

    // The same "sync" preferences that [SyncManager] uses for its per-user sync timestamps
    private val syncPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)
    }

    private fun awaitScheduledSyncTask(
        userId: Int,
        timeoutMs: Long = 5_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (SyncManager.scheduledSyncTasks[userId] == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    @Test
    fun startSyncReturnsDisabledWhenSyncDisabled() {
        // Both users fall back to the (disabled) global value
        assertEquals(StartSyncResult.SYNC_DISABLED, SyncManager.startSync(userId))
        assertEquals(StartSyncResult.SYNC_DISABLED, SyncManager.startSync(userId2))
    }

    @Test
    fun startSyncForEnabledUserReturnsSuccessAndReachesTerminalState() =
        runTest {
            // Mock the network layer so no real HTTP call is made; a thrown exception
            // simulates a sync failure, which drives the state machine to Error.
            mockkObject(SyncYomiSyncService)
            try {
                coEvery { SyncYomiSyncService.doSync(any(), any(), any(), any(), any()) } throws
                    Exception("Network error")

                userSettings.set(userId, userConfig.syncYomiEnabled, true)

                // an enabled user: startSync returns SUCCESS (not SYNC_DISABLED)
                assertEquals(StartSyncResult.SUCCESS, SyncManager.startSync(userId))

                // wait for the background sync to reach a terminal state
                val deadline = System.currentTimeMillis() + 5_000
                while (
                    SyncManager.lastSyncState(userId).value !is SyncManager.SyncState.Error &&
                    SyncManager.lastSyncState(userId).value !is SyncManager.SyncState.Success &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(50)
                }

                // the (mocked) network failure produced a terminal Error state
                val state = SyncManager.lastSyncState(userId).value
                assertIs<SyncManager.SyncState.Error>(state)
                assertTrue(state.message.endsWith("Network error"), "unexpected error message: ${state.message}")
            } finally {
                // an object mock poisons every later test in the fork when an assertion
                // above fails — release it unconditionally
                unmockkObject(SyncYomiSyncService)
            }
        }

    /**
     * U2-E: local category order values are 1-based (the default category holds 0) while
     * the wire carries 0-based ranks, so `isMangaDifferent` must rebase before comparing —
     * otherwise a manga whose categories match the remote is flagged different on every sync.
     */
    @Test
    fun isMangaDifferentRebasesLocalCategoryOrders() {
        val mangaRowId =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[MangaTable.url] = "/u2e/manga"
                        it[MangaTable.title] = "u2e"
                        it[MangaTable.sourceReference] = 0
                    }.value
            }
        val categoryRowId =
            transaction {
                CategoryTable
                    .insertAndGetId {
                        it[CategoryTable.name] = "Reading-u2e"
                        it[CategoryTable.user] = userId
                        it[CategoryTable.order] = 1
                    }.value
            }
        try {
            transaction {
                // link first: the insert trigger bumps MangaUserTable.version, so the
                // user row must be created afterwards to keep its expected version
                CategoryMangaTable.insert {
                    it[CategoryMangaTable.category] = categoryRowId
                    it[CategoryMangaTable.manga] = mangaRowId
                    it[CategoryMangaTable.user] = userId
                }
                MangaUserTable.insertAndGetId {
                    it[MangaUserTable.manga] = mangaRowId
                    it[MangaUserTable.user] = userId
                    it[MangaUserTable.inLibrary] = true
                    it[MangaUserTable.version] = 5
                }
            }

            val localManga =
                transaction {
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaRowId }
                        .map { MangaTable.toDataClass(it) }
                        .single()
                }
            // local order 1 (a user category) is wire rank 0
            val remoteManga =
                BackupManga(source = 0, url = "/u2e/manga", version = 5, categories = listOf(0))

            assertFalse(SyncManager.isMangaDifferent(userId, localManga, remoteManga))
            // sanity check: a genuinely different remote category set is still detected
            assertTrue(SyncManager.isMangaDifferent(userId, localManga, remoteManga.copy(categories = listOf(1))))
        } finally {
            transaction {
                CategoryMangaTable.deleteWhere {
                    (CategoryMangaTable.manga eq mangaRowId) and (CategoryMangaTable.user eq userId)
                }
                MangaUserTable.deleteWhere {
                    (MangaUserTable.manga eq mangaRowId) and (MangaUserTable.user eq userId)
                }
                CategoryTable.deleteWhere { CategoryTable.id eq categoryRowId }
                MangaTable.deleteWhere { MangaTable.id eq mangaRowId }
            }
        }
    }

    /**
     * U2-G: a remote change that only unfavorites a manga still runs the restore path.
     * updateNonFavorites writes the row with is_syncing set, and only the restore's
     * cleanup clears it — an early "nothing changed" exit would leave the flag stuck and
     * swallow later user edits (the version triggers skip syncing rows).
     */
    @Test
    fun nonFavoriteOnlyChangeStillRunsRestore() =
        runTest {
            val (sourceId, sourceName) =
                transaction {
                    SourceTable
                        .selectAll()
                        .limit(1)
                        .single()
                        .let { it[SourceTable.id].value to it[SourceTable.name] }
                }
            val mangaRowId =
                transaction {
                    MangaTable
                        .insertAndGetId {
                            it[MangaTable.url] = "/u2g/manga"
                            it[MangaTable.title] = "u2g"
                            it[MangaTable.sourceReference] = sourceId
                        }.value
                }

            try {
                transaction {
                    MangaUserTable.insertAndGetId {
                        it[MangaUserTable.manga] = mangaRowId
                        it[MangaUserTable.user] = userId
                        it[MangaUserTable.inLibrary] = true
                    }
                }

                // the remote reports the same manga as a non-favorite and nothing else
                val remote =
                    Backup(
                        backupManga =
                            listOf(
                                BackupManga(
                                    source = sourceId,
                                    url = "/u2g/manga",
                                    favorite = false,
                                    lastModifiedAt = 1,
                                ),
                            ),
                        backupCategories = emptyList(),
                        backupSources = listOf(BackupSource(name = sourceName, sourceId = sourceId)),
                    )

                mockkObject(SyncYomiSyncService)
                coEvery { SyncYomiSyncService.doSync(any(), any(), any(), any(), any()) } returns
                    SyncYomiSyncService.SyncResult(remote, changed = true, protocolV2 = true)

                userSettings.set(userId, userConfig.syncYomiEnabled, true)
                assertEquals(StartSyncResult.SUCCESS, SyncManager.startSync(userId))

                // the restore runs for real, so allow it more time than a pure mock path
                val deadline = System.currentTimeMillis() + 60_000
                while (
                    SyncManager.lastSyncState(userId).value !is SyncManager.SyncState.Error &&
                    SyncManager.lastSyncState(userId).value !is SyncManager.SyncState.Success &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(100)
                }

                val state = SyncManager.lastSyncState(userId).value
                assertIs<SyncManager.SyncState.Success>(state)

                val (inLibrary, isSyncing) =
                    transaction {
                        MangaUserTable
                            .select(MangaUserTable.inLibrary, MangaUserTable.isSyncing)
                            .where {
                                (MangaUserTable.manga eq mangaRowId) and (MangaUserTable.user eq userId)
                            }.single()
                            .let { it[MangaUserTable.inLibrary] to it[MangaUserTable.isSyncing] }
                    }
                // the remote unfavorite was mirrored locally ...
                assertFalse(inLibrary)
                // ... and the restore's cleanup cleared the flag instead of leaving it stuck
                assertFalse(isSyncing)
            } finally {
                unmockkObject(SyncYomiSyncService)
                transaction {
                    MangaUserTable.deleteWhere {
                        (MangaUserTable.manga eq mangaRowId) and (MangaUserTable.user eq userId)
                    }
                    MangaTable.deleteWhere { MangaTable.id eq mangaRowId }
                }
            }
        }

    @Test
    fun syncEnabledPerUserIsolation() {
        // Enable sync for user A only (a per-user override); user B keeps the global (disabled) fallback
        userSettings.set(userId, userConfig.syncYomiEnabled, true)

        assertEquals(true, userConfig.syncYomiEnabled.value(userId))
        assertEquals(false, userConfig.syncYomiEnabled.value(userId2))

        // User B's sync is still disabled
        assertEquals(StartSyncResult.SYNC_DISABLED, SyncManager.startSync(userId2))
    }

    @Test
    fun lastSyncStateIsPerUser() {
        // Each user has an independent sync state flow
        SyncManager.lastSyncState(userId)
        SyncManager.lastSyncState(userId2)
        assertNotSame(SyncManager.syncStates.getValue(userId), SyncManager.syncStates.getValue(userId2))
        // Both start out with no sync state
        assertEquals(null, SyncManager.lastSyncState(userId).value)
        assertEquals(null, SyncManager.lastSyncState(userId2).value)
    }

    @Test
    fun deletedUserSyncStateIsCleanedUp() {
        // Give user A a sync state flow, an enabled sync config, and a scheduled sync task
        SyncManager.lastSyncState(userId)
        SyncManager.lastSyncState(userId2)
        val stateA = SyncManager.syncStates.getValue(userId)
        val stateB = SyncManager.syncStates.getValue(userId2)
        userSettings.set(userId, userConfig.syncYomiEnabled, true)
        userSettings.set(userId, userConfig.syncInterval, 30.minutes)

        // Subscribe to the sync config of all users (creates the per-user config subscription)
        SyncManager.checkForNewUsers()
        // Wait for the (asynchronous) initial config emission to schedule user A's sync task
        awaitScheduledSyncTask(userId)
        assertTrue(SyncManager.scheduledSyncTasks.containsKey(userId))

        // Stamp a last-sync timestamp for user A
        syncPreferences.edit().putLong("last_sync_timestamp_$userId", 1234L).apply()

        // Delete user A
        transaction { UserAccountTable.deleteWhere { UserAccountTable.id eq userId } }

        // Run the check again - the deleted user's per-user state must be cleaned up
        SyncManager.checkForNewUsers()

        // User A's scheduled sync task has been descheduled
        assertTrue(SyncManager.scheduledSyncTasks[userId] == null)
        // User A's sync state has been evicted (a new flow is created on access)
        val stateAAfter = SyncManager.lastSyncState(userId)
        assertNotSame(stateA, SyncManager.syncStates.getValue(userId))
        assertEquals(null, stateAAfter.value)
        // User A's last-sync timestamp has been removed
        assertEquals(0L, syncPreferences.getLong("last_sync_timestamp_$userId", 0L))
        // User B's sync state is untouched
        assertSame(stateB, SyncManager.syncStates.getValue(userId2))
    }

    @Test
    fun rescheduleUsesPersistedLastScheduledSyncForInitialDelay() {
        // User A has sync enabled (30m interval) and last triggered a periodic sync 10m ago
        userSettings.set(userId, userConfig.syncYomiEnabled, true)
        userSettings.set(userId, userConfig.syncInterval, 30.minutes)
        syncPreferences
            .edit()
            .putLong("last_scheduled_sync_$userId", (Clock.System.now() - 10.minutes).toEpochMilliseconds())
            .apply()

        SyncManager.checkForNewUsers()
        awaitScheduledSyncTask(userId)

        // The initial delay should be the time until the next slot (~20m), not the full interval (30m)
        val (taskId, interval) = SyncManager.scheduledSyncTasks.getValue(userId)
        assertEquals(30.minutes, interval)
        val task = checkNotNull(HAScheduler.deschedule(taskId))
        val nextExecutionInMs = task.getNextExecutionTime() - System.currentTimeMillis()
        val expectedMs = 20.minutes.inWholeMilliseconds
        assertTrue(
            nextExecutionInMs in (expectedMs - 10_000)..(expectedMs + 10_000),
            "expected next execution in ~${expectedMs}ms, was ${nextExecutionInMs}ms",
        )

        // Clean up the (canceled) task and the persisted key
        SyncManager.scheduledSyncTasks.remove(userId)
        syncPreferences.edit().remove("last_scheduled_sync_$userId").apply()
    }

    @Test
    fun firstScheduleWaitsFullIntervalAndDisablingRemovesLastScheduledSync() {
        userSettings.set(userId, userConfig.syncYomiEnabled, true)
        userSettings.set(userId, userConfig.syncInterval, 30.minutes)

        SyncManager.checkForNewUsers()
        awaitScheduledSyncTask(userId)

        // With no persisted last_scheduled_sync, the key is stamped at schedule time...
        assertTrue(syncPreferences.contains("last_scheduled_sync_$userId"))
        // ...and the initial delay is the full interval
        val (taskId, _) = SyncManager.scheduledSyncTasks.getValue(userId)
        val task = checkNotNull(HAScheduler.deschedule(taskId))
        val nextExecutionInMs = task.getNextExecutionTime() - System.currentTimeMillis()
        val expectedMs = 30.minutes.inWholeMilliseconds
        assertTrue(
            nextExecutionInMs in (expectedMs - 10_000)..(expectedMs + 10_000),
            "expected next execution in ~${expectedMs}ms, was ${nextExecutionInMs}ms",
        )
        SyncManager.scheduledSyncTasks.remove(userId)

        // Disabling sync removes the persisted key
        userSettings.set(userId, userConfig.syncYomiEnabled, false)
        val deadline = System.currentTimeMillis() + 5_000
        while (syncPreferences.contains("last_scheduled_sync_$userId") && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(!syncPreferences.contains("last_scheduled_sync_$userId"))
    }
}
