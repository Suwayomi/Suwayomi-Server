package suwayomi.tachidesk.graphql

import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsMutationTest : GraphQLTest() {
    private fun viewerUser(): Pair<Int, UserType> {
        val userId = createTestUser("settingsviewer")
        return userId to UserType.User(userId, listOf(UserPermission.DOWNLOAD_CHAPTERS))
    }

    private fun managerUser(): UserType {
        val userId = createTestUser("settingsmanager")
        return UserType.User(userId, listOf(UserPermission.MANAGE_SETTINGS))
    }

    @Test
    fun settingsQueryReturnsDefaultsForNonManageSettingsUser() {
        val (_, user) = viewerUser()
        serverConfig.maxSourcesInParallel.value = 12
        try {
            val response =
                graphql(
                    """
                    query {
                        settings {
                            maxSourcesInParallel
                        }
                    }
                    """.trimIndent(),
                    user = user,
                )

            response.assertNoErrors()
            assertEquals(
                6,
                (response.dataPath("settings", "maxSourcesInParallel") as Number).toInt(),
                "users without MANAGE_SETTINGS should see the default value",
            )
        } finally {
            serverConfig.maxSourcesInParallel.value = 6
        }
    }

    @Test
    fun settingsQueryReturnsRealValuesForManageSettingsUser() {
        serverConfig.maxSourcesInParallel.value = 12
        try {
            val response =
                graphql(
                    """
                    query {
                        settings {
                            maxSourcesInParallel
                        }
                    }
                    """.trimIndent(),
                    user = managerUser(),
                )

            response.assertNoErrors()
            assertEquals(
                12,
                (response.dataPath("settings", "maxSourcesInParallel") as Number).toInt(),
                "users with MANAGE_SETTINGS should see the real value",
            )
        } finally {
            serverConfig.maxSourcesInParallel.value = 6
        }
    }

    @Test
    fun settingsQueryShowsTheCallersUserSettingsForMovedSettings() {
        val (userId, user) = viewerUser()

        userSettings.set(userId, userConfig.excludeUnreadChapters, false)

        val response =
            graphql(
                """
                query {
                    settings {
                        excludeUnreadChapters
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(
            false,
            response.dataPath("settings", "excludeUnreadChapters"),
            "the masked view should show the caller's own user setting for moved settings",
        )
    }

    @Test
    fun setSettingsDoesNotChangeGlobalSettingsWithoutManageSettings() {
        val (_, user) = viewerUser()
        serverConfig.maxSourcesInParallel.value = 12
        try {
            val response =
                graphql(
                    """
                    mutation(${'$'}input: SetSettingsInput!) {
                        setSettings(input: ${'$'}input) {
                            settings {
                                maxSourcesInParallel
                            }
                        }
                    }
                    """.trimIndent(),
                    mapOf("input" to mapOf("settings" to mapOf("maxSourcesInParallel" to 99))),
                    user = user,
                )

            response.assertNoErrors()
            assertEquals(
                12,
                serverConfig.maxSourcesInParallel.value,
                "the global setting must not be changed",
            )
            assertEquals(
                6,
                (response.dataPath("setSettings", "settings", "maxSourcesInParallel") as Number).toInt(),
                "the payload should show the masked default value",
            )
        } finally {
            serverConfig.maxSourcesInParallel.value = 6
        }
    }

    @Test
    fun setSettingsAppliesMovedSettingsToTheCallersUserSettings() {
        val (userId, user) = viewerUser()

        val response =
            graphql(
                """
                mutation(${'$'}input: SetSettingsInput!) {
                    setSettings(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("settings" to mapOf("excludeUnreadChapters" to false))),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(
            false,
            userSettings.value(userId, userConfig.excludeUnreadChapters),
            "the per-user-moved setting should be applied to the caller's user settings",
        )
    }

    @Test
    fun resetSettingsResetsTheCallersMovedUserSettings() {
        val (userId, user) = viewerUser()

        userSettings.set(userId, userConfig.excludeUnreadChapters, false)
        assertEquals(false, userSettings.value(userId, userConfig.excludeUnreadChapters))

        val response =
            graphql(
                """
                mutation(${'$'}input: ResetSettingsInput!) {
                    resetSettings(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf<String, Any?>()),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(
            true,
            userSettings.value(userId, userConfig.excludeUnreadChapters),
            "the per-user-moved setting should be reset to its default",
        )
    }

    @Test
    fun setSettingsUpdatesGlobalSettingsWithManageSettings() {
        serverConfig.maxSourcesInParallel.value = 6
        try {
            val response =
                graphql(
                    """
                    mutation(${'$'}input: SetSettingsInput!) {
                        setSettings(input: ${'$'}input) {
                            settings {
                                maxSourcesInParallel
                            }
                        }
                    }
                    """.trimIndent(),
                    mapOf("input" to mapOf("settings" to mapOf("maxSourcesInParallel" to 12))),
                    user = managerUser(),
                )

            response.assertNoErrors()
            assertEquals(12, serverConfig.maxSourcesInParallel.value)
            assertEquals(
                12,
                (response.dataPath("setSettings", "settings", "maxSourcesInParallel") as Number).toInt(),
                "the payload should show the real value",
            )
        } finally {
            serverConfig.maxSourcesInParallel.value = 6
        }
    }

    @AfterEach
    fun tearDown() {
        serverConfig.maxSourcesInParallel.value = 6
        transaction {
            UserSettingsTable.deleteAll()
        }
    }
}
