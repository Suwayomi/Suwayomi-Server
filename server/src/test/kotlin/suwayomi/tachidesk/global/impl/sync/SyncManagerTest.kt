package suwayomi.tachidesk.global.impl.sync

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.StartSyncResult
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for the per-user [SyncManager].
 *
 * Each user has their own sync account and enablement. These tests verify the per-user enablement (with the global
 * value as fallback) and the per-user sync state without exercising the network (the disabled paths return before any
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
    fun syncEnabledPerUserIsolation() {
        // Enable sync for user A only (a per-user override); user B keeps the global (disabled) fallback
        userSettings.set(userId, userConfig.syncYomiEnabled, true)

        assertEquals(true, userSettings.value(userId, userConfig.syncYomiEnabled))
        assertEquals(false, userSettings.value(userId2, userConfig.syncYomiEnabled))

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
}
