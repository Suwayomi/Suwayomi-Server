package suwayomi.tachidesk.global.impl.sync

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.and
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
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

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
        assertNotSame(SyncManager.lastSyncState(userId), SyncManager.lastSyncState(userId2))
        // Both start out with no sync state
        assertEquals(null, SyncManager.lastSyncState(userId).value)
        assertEquals(null, SyncManager.lastSyncState(userId2).value)
    }
}
