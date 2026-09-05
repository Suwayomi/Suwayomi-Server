package suwayomi.tachidesk.server.settings

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the [UserSettings] per-user override store: default fallback, overrides, reset, per-user isolation,
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserSettingsTest : ApplicationTest() {
    private var userId: Int = 0
    private var userId2: Int = 0

    @BeforeEach
    fun setUp() {
        userId = createUser("usettings_a")
        userId2 = createUser("usettings_b")
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
            UserCodeService.createUser(
                username,
                "password",
            )
        }

    @Test
    fun valueFallsBackToDefault() {
        assertEquals(
            userConfig.opdsItemsPerPage.defaultValue,
            userSettings.value(userId, userConfig.opdsItemsPerPage),
        )
        assertEquals(
            userConfig.excludeUnreadChapters.defaultValue,
            userSettings.value(userId, userConfig.excludeUnreadChapters),
        )
    }

    @Test
    fun setCreatesOverride() {
        userSettings.set(userId, userConfig.opdsItemsPerPage, 250)

        assertEquals(250, userSettings.value(userId, userConfig.opdsItemsPerPage))

        val stored =
            transaction {
                UserSettingsTable
                    .select(UserSettingsTable.value)
                    .where { (UserSettingsTable.user eq userId) and (UserSettingsTable.key eq "opdsItemsPerPage") }
                    .map { it[UserSettingsTable.value] }
                    .single()
            }
        assertEquals("250", stored)
    }

    @Test
    fun perUserIsolation() {
        userSettings.set(userId, userConfig.opdsItemsPerPage, 250)

        assertEquals(250, userSettings.value(userId, userConfig.opdsItemsPerPage))
        assertEquals(
            userConfig.opdsItemsPerPage.defaultValue,
            userSettings.value(userId2, userConfig.opdsItemsPerPage),
        )
    }

    @Test
    fun resetReSyncsToDefault() {
        userSettings.set(userId, userConfig.opdsItemsPerPage, 250)

        userSettings.reset(userId, userConfig.opdsItemsPerPage)

        assertEquals(
            userConfig.opdsItemsPerPage.defaultValue,
            userSettings.value(userId, userConfig.opdsItemsPerPage),
        )

        val rows =
            transaction {
                UserSettingsTable
                    .select(UserSettingsTable.value)
                    .where { (UserSettingsTable.user eq userId) and (UserSettingsTable.key eq "opdsItemsPerPage") }
                    .count()
            }
        assertEquals(0, rows)
    }

    @Test
    fun resetAllClearsAllOverrides() {
        userSettings.set(userId, userConfig.opdsItemsPerPage, 250)
        userSettings.set(userId, userConfig.excludeUnreadChapters, true)

        userSettings.resetAll(userId)

        assertEquals(
            userConfig.opdsItemsPerPage.defaultValue,
            userSettings.value(userId, userConfig.opdsItemsPerPage),
        )
        assertEquals(
            userConfig.excludeUnreadChapters.defaultValue,
            userSettings.value(userId, userConfig.excludeUnreadChapters),
        )

        val rows =
            transaction {
                UserSettingsTable
                    .select(UserSettingsTable.value)
                    .where { UserSettingsTable.user eq userId }
                    .count()
            }
        assertEquals(0, rows)
    }

    @Test
    fun setRejectsInvalidValue() {
        assertFailsWith<IllegalArgumentException> {
            userSettings.set(userId, userConfig.opdsItemsPerPage, 5)
        }

        // The invalid value must not have been stored
        assertEquals(
            userConfig.opdsItemsPerPage.defaultValue,
            userSettings.value(userId, userConfig.opdsItemsPerPage),
        )
    }

    @Test
    fun serveConversionsEncodeDecodeRoundTrip() {
        val conversions =
            mapOf(
                "image/webp" to
                    DownloadConversion(
                        target = "image/jpeg",
                        compressionLevel = 0.8,
                        callTimeout = 5.seconds,
                        connectTimeout = 10.seconds,
                        headers = mapOf("X-Test" to "value"),
                    ),
                "image/png" to DownloadConversion(target = "image/webp"),
            )

        val raw = UserSettings.encode(conversions)
        val decoded = UserSettings.decode(raw, userConfig.serveConversions)

        assertConversionsEqual(conversions, decoded)
    }

    @Test
    fun serveConversionsSetAndRead() {
        val conversions =
            mapOf(
                "image/webp" to
                    DownloadConversion(
                        target = "image/jpeg",
                        compressionLevel = 0.8,
                        callTimeout = 5.seconds,
                        connectTimeout = 10.seconds,
                        headers = mapOf("X-Test" to "value"),
                    ),
            )

        userSettings.set(userId, userConfig.serveConversions, conversions)

        assertConversionsEqual(conversions, userSettings.value(userId, userConfig.serveConversions))
    }

    @Test
    fun enumSettingEncodeDecodeRoundTrip() {
        assertEquals(
            SortOrder.ASC,
            UserSettings.decode(UserSettings.encode(SortOrder.ASC), userConfig.opdsChapterSortOrder),
        )
        assertEquals(
            SortOrder.DESC,
            UserSettings.decode(UserSettings.encode(SortOrder.DESC), userConfig.opdsChapterSortOrder),
        )
    }

    @Test
    fun primitiveEncodeDecodeRoundTrip() {
        assertEquals(true, UserSettings.decode(UserSettings.encode(true), userConfig.excludeUnreadChapters))
        assertEquals(42, UserSettings.decode(UserSettings.encode(42), userConfig.opdsItemsPerPage))
        assertEquals(0.5, UserSettings.decode(UserSettings.encode(0.5), userConfig.koreaderSyncPercentageTolerance))
    }

    private fun assertConversionsEqual(
        expected: Map<String, DownloadConversion>,
        actual: Map<String, DownloadConversion>,
    ) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (mimeType, e) ->
            val a = actual.getValue(mimeType)
            assertEquals(e.target, a.target, "target for $mimeType")
            assertEquals(e.compressionLevel, a.compressionLevel, "compressionLevel for $mimeType")
            assertEquals(e.callTimeout, a.callTimeout, "callTimeout for $mimeType")
            assertEquals(e.connectTimeout, a.connectTimeout, "connectTimeout for $mimeType")
            assertEquals(e.headers, a.headers, "headers for $mimeType")
        }
    }
}
