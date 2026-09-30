package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for the per-user SyncYomi and KOReader GraphQL endpoints.
 *
 * The `@RequireAuth` directive injects the caller's `userId`, so each operation only ever reads or writes the
 * caller's own per-user sync state / connection. The credential-less and disabled paths are exercised (no network).
 */
class SyncGraphQLTest : GraphQLTest() {
    @BeforeEach
    fun setUp() {
        userSettings.resetAll(1)
    }

    @AfterEach
    fun tearDown() {
        userSettings.resetAll(1)
    }

    @Test
    fun startSyncReturnsDisabledForDisabledUser() {
        val response =
            graphql(
                """
                mutation(${'$'}input: StartSyncInput!) {
                    startSync(input: ${'$'}input) {
                        result
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf<String, Any?>()),
            )

        response.assertNoErrors()
        assertEquals("SYNC_DISABLED", response.dataPath("startSync", "result"))
    }

    @Test
    fun lastSyncStatusIsNullForUserWithoutSync() {
        val response =
            graphql(
                """
                query {
                    lastSyncStatus {
                        state
                        startDate
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        // No sync has been started for the admin, so the status is null
        assertEquals(null, response.dataPath("lastSyncStatus"))
    }

    @Test
    fun koSyncStatusReturnsNotLoggedInForNoCredentials() {
        val response =
            graphql(
                """
                query {
                    koSyncStatus {
                        isLoggedIn
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(false, response.dataPath("koSyncStatus", "isLoggedIn"))
    }

    @Test
    fun userSettingsExposesPerUserSyncSettings() {
        // Set a per-user sync enabled override for the admin
        userSettings.set(1, userConfig.syncYomiEnabled, true)

        val response =
            graphql(
                """
                query {
                    userSettings {
                        syncYomiEnabled
                        syncInterval
                        syncYomiHost
                        syncDataManga
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        // The per-user override is visible
        assertEquals(true, response.dataPath("userSettings", "syncYomiEnabled"))
        // The rest fall back to the default values
        assertEquals(
            userConfig.syncInterval.defaultValue.toIsoString(),
            response.dataPath("userSettings", "syncInterval"),
        )
        assertEquals(
            userConfig.syncYomiHost.defaultValue,
            response.dataPath("userSettings", "syncYomiHost"),
        )
        assertEquals(
            userConfig.syncDataManga.defaultValue,
            response.dataPath("userSettings", "syncDataManga"),
        )
    }
}
