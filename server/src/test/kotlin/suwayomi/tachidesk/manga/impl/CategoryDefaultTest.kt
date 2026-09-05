package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.global.impl.sync.SyncYomiSyncService
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.StartSyncResult
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupCategoryHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.createLibraryManga
import suwayomi.tachidesk.test.ensureDefaultCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the per-user system-managed "Default" category row (`is_default_category`):
 * the implicit uncategorized bucket and its protection, visibility and toggle semantics.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryDefaultTest : ApplicationTest() {
    private val createdUserIds = mutableListOf<Int>()
    private val createdMangaIds = mutableListOf<Int>()

    private val syncPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("sync", Context.MODE_PRIVATE)
    }

    private fun createUser(username: String): Int {
        val userId =
            transaction {
                UserCodeService.createUser(username, "password")
            }
        createdUserIds.add(userId)
        return userId
    }

    private fun createManga(
        title: String,
        userId: Int,
    ): Int {
        val mangaId = createLibraryManga(title, userId)
        createdMangaIds.add(mangaId)
        return mangaId
    }

    private fun categoriesOf(userId: Int): List<CategoryDataClass> =
        transaction {
            CategoryTable
                .selectAll()
                .where { CategoryTable.user eq userId }
                .map { CategoryTable.toDataClass(it) }
        }

    @Test
    fun `default category row is flagged and at most one exists per user`() {
        ensureDefaultCategory(1)

        val flagged = categoriesOf(1).filter { it.isDefaultCategory }
        assertEquals(1, flagged.size, "user 1 should have exactly one flagged default category row")
        assertEquals(Category.DEFAULT_CATEGORY_NAME, flagged.single().name)

        // a second flagged row for the same user is rejected by the partial unique index
        assertFailsWith<ExposedSQLException> {
            transaction {
                CategoryTable.insert {
                    it[CategoryTable.name] = "Second Default"
                    it[CategoryTable.isDefaultCategory] = true
                    it[CategoryTable.user] = 1
                }
            }
        }
    }

    @Test
    fun `createUser inserts the default category row`() {
        val userId = createUser("defaultcat_a")

        val row = Category.getDefaultCategory(userId)
        assertNotNull(row, "createUser should create the user's default category row")
        assertEquals(Category.DEFAULT_CATEGORY_NAME, row.name)
        assertEquals(0, row.order)
        assertEquals(true, row.default)
        assertEquals(IncludeOrExclude.UNSET, row.includeInUpdate)
        assertEquals(IncludeOrExclude.UNSET, row.includeInDownload)
        assertEquals(true, row.isDefaultCategory)

        assertEquals(1, categoriesOf(userId).count { it.isDefaultCategory }, "exactly one flagged row per user")
    }

    @Test
    fun `auto update queues uncategorized manga for a second user`() {
        val userId = createUser("autoupdate_a")
        val mangaId = createManga("Uncat", userId)

        val updater = Injekt.get<IUpdater>()
        updater.reset()
        updater.addCategoriesToUpdateQueue(userId, Category.getCategoryList(userId), clear = true, forceAll = false)

        val deadline = System.currentTimeMillis() + 5_000
        var queued = false
        while (!queued && System.currentTimeMillis() < deadline) {
            queued = updater.getStatus().mangaUpdates.any { it.manga.id == mangaId }
            if (!queued) {
                Thread.sleep(50)
            }
        }
        updater.reset()

        assertTrue(queued, "a second user's uncategorized library manga should be queued for update (default row UNSET)")
    }

    @Test
    fun `auto download uses the default category toggles`() {
        val userId = createUser("autodownload_a")
        val mangaId = createManga("AutoDL", userId)

        // default row is UNSET -> treated as included
        assertEquals(true, Manga.isInIncludedDownloadCategory(userId, mangaId = mangaId))

        // EXCLUDE on the default row -> not downloaded
        val defaultCategoryId = Category.getDefaultCategoryId(userId)!!
        Category.updateCategory(userId, defaultCategoryId, null, null, null, IncludeOrExclude.EXCLUDE.value)
        assertEquals(false, Manga.isInIncludedDownloadCategory(userId, mangaId = mangaId))
    }

    @Test
    fun `impl protects the default category row`() {
        ensureDefaultCategory(1)
        val defaultCategoryId = Category.getDefaultCategoryId(1)!!

        // rename and landing-flag changes are no-ops on the default row
        Category.updateCategory(1, defaultCategoryId, "Renamed", false, null, null)
        var row = Category.getDefaultCategory(1)!!
        assertEquals(Category.DEFAULT_CATEGORY_NAME, row.name)
        assertEquals(true, row.default)

        // includeInUpdate/includeInDownload toggles are applied
        Category.updateCategory(1, defaultCategoryId, null, null, IncludeOrExclude.INCLUDE.value, IncludeOrExclude.INCLUDE.value)
        row = Category.getDefaultCategory(1)!!
        assertEquals(IncludeOrExclude.INCLUDE, row.includeInUpdate)
        assertEquals(IncludeOrExclude.INCLUDE, row.includeInDownload)

        // delete is a no-op
        Category.removeCategory(1, defaultCategoryId)
        assertNotNull(Category.getDefaultCategory(1), "the default category row must not be deletable")
    }

    @Test
    fun `create category named default returns the users default id`() {
        val userId = createUser("defaultname_a")
        val defaultCategoryId = Category.getDefaultCategoryId(userId)!!

        val id = Category.createCategory(userId, "Default")
        assertEquals(defaultCategoryId, id, "creating a category named 'Default' should return the existing default id")
        assertEquals(1, categoriesOf(userId).size, "no extra category should be created")
    }

    @Test
    fun `default category row visibility follows uncategorized manga`() {
        val userId = createUser("visibility_a")

        // no library manga -> hidden
        assertEquals(false, Category.getCategoryList(userId).any { it.isDefaultCategory })

        // uncategorized library manga -> visible
        val mangaId = createManga("VisManga", userId)
        assertEquals(true, Category.getCategoryList(userId).any { it.isDefaultCategory })

        // categorized -> hidden again
        val categoryId = Category.createCategory(userId, "Cat")
        CategoryManga.addMangaToCategory(userId, mangaId, categoryId)
        assertEquals(false, Category.getCategoryList(userId).any { it.isDefaultCategory })
    }

    @Test
    fun `backup restore maps the default entry onto the existing default row`() {
        val userId = createUser("backuprestore_a")
        // the user has no uncategorized manga, so the default row is hidden from getCategoryList
        val defaultCategoryId = Category.getDefaultCategoryId(userId)!!

        BackupCategoryHandler.restore(
            userId,
            listOf(
                BackupCategory("Default", 0),
                BackupCategory("Other", 1),
            ),
        )

        val allCats = categoriesOf(userId)
        assertEquals(1, allCats.count { it.name.equals("Default", true) }, "restoring a 'Default' entry must not create a duplicate")
        assertEquals(2, allCats.size)
        assertEquals(
            defaultCategoryId,
            allCats
                .first {
                    it.name.equals("Default", true)
                }.id,
            "the existing flagged row should match the backup entry",
        )
    }

    @Test
    fun `sync delete protection keeps the default row and removes remote-absent landing category`() =
        runTest {
            val userId = createUser("syncprotect_a")
            val mangaId = createManga("SyncManga", userId)
            val landingCategoryId =
                transaction {
                    CategoryTable
                        .insertAndGetId {
                            it[CategoryTable.name] = "Landing"
                            it[CategoryTable.order] = 1
                            it[CategoryTable.user] = userId
                            it[CategoryTable.isDefault] = true
                        }.value
                }

            userSettings.set(userId, userConfig.syncYomiEnabled, true)
            userSettings.set(userId, userConfig.syncDataCategories, true)
            // not the first sync
            syncPreferences.edit().putLong("last_sync_timestamp_$userId", 1234L).apply()

            val localVersion =
                transaction {
                    MangaUserTable
                        .select(MangaUserTable.version)
                        .where { (MangaUserTable.user eq userId) and (MangaUserTable.manga eq mangaId) }
                        .first()[MangaUserTable.version]
                }

            mockkObject(SyncYomiSyncService)
            try {
                // remote backup: the manga is up-to-date, but no categories at all
                coEvery { SyncYomiSyncService.doSync(any(), any(), any(), any(), any()) } returns SyncYomiSyncService.SyncResult(
                    Backup(
                        backupManga =
                            listOf(
                                BackupManga(
                                    source = 1L,
                                    url = "SyncManga",
                                    title = "SyncManga",
                                    favorite = true,
                                    version = localVersion,
                                ),
                            ),
                        backupCategories = emptyList(),
                        backupSources = emptyList(),
                        serverSettings = null,
                        userSettings = null,
                    ),
                    true,
                    false,
                )

                assertEquals(StartSyncResult.SUCCESS, SyncManager.startSync(userId))

                val deadline = System.currentTimeMillis() + 10_000
                while (
                    SyncManager.lastSyncState(userId).value !is SyncManager.SyncState.Success &&
                    SyncManager.lastSyncState(userId).value !is SyncManager.SyncState.Error &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(50)
                }
            } finally {
                unmockkObject(SyncYomiSyncService)
            }

            val state = SyncManager.lastSyncState(userId).value
            assertIs<SyncManager.SyncState.Success>(state, "sync should complete: $state")

            assertEquals(
                false,
                transaction {
                    CategoryTable
                        .selectAll()
                        .where { CategoryTable.id eq landingCategoryId }
                        .any()
                },
                "a landing category absent from the remote should be deleted",
            )
            assertNotNull(Category.getDefaultCategory(userId), "the default category row must never be deleted")
        }

    @AfterEach
    internal fun tearDown() {
        createdUserIds.forEach { userId ->
            userSettings.resetAll(userId)
            syncPreferences.edit().remove("last_sync_timestamp_$userId").apply()
        }
        transaction {
            if (createdUserIds.isNotEmpty()) {
                UserAccountTable.deleteWhere { UserAccountTable.id inList createdUserIds }
            }
            if (createdMangaIds.isNotEmpty()) {
                ChapterTable.deleteWhere { ChapterTable.manga inList createdMangaIds }
                MangaTable.deleteWhere { MangaTable.id inList createdMangaIds }
                CategoryMangaTable.deleteWhere { CategoryMangaTable.manga inList createdMangaIds }
            }
        }
        createdUserIds.clear()
        createdMangaIds.clear()
    }
}
