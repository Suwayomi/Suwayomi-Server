@file:Suppress("DEPRECATION")

package suwayomi.tachidesk.manga.impl.backup

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Sink
import okio.buffer
import okio.gzip
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupUserSettings
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtoBackupImportTest : ApplicationTest() {
    private fun insertUser(username: String): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = username
                    it[UserAccountTable.password] = Bcrypt.encryptPassword("password")
                }.value
        }

    /**
     * Builds a gzipped backup stream (the format [ProtoBackupImport] expects for manual
     * restores) with the given server/user settings blocks.
     */
    private fun buildBackupStream(
        serverSettings: BackupServerSettings?,
        userSettings: BackupUserSettings?,
    ): ByteArrayInputStream {
        val backup =
            Backup(
                backupManga = emptyList(),
                serverSettings = serverSettings,
                userSettings = userSettings,
            )
        val encoded = ProtoBackupImport.parser.encodeToByteArray(Backup.serializer(), backup)
        val buffer = Buffer()
        (buffer as Sink)
            .gzip()
            .buffer()
            .use { it.write(encoded) }
        return ByteArrayInputStream(buffer.readByteArray())
    }

    @Test
    fun restoreDoesNotChangeGlobalSettingsWithoutManageSettings() =
        runTest {
            val userId = insertUser("restorenoperm")
            serverConfig.maxSourcesInParallel.value = 6
            try {
                val stream =
                    buildBackupStream(
                        serverSettings = BackupServerSettings(maxSourcesInParallel = 12),
                        userSettings = null,
                    )

                ProtoBackupImport.restoreLegacy(userId, stream, flags = BackupFlags.DEFAULT)

                assertEquals(
                    6,
                    serverConfig.maxSourcesInParallel.value,
                    "the global setting must not be changed for users without MANAGE_SETTINGS",
                )
            } finally {
                serverConfig.maxSourcesInParallel.value = 6
            }
        }

    @Test
    fun restoreChangesGlobalSettingsWithManageSettings() =
        runTest {
            // user 1 has the ADMIN role in the database, which bypasses permission checks
            serverConfig.maxSourcesInParallel.value = 6
            try {
                val stream =
                    buildBackupStream(
                        serverSettings = BackupServerSettings(maxSourcesInParallel = 12),
                        userSettings = null,
                    )

                ProtoBackupImport.restoreLegacy(1, stream, flags = BackupFlags.DEFAULT)

                assertEquals(
                    12,
                    serverConfig.maxSourcesInParallel.value,
                    "the global setting should be restored for privileged users",
                )
            } finally {
                serverConfig.maxSourcesInParallel.value = 6
            }
        }

    @Test
    fun restoreAppliesLegacyServerSettingsAsUserOverridesWithoutManageSettings() =
        runTest {
            val userId = insertUser("restorelegacy")
            try {
                // an old-format backup: the per-user-moved setting lives in serverSettings and
                // there is no userSettings block; it should still land as the importing user's
                // per-user override
                val stream =
                    buildBackupStream(
                        serverSettings = BackupServerSettings(excludeUnreadChapters = false),
                        userSettings = null,
                    )

                ProtoBackupImport.restoreLegacy(userId, stream, flags = BackupFlags.DEFAULT)

                assertEquals(
                    false,
                    userSettings.value(userId, userConfig.excludeUnreadChapters),
                    "the legacy global value should become the importing user's override",
                )
            } finally {
                userSettings.reset(userId, userConfig.excludeUnreadChapters)
            }
        }

    @AfterEach
    fun tearDown() {
        serverConfig.maxSourcesInParallel.value = 6
        transaction {
            UserSettingsTable.deleteAll()
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
