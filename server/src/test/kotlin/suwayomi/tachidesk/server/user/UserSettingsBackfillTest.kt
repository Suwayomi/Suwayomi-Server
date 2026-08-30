package suwayomi.tachidesk.server.user

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.ts.config.GlobalConfigManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * Tests for the user settings backfill: saving user-set values of the (now per-user) settings to a file in the
 * application directory (pre-DB migration) and applying them as user 1 overrides (post-DB migration).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserSettingsBackfillTest : ApplicationTest() {
    // Lazy so the data root is captured after ApplicationTest.beforeAll() sets the rootDir system property
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val backfillFile by lazy {
        File(
            applicationDirs.dataRoot,
            USER_SETTINGS_BACKFILL_FILENAME,
        )
    }

    @AfterEach
    fun tearDown() {
        backfillFile.delete()
        userSettings.resetAll(1)
    }

    private fun setConfigValues(vararg values: Pair<String, Any>) {
        runBlocking {
            values.forEach { (key, value) ->
                GlobalConfigManager.updateValue(key, value)
            }
        }
    }

    @Test
    fun saveCapturesUserSetValues() {
        setConfigValues(
            "server.excludeUnreadChapters" to false,
            "server.opdsItemsPerPage" to 250,
            "server.opdsChapterSortOrder" to SortOrder.ASC,
            "server.syncInterval" to 12.hours,
            "server.syncYomiHost" to "https://sync.example.com",
            "server.serveConversions" to
                mapOf(
                    "image/webp" to DownloadConversion(target = "image/jpeg", headers = mapOf("X-Test" to "value")),
                ),
        )

        saveUserSettingsBackfillFile(applicationDirs)

        assertTrue(backfillFile.exists())
        val content = backfillFile.readText()
        assertTrue(content.contains("\"excludeUnreadChapters\""))
        assertTrue(content.contains("\"opdsItemsPerPage\""))
        assertTrue(content.contains("\"opdsChapterSortOrder\""))
        assertTrue(content.contains("\"syncInterval\""))
        assertTrue(content.contains("\"syncYomiHost\""))
        assertTrue(content.contains("\"serveConversions\""))
    }

    @Test
    fun applySetsUser1Overrides() {
        setConfigValues(
            "server.autoDownloadNewChapters" to true,
            "server.opdsItemsPerPage" to 250,
            "server.opdsChapterSortOrder" to SortOrder.ASC,
            "server.syncInterval" to 12.hours,
            "server.syncYomiHost" to "https://sync.example.com",
            "server.serveConversions" to
                mapOf(
                    "image/webp" to DownloadConversion(target = "image/jpeg", headers = mapOf("X-Test" to "value")),
                ),
        )

        saveUserSettingsBackfillFile(applicationDirs)
        applyUserSettingsBackfillFile(applicationDirs)

        assertEquals(true, userSettings.value(1, userConfig.autoDownloadNewChapters))
        assertEquals(250, userSettings.value(1, userConfig.opdsItemsPerPage))
        assertEquals(SortOrder.ASC, userSettings.value(1, userConfig.opdsChapterSortOrder))
        assertEquals(12.hours, userSettings.value(1, userConfig.syncInterval))
        assertEquals("https://sync.example.com", userSettings.value(1, userConfig.syncYomiHost))

        val conversions = userSettings.value(1, userConfig.serveConversions)
        assertEquals(setOf("image/webp"), conversions.keys)
        assertEquals("image/jpeg", conversions.getValue("image/webp").target)
        assertEquals(mapOf("X-Test" to "value"), conversions.getValue("image/webp").headers)

        // Config values persist across tests (GlobalConfigManager is a JVM singleton), so the save may have
        // captured values set by other tests; assert this test's keys are all present.
        val storedKeys =
            transaction {
                UserSettingsTable
                    .select(UserSettingsTable.key)
                    .where { UserSettingsTable.user eq 1 }
                    .map { it[UserSettingsTable.key] }
                    .toSet()
            }
        assertTrue(
            storedKeys.containsAll(
                setOf(
                    "autoDownloadNewChapters",
                    "opdsItemsPerPage",
                    "opdsChapterSortOrder",
                    "syncInterval",
                    "syncYomiHost",
                    "serveConversions",
                ),
            ),
        )
    }

    @Test
    fun saveSkipsUnsetSettings() {
        setConfigValues("server.opdsMarkAsReadOnDownload" to true)

        saveUserSettingsBackfillFile(applicationDirs)

        val content = backfillFile.readText()
        assertTrue(content.contains("\"opdsMarkAsReadOnDownload\""))
        assertFalse(content.contains("\"opdsSkipChapterMetadataFeed\""))
    }

    @Test
    fun applyWithoutFileIsNoop() {
        assertFalse(backfillFile.exists())

        applyUserSettingsBackfillFile(applicationDirs)

        assertEquals(
            userConfig.opdsItemsPerPage.defaultValue,
            userSettings.value(1, userConfig.opdsItemsPerPage),
        )
        val rows =
            transaction {
                UserSettingsTable
                    .select(UserSettingsTable.key)
                    .where { UserSettingsTable.user eq 1 }
                    .count()
            }
        assertEquals(0, rows)
    }

    @Test
    fun applyIsIdempotent() {
        setConfigValues(
            "server.excludeNotStarted" to false,
            "server.opdsItemsPerPage" to 150,
        )

        saveUserSettingsBackfillFile(applicationDirs)
        applyUserSettingsBackfillFile(applicationDirs)
        applyUserSettingsBackfillFile(applicationDirs)

        assertEquals(false, userSettings.value(1, userConfig.excludeNotStarted))
        assertEquals(150, userSettings.value(1, userConfig.opdsItemsPerPage))
    }
}
