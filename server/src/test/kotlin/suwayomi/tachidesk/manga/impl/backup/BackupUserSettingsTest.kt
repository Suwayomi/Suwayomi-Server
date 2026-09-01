package suwayomi.tachidesk.manga.impl.backup

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.v1.core.SortOrder
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
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupSettingsHandler
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupUserSettingsHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionHeaderType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSettingsDownloadConversionType
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupUserSettings
import suwayomi.tachidesk.server.settings.UserSettingsRegistry
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for per-user settings backup support ([BackupUserSettingsHandler] and the `userSettings` backup proto
 * field): effective-value export, restore onto another user, legacy (pre-change) server-settings import, and the
 * proto round-trip.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackupUserSettingsTest : ApplicationTest() {
    private var userA: Int = 0
    private var userB: Int = 0
    private var userC: Int = 0

    @BeforeEach
    fun setUp() {
        userA = createUser("backupsettings_a")
        userB = createUser("backupsettings_b")
        userC = createUser("backupsettings_c")
    }

    @AfterEach
    fun tearDown() {
        userSettings.resetAll(userA)
        userSettings.resetAll(userB)
        userSettings.resetAll(userC)
        transaction {
            UserAccountTable.deleteWhere {
                (UserAccountTable.id eq userA) or (UserAccountTable.id eq userB) or (UserAccountTable.id eq userC)
            }
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
    fun backupExportsEffectiveValues() {
        userSettings.set(userA, userConfig.opdsItemsPerPage, 250)
        userSettings.set(userA, userConfig.excludeUnreadChapters, true)

        val backup = BackupUserSettingsHandler.backup(BackupFlags.DEFAULT, userA)
        assertNotNull(backup)

        // Overrides are exported as-is
        assertEquals(250, backup.opdsItemsPerPage)
        assertEquals(true, backup.excludeUnreadChapters)

        // Settings without an override are exported as the current global (effective) value, so restoring on a
        // server with different globals preserves the user's experience
        assertEquals(userConfig.opdsChapterSortOrder.defaultValue, backup.opdsChapterSortOrder)
        assertEquals(userConfig.koreaderSyncPercentageTolerance.defaultValue, backup.koreaderSyncPercentageTolerance)
        assertEquals(userConfig.autoDownloadNewChaptersLimit.defaultValue, backup.autoDownloadNewChaptersLimit)
    }

    @Test
    fun restoreAppliesToTargetUser() {
        userSettings.set(userA, userConfig.opdsItemsPerPage, 250)
        userSettings.set(userA, userConfig.excludeUnreadChapters, true)

        val backup = BackupUserSettingsHandler.backup(BackupFlags.DEFAULT, userA)
        assertNotNull(backup)

        BackupUserSettingsHandler.restore(userB, backup, null)

        // B receives A's effective values as its own overrides
        assertEquals(250, userSettings.value(userB, userConfig.opdsItemsPerPage))
        assertEquals(true, userSettings.value(userB, userConfig.excludeUnreadChapters))
        assertEquals(
            userConfig.opdsChapterSortOrder.defaultValue,
            userSettings.value(userB, userConfig.opdsChapterSortOrder),
        )

        // A is unaffected
        assertEquals(250, userSettings.value(userA, userConfig.opdsItemsPerPage))
        assertEquals(true, userSettings.value(userA, userConfig.excludeUnreadChapters))

        // B actually has override rows stored (one per exported setting)
        val rows =
            transaction {
                UserSettingsTable
                    .select(UserSettingsTable.value)
                    .where { UserSettingsTable.user eq userB }
                    .count()
                    .toInt()
            }
        assertEquals(UserSettingsRegistry.getAll().size, rows)
    }

    @Test
    fun restoreLegacyServerSettingsCreatesOverrides() {
        // A backup in the pre-change format: the 23 per-user settings live in serverSettings, userSettings is absent
        val legacy =
            BackupServerSettings(
                opdsItemsPerPage = 300,
                excludeUnreadChapters = true,
                koreaderSyncChecksumMethod = KoreaderSyncChecksumMethod.FILENAME,
            )

        BackupUserSettingsHandler.restore(userC, null, legacy)

        // The old global values become the importing user's per-user overrides
        assertEquals(300, userSettings.value(userC, userConfig.opdsItemsPerPage))
        assertEquals(true, userSettings.value(userC, userConfig.excludeUnreadChapters))
        assertEquals(
            KoreaderSyncChecksumMethod.FILENAME,
            userSettings.value(userC, userConfig.koreaderSyncChecksumMethod),
        )

        // Settings absent from the legacy backup stay on the global fallback
        assertEquals(
            userConfig.opdsChapterSortOrder.defaultValue,
            userSettings.value(userC, userConfig.opdsChapterSortOrder),
        )
    }

    @Test
    fun newBackupExportsDeprecatedServerSettingsAsNull() {
        val backup = assertNotNull(BackupSettingsHandler.backup(BackupFlags.DEFAULT))

        // The per-user settings are deprecated in the global config, so new backups no longer carry them in
        // serverSettings (they are carried in userSettings instead)
        assertNull(backup.opdsItemsPerPage)
        assertNull(backup.excludeUnreadChapters)
        assertNull(backup.autoDownloadNewChaptersLimit)
        assertNull(backup.koreaderSyncPercentageTolerance)
        assertNull(backup.opdsChapterSortOrder)
        assertNull(backup.serveConversions)
    }

    @Test
    fun backupProtoRoundTripPreservesUserSettings() {
        val parser = ProtoBuf
        val userSettingsBackup =
            BackupUserSettings(
                opdsItemsPerPage = 250,
                excludeUnreadChapters = true,
                opdsChapterSortOrder = SortOrder.ASC,
                koreaderSyncPercentageTolerance = 0.5,
                serveConversions =
                    listOf(
                        BackupSettingsDownloadConversionType(
                            "image/webp",
                            "image/jpeg",
                            0.8,
                            5.seconds,
                            10.seconds,
                            listOf(BackupSettingsDownloadConversionHeaderType("X-Test", "value")),
                        ),
                    ),
            )

        val backup = Backup(emptyList(), serverSettings = null, userSettings = userSettingsBackup)

        val bytes = parser.encodeToByteArray(Backup.serializer(), backup)
        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)

        val decodedUserSettings = assertNotNull(decoded.userSettings)
        assertEquals(250, decodedUserSettings.opdsItemsPerPage)
        assertEquals(true, decodedUserSettings.excludeUnreadChapters)
        assertEquals(SortOrder.ASC, decodedUserSettings.opdsChapterSortOrder)
        assertEquals(0.5, decodedUserSettings.koreaderSyncPercentageTolerance)

        val conversion = assertNotNull(decodedUserSettings.serveConversions?.single())
        assertEquals("image/webp", conversion.mimeType)
        assertEquals("image/jpeg", conversion.target)
        assertEquals(0.8, conversion.compressionLevel)
        assertEquals(5.seconds, conversion.callTimeout)
        assertEquals(10.seconds, conversion.connectTimeout)
        assertEquals("X-Test", conversion.headers?.single()?.name)
        assertEquals("value", conversion.headers?.single()?.value)
    }
}
