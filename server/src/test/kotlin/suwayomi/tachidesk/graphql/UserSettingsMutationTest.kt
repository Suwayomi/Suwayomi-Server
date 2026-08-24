package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for the `userSettings` query and `setUserSettings`/`resetUserSettings` mutations.
 *
 * The `@RequireAuth` directive injects the caller's `userId`, so each operation only ever reads or writes the
 * caller's own per-user settings.
 */
class UserSettingsMutationTest : GraphQLTest() {
    private var originalOpdsItemsPerPage: Int = 0

    @AfterEach
    fun tearDown() {
        userSettings.resetAll(1)
        serverConfig.opdsItemsPerPage.value = originalOpdsItemsPerPage
    }

    private fun userSettingsQuery(settings: String) =
        graphql(
            """
            query {
                userSettings {
                    $settings
                }
            }
            """.trimIndent(),
        )

    @Test
    fun userSettingsQueryReturnsCallerValues() {
        originalOpdsItemsPerPage = serverConfig.opdsItemsPerPage.value
        userSettings.set(1, userConfig.opdsItemsPerPage, 250)

        val response =
            userSettingsQuery(
                """
                opdsItemsPerPage
                excludeUnreadChapters
                autoDownloadNewChaptersLimit
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertEquals(250, response.dataPath("userSettings", "opdsItemsPerPage"))
        assertEquals(
            serverConfig.excludeUnreadChapters.value,
            response.dataPath("userSettings", "excludeUnreadChapters"),
        )
        assertEquals(
            serverConfig.autoDownloadNewChaptersLimit.value,
            response.dataPath("userSettings", "autoDownloadNewChaptersLimit"),
        )
    }

    @Test
    fun setUserSettingsUpdatesCallerOnly() {
        originalOpdsItemsPerPage = serverConfig.opdsItemsPerPage.value
        val user2Id = createTestUser("usettings_gql_user2")
        val user2 = UserType.User(user2Id, emptyList())

        val mutationResponse =
            graphql(
                """
                mutation(${'$'}input: SetUserSettingsInput!) {
                    setUserSettings(input: ${'$'}input) {
                        userSettings {
                            opdsItemsPerPage
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("userSettings" to mapOf("opdsItemsPerPage" to 250))),
            )
        mutationResponse.assertNoErrors()
        assertEquals(250, mutationResponse.dataPath("setUserSettings", "userSettings", "opdsItemsPerPage"))

        // The caller (admin) sees the override
        val adminQuery = userSettingsQuery("opdsItemsPerPage")
        adminQuery.assertNoErrors()
        assertEquals(250, adminQuery.dataPath("userSettings", "opdsItemsPerPage"))

        // User 2 is unaffected and still falls back to the global value
        val user2Query =
            graphql(
                """
                query {
                    userSettings {
                        opdsItemsPerPage
                    }
                }
                """.trimIndent(),
                user = user2,
            )
        user2Query.assertNoErrors()
        assertEquals(
            serverConfig.opdsItemsPerPage.value,
            user2Query.dataPath("userSettings", "opdsItemsPerPage"),
        )
    }

    @Test
    fun setUserSettingsRejectsInvalidValue() {
        originalOpdsItemsPerPage = serverConfig.opdsItemsPerPage.value

        val response =
            graphql(
                """
                mutation(${'$'}input: SetUserSettingsInput!) {
                    setUserSettings(input: ${'$'}input) {
                        userSettings {
                            opdsItemsPerPage
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("userSettings" to mapOf("opdsItemsPerPage" to 5))),
            )

        response.assertHasError()
    }

    @Test
    fun resetUserSettingsReSyncsToGlobal() {
        originalOpdsItemsPerPage = serverConfig.opdsItemsPerPage.value
        userSettings.set(1, userConfig.opdsItemsPerPage, 250)
        serverConfig.opdsItemsPerPage.value = 300

        val response =
            graphql(
                """
                mutation(${'$'}input: ResetUserSettingsInput!) {
                    resetUserSettings(input: ${'$'}input) {
                        userSettings {
                            opdsItemsPerPage
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf<String, Any?>()),
            )

        response.assertNoErrors()
        assertEquals(300, response.dataPath("resetUserSettings", "userSettings", "opdsItemsPerPage"))
    }

    @Test
    fun visitorCannotAccessUserSettings() {
        originalOpdsItemsPerPage = serverConfig.opdsItemsPerPage.value

        val response =
            graphql(
                """
                query {
                    userSettings {
                        opdsItemsPerPage
                    }
                }
                """.trimIndent(),
                user = UserType.Visitor,
            )

        response.assertHasError()
    }

    @Test
    fun serveConversionsViaGraphQL() {
        originalOpdsItemsPerPage = serverConfig.opdsItemsPerPage.value

        val conversionInput =
            mapOf(
                "mimeType" to "image/webp",
                "target" to "image/jpeg",
                "compressionLevel" to 0.8,
                "callTimeout" to "PT5S",
                "connectTimeout" to "PT10S",
                "headers" to listOf(mapOf("name" to "X-Test", "value" to "value")),
            )

        val mutationResponse =
            graphql(
                """
                mutation(${'$'}input: SetUserSettingsInput!) {
                    setUserSettings(input: ${'$'}input) {
                        userSettings {
                            serveConversions {
                                mimeType
                                target
                                compressionLevel
                                callTimeout
                                connectTimeout
                                headers {
                                    name
                                    value
                                }
                            }
                        }
                    }
                }
                """.trimIndent(),
                mapOf(
                    "input" to
                        mapOf(
                            "userSettings" to
                                mapOf(
                                    "serveConversions" to listOf(conversionInput),
                                ),
                        ),
                ),
            )

        mutationResponse.assertNoErrors()
        val conversion = mutationResponse.dataPath("setUserSettings", "userSettings", "serveConversions", "0") as Map<*, *>
        assertEquals("image/webp", conversion["mimeType"])
        assertEquals("image/jpeg", conversion["target"])
        assertEquals(0.8, conversion["compressionLevel"])
        assertEquals("PT5S", conversion["callTimeout"])
        assertEquals("PT10S", conversion["connectTimeout"])
        val header = (conversion["headers"] as List<*>)[0] as Map<*, *>
        assertEquals("X-Test", header["name"])
        assertEquals("value", header["value"])
    }
}
