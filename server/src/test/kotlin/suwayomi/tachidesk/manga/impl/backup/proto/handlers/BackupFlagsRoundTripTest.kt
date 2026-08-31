package suwayomi.tachidesk.manga.impl.backup.proto.handlers

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.SyncRestoreMode
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.util.Date

// Tachiyomi sort/display/reader bitmasks are opaque to Suwayomi but must survive a sync round trip.
class BackupFlagsRoundTripTest : ApplicationTest() {
    private val backupFlags =
        BackupFlags(
            includeManga = true,
            includeCategories = true,
            includeChapters = false,
            includeTracking = false,
            includeHistory = false,
            includeClientData = false,
            includeServerSettings = false,
        )

    @AfterEach
    fun tearDown() {
        clearTables(CategoryTable, MangaTable)
    }

    @Test
    fun `category flags survive a sync round trip`() {
        BackupCategoryHandler.restore(
            listOf(BackupCategory(name = "Reading", order = 1, flags = 0x1234, uid = 7, version = 3)),
            SyncRestoreMode.ADOPT,
        )
        assertEquals(0x1234, BackupCategoryHandler.backup(backupFlags).single { it.name == "Reading" }.flags)

        // a zeroed copy outside ADOPT must not wipe the stored flags
        BackupCategoryHandler.restore(listOf(BackupCategory(name = "Reading", order = 1, uid = 7, version = 3)))
        assertEquals(0x1234, BackupCategoryHandler.backup(backupFlags).single { it.name == "Reading" }.flags)

        // an adopted zeroed copy wins
        BackupCategoryHandler.restore(
            listOf(BackupCategory(name = "Reading", order = 1, uid = 7, version = 4)),
            SyncRestoreMode.ADOPT,
        )
        assertEquals(0, BackupCategoryHandler.backup(backupFlags).single { it.name == "Reading" }.flags)
    }

    @Test
    fun `adopt keeps a newer local category`() {
        BackupCategoryHandler.restore(
            listOf(BackupCategory(name = "Reading", order = 1, flags = 7, uid = 9, version = 5)),
            SyncRestoreMode.ADOPT,
        )
        val adopted = BackupCategoryHandler.backup(backupFlags).single { it.name == "Reading" }

        // a stale echo with a lower version must not downgrade the local copy
        BackupCategoryHandler.restore(
            listOf(BackupCategory(name = "Reading", order = 3, flags = 1, uid = 9, version = 4)),
            SyncRestoreMode.ADOPT,
        )
        val category = BackupCategoryHandler.backup(backupFlags).single { it.name == "Reading" }
        assertEquals(5, category.version)
        assertEquals(7, category.flags)
        assertEquals(adopted.order, category.order)
    }

    @Test
    fun `manga viewer and chapter flags survive a sync round trip`() {
        val errors = mutableListOf<Pair<Date, String>>()
        val manga =
            BackupManga(
                source = 42,
                url = "/m",
                title = "Manga",
                viewer = 2,
                viewer_flags = 5,
                chapterFlags = 0x4321,
                version = 3,
            )

        BackupMangaHandler.restore(manga, emptyMap(), emptyMap(), errors, backupFlags, SyncRestoreMode.ADOPT)
        assertTrue(errors.isEmpty(), errors.joinToString())

        val exported = BackupMangaHandler.backup(backupFlags).single()
        assertEquals(2, exported.viewer)
        assertEquals(5, exported.viewer_flags)
        assertEquals(0x4321, exported.chapterFlags)

        // a zeroed copy outside ADOPT keeps the stored values
        BackupMangaHandler.restore(
            BackupManga(source = 42, url = "/m", title = "Manga", version = 3),
            emptyMap(),
            emptyMap(),
            errors,
            backupFlags,
        )
        assertTrue(errors.isEmpty(), errors.joinToString())
        val kept = BackupMangaHandler.backup(backupFlags).single()
        assertEquals(2, kept.viewer)
        assertEquals(5, kept.viewer_flags)
        assertEquals(0x4321, kept.chapterFlags)
    }
}
