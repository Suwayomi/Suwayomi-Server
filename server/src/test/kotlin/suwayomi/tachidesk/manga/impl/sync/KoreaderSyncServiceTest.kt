package suwayomi.tachidesk.manga.impl.sync

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests for the per-user [KoreaderSyncService] connection handling.
 *
 * Connection credentials (server address, username, user key, device id) are stored per user in SharedPreferences,
 * keyed by user id. These tests verify the per-user isolation without exercising the network (the credential-less
 * paths short-circuit before any HTTP call).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KoreaderSyncServiceTest : ApplicationTest() {
    private var userId: Int = 0
    private var userId2: Int = 0

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("koreader_sync", Context.MODE_PRIVATE)

    private fun key(
        base: String,
        user: Int,
    ) = "${base}_$user"

    @BeforeEach
    fun setUp() {
        userId = createUser("kosync_a")
        userId2 = createUser("kosync_b")
    }

    @AfterEach
    fun tearDown() {
        clearCredentials(userId)
        clearCredentials(userId2)
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

    private fun setCredentials(
        user: Int,
        serverAddress: String,
        username: String,
        userKey: String,
    ) {
        preferences
            .edit()
            .putString(key("server_address", user), serverAddress)
            .putString(key("username", user), username)
            .putString(key("user_key", user), userKey)
            .apply()
    }

    private fun clearCredentials(user: Int) {
        preferences
            .edit()
            .remove(key("server_address", user))
            .remove(key("username", user))
            .remove(key("user_key", user))
            .remove(key("client_id", user))
            .apply()
    }

    @Test
    fun getStatusReturnsNotLoggedInWhenNoCredentials() =
        runBlocking {
            val status = KoreaderSyncService.getStatus(userId)

            assertFalse(status.isLoggedIn)
            assertEquals(null, status.serverAddress)
            assertEquals(null, status.username)
        }

    @Test
    fun logoutClearsOnlyThatUsersCredentials() {
        // Simulate a successful connect for both users by writing their credentials
        setCredentials(userId, "https://a.example.com", "alice", "alice-key")
        setCredentials(userId2, "https://b.example.com", "bob", "bob-key")

        assertEquals("alice", preferences.getString(key("username", userId), ""))
        assertEquals("bob", preferences.getString(key("username", userId2), ""))

        // Log out user A only
        KoreaderSyncService.logout(userId)

        // User A's credentials are cleared
        assertEquals(null, preferences.getString(key("username", userId), null))
        assertEquals(null, preferences.getString(key("user_key", userId), null))
        assertEquals(null, preferences.getString(key("server_address", userId), null))
        assertEquals(null, preferences.getString(key("client_id", userId), null))

        // User B's credentials are untouched
        assertEquals("https://b.example.com", preferences.getString(key("server_address", userId2), null))
        assertEquals("bob", preferences.getString(key("username", userId2), null))
        assertEquals("bob-key", preferences.getString(key("user_key", userId2), null))
    }

    @Test
    fun perUserConnectionIsolation() {
        setCredentials(userId, "https://a.example.com", "alice", "alice-key")

        assertEquals("alice", preferences.getString(key("username", userId), ""))
        // User B is unaffected
        assertEquals(null, preferences.getString(key("username", userId2), null))
    }
}
