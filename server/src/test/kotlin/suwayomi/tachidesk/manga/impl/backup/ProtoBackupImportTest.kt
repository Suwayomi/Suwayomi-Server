@file:Suppress("DEPRECATION")

package suwayomi.tachidesk.manga.impl.backup

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Sink
import okio.buffer
import okio.gzip
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.test.ApplicationTest
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProtoBackupImportTest : ApplicationTest() {
    // ids of the users created by the running test — cleanup deletes by identity, never by
    // broad "id != 1" predicates (see the conventions documented in TestUtils)
    private val createdUserIds = mutableListOf<Int>()

    // the actual pre-test value, restored verbatim — not a hardcoded guess at the default
    private var originalMaxSourcesInParallel = 0

    @BeforeEach
    fun captureSharedState() {
        originalMaxSourcesInParallel = serverConfig.maxSourcesInParallel.value
    }

    private fun insertUser(username: String): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = username
                    it[UserAccountTable.password] = Bcrypt.encryptPassword("password")
                }.value
        }.also { createdUserIds += it }

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
            val stream =
                buildBackupStream(
                    serverSettings = BackupServerSettings(maxSourcesInParallel = 12),
                    userSettings = null,
                )

            ProtoBackupImport.restoreLegacy(userId, stream, flags = BackupFlags.DEFAULT)

            assertEquals(
                originalMaxSourcesInParallel,
                serverConfig.maxSourcesInParallel.value,
                "the global setting must not be changed for users without MANAGE_SETTINGS",
            )
        }

    @Test
    fun restoreChangesGlobalSettingsWithManageSettings() =
        runTest {
            // user 1 has the ADMIN role in the database, which bypasses permission checks
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
                    userConfig.excludeUnreadChapters.value(userId),
                    "the legacy global value should become the importing user's override",
                )
            } finally {
                userSettings.reset(userId, userConfig.excludeUnreadChapters)
            }
        }

    @Test
    fun restoreStateIsOnlyVisibleToOwningUser() =
        runTest {
            val ownerId = insertUser("restoreowner")
            val otherId = insertUser("restoreother")
            val restoreId = "cross-user-${UUID.randomUUID()}"
            val stream = buildBackupStream(serverSettings = null, userSettings = null)

            ProtoBackupImport.restoreLegacy(ownerId, stream, restoreId = restoreId, flags = BackupFlags.DEFAULT)

            // after restoreLegacy returns the restore has settled in a terminal state; the
            // entry still exists until the delayed cleanup evicts it
            assertNotNull(
                ProtoBackupImport.getRestoreState(ownerId, restoreId),
                "the owner must be able to observe their own restore state",
            )
            assertNull(
                ProtoBackupImport.getRestoreState(otherId, restoreId),
                "another user must not observe the restore state",
            )
            assertNull(
                ProtoBackupImport.getRestoreState(1, restoreId),
                "not even the admin user may observe a restore owned by someone else",
            )
        }

    @AfterEach
    fun tearDown() {
        // restore the actual captured value — the default may change and the suite must not
        // bake assumptions into shared global config
        serverConfig.maxSourcesInParallel.value = originalMaxSourcesInParallel
        transaction {
            if (createdUserIds.isNotEmpty()) {
                UserSettingsTable.deleteWhere { UserSettingsTable.user inList createdUserIds }
                UserAccountTable.deleteWhere { UserAccountTable.id inList createdUserIds }
                createdUserIds.clear()
            }
        }
    }
}
