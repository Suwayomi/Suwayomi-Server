package suwayomi.tachidesk.manga.impl.backup.proto.handlers

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.SyncRestoreMode
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables

class CategoryOrderRoundTripTest : ApplicationTest() {
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
        clearTables(CategoryTable)
    }

    @Test
    fun `sync restore writes ranks instead of raw 0-based wire orders`() {
        val mapping =
            BackupCategoryHandler.restore(
                listOf(
                    BackupCategory(name = "A", order = 0, uid = 1, version = 1),
                    BackupCategory(name = "B", order = 1, uid = 2, version = 1),
                    BackupCategory(name = "C", order = 2, uid = 3, version = 1),
                ),
                SyncRestoreMode.ADOPT,
            )

        val orderByName = Category.getCategoryList().associate { it.name to it.order }
        assertEquals(mapOf("A" to 1, "B" to 2, "C" to 3), orderByName)

        // membership mapping stays keyed by the wire order values
        val idByName = Category.getCategoryList().associate { it.name to it.id }
        assertEquals(idByName["A"], mapping[0])
        assertEquals(idByName["B"], mapping[1])
        assertEquals(idByName["C"], mapping[2])
    }

    @Test
    fun `collided incoming orders get distinct ranks`() {
        BackupCategoryHandler.restore(
            listOf(
                BackupCategory(name = "A", order = 0, uid = 1, version = 1),
                BackupCategory(name = "B", order = 0, uid = 2, version = 1),
                BackupCategory(name = "C", order = 1, uid = 3, version = 1),
            ),
            SyncRestoreMode.ADOPT,
        )

        val orderByName = Category.getCategoryList().associate { it.name to it.order }
        assertEquals(mapOf("A" to 1, "B" to 2, "C" to 3), orderByName)
    }

    @Test
    fun `plain restore ranks matched categories and appends new ones`() {
        Category.createCategory("k1")
        Category.createCategory("k2")

        BackupCategoryHandler.restore(
            listOf(
                BackupCategory(name = "k2", order = 0, version = 1),
                BackupCategory(name = "new", order = 1, version = 1),
            ),
        )

        val orderByName = Category.getCategoryList().associate { it.name to it.order }
        assertEquals(0, orderByName["k1"])
        assertEquals(1, orderByName["k2"])
        assertEquals(2, orderByName["new"])
    }

    @Test
    fun `wire rebase makes categories 0-based and remaps manga refs`() {
        val categories =
            listOf(
                BackupCategory(name = "A", order = 1),
                BackupCategory(name = "B", order = 2),
            )
        val manga = BackupManga(source = 42, url = "/m", title = "Manga", categories = listOf(2, 1, 7))

        SyncManager.toWireCategoryOrders(categories, listOf(manga))

        assertEquals(listOf(0, 1), categories.map { it.order })
        assertEquals(listOf(1, 0), manga.categories)
    }

    @Test
    fun `wire orders survive a sync round trip`() {
        val wire =
            listOf(
                BackupCategory(name = "A", order = 0, uid = 1, version = 1),
                BackupCategory(name = "B", order = 1, uid = 2, version = 1),
            )
        BackupCategoryHandler.restore(wire, SyncRestoreMode.ADOPT)

        val exported =
            BackupCategoryHandler
                .backup(backupFlags)
                .filter { it.name != Category.DEFAULT_CATEGORY_NAME }
        SyncManager.toWireCategoryOrders(exported, emptyList())

        assertEquals(wire.map { it.name to it.order }, exported.map { it.name to it.order })
    }
}
