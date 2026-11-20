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
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncYomiChapterTombstoneTest : ApplicationTest() {
    private var syncingUser: Int = 0
    private var otherUser: Int = 0

    @BeforeEach
    fun setUp() {
        syncingUser = createUser("tombstone_a")
        otherUser = createUser("tombstone_b")
        userSettings.set(syncingUser, userConfig.syncYomiEnabled, true)
    }

    @AfterEach
    fun tearDown() {
        userSettings.resetAll(syncingUser)
        userSettings.resetAll(otherUser)
        Injekt
            .get<Application>()
            .getSharedPreferences("sync", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        transaction {
            UserAccountTable.deleteWhere { (UserAccountTable.id eq syncingUser) or (UserAccountTable.id eq otherUser) }
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
    fun removedChaptersArePendingOnlyForUsersWithSyncEnabled() {
        SyncYomiSyncService.recordChapterListChange(removedKeys = listOf("a", "b"), addedKeys = emptyList())

        assertEquals(setOf("a", "b"), SyncYomiSyncService.pendingDeletedChapters(syncingUser))
        assertEquals(emptySet(), SyncYomiSyncService.pendingDeletedChapters(otherUser))
    }

    @Test
    fun reAddedChaptersAreNoLongerPending() {
        SyncYomiSyncService.recordChapterListChange(removedKeys = listOf("a", "b"), addedKeys = emptyList())

        SyncYomiSyncService.recordChapterListChange(removedKeys = emptyList(), addedKeys = listOf("a"))

        assertEquals(setOf("b"), SyncYomiSyncService.pendingDeletedChapters(syncingUser))
    }

    @Test
    fun concurrentRemovalsAreAllKept() {
        val keys = (1..200).map { "k$it" }
        val pool = Executors.newFixedThreadPool(8)

        keys.forEach { key -> pool.execute { SyncYomiSyncService.recordChapterListChange(listOf(key), emptyList()) } }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        assertEquals(keys.toSet(), SyncYomiSyncService.pendingDeletedChapters(syncingUser))
    }
}
