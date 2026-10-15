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
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // Lazy: the shared preferences come from Koin, which is only started by @BeforeAll.
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("koreader_sync", Context.MODE_PRIVATE)
    }

    private fun key(
        base: String,
        userId: Int,
    ) = "${base}_$userId"

    @BeforeEach
    fun setUp() {
        userId = createUser("kosync_a")
        userId2 = createUser("kosync_b")
    }

    @AfterEach
    fun tearDown() {
        clearCredentials(userId)
        clearCredentials(userId2)
        userSettings.resetAll(userId)
        userSettings.resetAll(userId2)
        clearTables(ChapterTable, MangaTable)
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

    private fun insertChapter(
        mangaId: Int,
        name: String,
        sourceOrder: Int,
    ): Int =
        transaction {
            ChapterTable
                .insertAndGetId {
                    it[ChapterTable.url] = "$mangaId-$sourceOrder"
                    it[ChapterTable.name] = name
                    it[ChapterTable.sourceOrder] = sourceOrder
                    it[ChapterTable.manga] = mangaId
                    it[ChapterTable.memo] = JsonObject.EMPTY
                }.value
        }

    private fun readKoreaderHash(
        user: Int,
        chapter: Int,
    ): String? =
        transaction {
            ChapterUserTable
                .select(ChapterUserTable.koreaderHash)
                .where { (ChapterUserTable.user eq user) and (ChapterUserTable.chapter eq chapter) }
                .firstOrNull()
                ?.get(ChapterUserTable.koreaderHash)
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

    @Test
    fun pushGeneratesFilenameHashMatchingServedName() =
        runBlocking {
            val mangaId = createLibraryManga("My Manga", userId)
            val chapterId = insertChapter(mangaId, "Chapter 1", sourceOrder = 1)

            userSettings.set(userId, userConfig.koreaderSyncChecksumMethod, KoreaderSyncChecksumMethod.FILENAME)
            userSettings.set(userId, userConfig.koreaderSyncStrategyBackward, KoreaderSyncConflictStrategy.KEEP_LOCAL)
            // Credentials exist (bogus host is fine): the push aborts on pageCount = -1
            // before any network call, but the hash must already be persisted.
            setCredentials(userId, "https://koreader.invalid", "alice", "alice-key")

            assertTrue(KoreaderSyncService.pushProgress(userId, chapterId))

            // md5("My Manga - Chapter 1.cbz") — the exact file name the download endpoint serves.
            assertEquals("7dc341441abf6d1dda2b2dbd0412e931", readKoreaderHash(userId, chapterId))
        }

    @Test
    fun pushWithoutUserRowDoesNotThrow() =
        runBlocking {
            // Chapter the user never interacted with: the LEFT JOIN produces NULL user
            // columns which must not crash hash generation (defaults: BINARY method,
            // not downloaded -> hash is skipped, not pushed).
            val mangaId = createLibraryManga("NoRow Manga", userId)
            val chapterId = insertChapter(mangaId, "Chapter 1", sourceOrder = 1)

            userSettings.set(userId, userConfig.koreaderSyncStrategyBackward, KoreaderSyncConflictStrategy.KEEP_LOCAL)
            setCredentials(userId, "https://koreader.invalid", "alice", "alice-key")

            assertTrue(KoreaderSyncService.pushProgress(userId, chapterId))
            assertNull(readKoreaderHash(userId, chapterId))
        }

    @Test
    fun pushSkippedWhenBackwardDisabled() =
        runBlocking {
            val mangaId = createLibraryManga("Disabled Manga", userId)
            val chapterId = insertChapter(mangaId, "Chapter 1", sourceOrder = 1)

            // DISABLED is also the default; make it explicit.
            userSettings.set(userId, userConfig.koreaderSyncStrategyBackward, KoreaderSyncConflictStrategy.DISABLED)
            setCredentials(userId, "https://koreader.invalid", "alice", "alice-key")

            assertTrue(KoreaderSyncService.pushProgress(userId, chapterId))
            assertNull(readKoreaderHash(userId, chapterId))
        }

    @Test
    fun pullSkippedWhenBothDirectionsKeepLocal() =
        runBlocking {
            val mangaId = createLibraryManga("Local Manga", userId)
            val chapterId = insertChapter(mangaId, "Chapter 1", sourceOrder = 1)

            userSettings.set(userId, userConfig.koreaderSyncStrategyForward, KoreaderSyncConflictStrategy.KEEP_LOCAL)
            userSettings.set(userId, userConfig.koreaderSyncStrategyBackward, KoreaderSyncConflictStrategy.KEEP_LOCAL)
            setCredentials(userId, "https://koreader.invalid", "alice", "alice-key")

            assertNull(KoreaderSyncService.checkAndPullProgress(userId, chapterId))
        }

    @Test
    fun pullSkippedWhenBothDirectionsDisabled() =
        runBlocking {
            val mangaId = createLibraryManga("Off Manga", userId)
            val chapterId = insertChapter(mangaId, "Chapter 1", sourceOrder = 1)

            userSettings.set(userId, userConfig.koreaderSyncStrategyForward, KoreaderSyncConflictStrategy.DISABLED)
            userSettings.set(userId, userConfig.koreaderSyncStrategyBackward, KoreaderSyncConflictStrategy.DISABLED)
            setCredentials(userId, "https://koreader.invalid", "alice", "alice-key")

            assertNull(KoreaderSyncService.checkAndPullProgress(userId, chapterId))
        }
}
