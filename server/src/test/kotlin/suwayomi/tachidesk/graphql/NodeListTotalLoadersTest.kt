package suwayomi.tachidesk.graphql

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.dataLoaders.CategoriesForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.CategoryCountForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ChapterCountForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ChaptersForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ExtensionCountForExtensionStore
import suwayomi.tachidesk.graphql.dataLoaders.ExtensionsForExtensionStore
import suwayomi.tachidesk.graphql.dataLoaders.MangaCountForCategoryDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaCountForSourceDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaForCategoryDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaForSourceDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.TrackRecordCountForMangaIdDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.TrackRecordCountForTrackerIdDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.TrackRecordsForMangaIdDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.TrackRecordsForTrackerIdDataLoader
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.server.JavalinSetup
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import suwayomi.tachidesk.test.ensureDefaultCategory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each count DataLoader has to give the same totals as the DataLoader loading the nodes of the same
 * list field, which it stands in for when only `totalCount` is selected.
 */
class NodeListTotalLoadersTest : GraphQLTest() {
    private var otherUserId = 0
    private lateinit var mangaIds: List<Int>
    private lateinit var categoryIds: List<Int>
    private val sourceIds = listOf(1L, SOURCE_ID, EMPTY_SOURCE_ID)
    private val extensionPkgs = listOf(EXTENSION_PKG, EMPTY_EXTENSION_PKG)
    private val trackerIds = listOf(1, 2, 3)
    private val storeIndexUrls = listOf(STORE_INDEX_URL, "https://empty.example/index.min.json")

    @BeforeEach
    fun seed() {
        otherUserId = createTestUser("other")
        ensureDefaultCategory(1)
        ensureDefaultCategory(otherUserId)

        // A: 3 chapters, categorized; B: uncategorized, no chapter; C: not in the library
        val a = createLibraryManga("A")
        val b = createLibraryManga("B")
        val c = transaction { insertManga("C") }
        createChapters(a, 3, read = false)
        createChapters(c, 2, read = true)

        val categorized = Category.createCategory(1, "Categorized")
        val empty = Category.createCategory(1, "Empty")
        CategoryManga.addMangaToCategory(1, a, categorized)
        val otherUsersCategory = Category.createCategory(otherUserId, "Other")
        CategoryManga.addMangaToCategory(otherUserId, a, otherUsersCategory)
        CategoryManga.addMangaToCategory(otherUserId, b, otherUsersCategory)

        transaction {
            insertTrackRecord(a, trackerId = 1, userId = 1)
            insertTrackRecord(a, trackerId = 2, userId = 1)
            insertTrackRecord(b, trackerId = 1, userId = 1)
            insertTrackRecord(a, trackerId = 1, userId = otherUserId)

            val extension = insertExtension(EXTENSION_PKG, STORE_INDEX_URL)
            insertExtension(EMPTY_EXTENSION_PKG, STORE_INDEX_URL)
            insertSource(SOURCE_ID, extension)
            insertSource(SOURCE_ID + 1, extension)
        }

        mangaIds = listOf(a, b, c)
        categoryIds =
            listOf(Category.getDefaultCategoryId(1)!!, Category.getDefaultCategoryId(otherUserId)!!, categorized, empty, otherUsersCategory)
    }

    @Test
    fun countLoadersMatchTheNodeLoadersForEachUser() {
        listOf(admin, UserType.Admin(otherUserId)).forEach { user ->
            assertSameTotals(user, ChaptersForMangaDataLoader(), ChapterCountForMangaDataLoader(), mangaIds)
            assertSameTotals(user, CategoriesForMangaDataLoader(), CategoryCountForMangaDataLoader(), mangaIds)
            assertSameTotals(user, TrackRecordsForMangaIdDataLoader(), TrackRecordCountForMangaIdDataLoader(), mangaIds)
            assertSameTotals(user, MangaForCategoryDataLoader(), MangaCountForCategoryDataLoader(), categoryIds)
            assertSameTotals(user, MangaForSourceDataLoader(), MangaCountForSourceDataLoader(), sourceIds)
            assertSameTotals(user, TrackRecordsForTrackerIdDataLoader(), TrackRecordCountForTrackerIdDataLoader(), trackerIds)
            assertSameTotals(user, ExtensionsForExtensionStore(), ExtensionCountForExtensionStore(), storeIndexUrls)
        }
    }

    private fun <K : Any> assertSameTotals(
        user: UserType,
        nodesLoader: KotlinDataLoader<K, out NodeList>,
        totalLoader: KotlinDataLoader<K, Int>,
        keys: List<K>,
    ) {
        val expected = load(user, nodesLoader, keys).map { it.totalCount }
        assertEquals(expected, load(user, totalLoader, keys), "${totalLoader.dataLoaderName} for $user and $keys")
    }

    private fun <K : Any, V : Any> load(
        user: UserType,
        loader: KotlinDataLoader<K, V>,
        keys: List<K>,
    ): List<V> {
        val dataLoader = loader.getDataLoader(mapOf(JavalinSetup.Attribute.TachideskUser to user).toGraphQLContext())
        val values = dataLoader.loadMany(keys)
        dataLoader.dispatch()
        return values.join()
    }

    private fun insertManga(title: String): Int =
        MangaTable
            .insertAndGetId {
                it[MangaTable.title] = title
                it[url] = title
                it[sourceReference] = SOURCE_ID
            }.value

    private fun insertTrackRecord(
        mangaId: Int,
        trackerId: Int,
        userId: Int,
    ) {
        TrackRecordTable.insert {
            it[TrackRecordTable.mangaId] = mangaId
            it[TrackRecordTable.trackerId] = trackerId
            it[remoteId] = 0
            it[title] = "t"
            it[lastChapterRead] = 0.0
            it[totalChapters] = 0
            it[status] = 1
            it[score] = 0.0
            it[remoteUrl] = "/u"
            it[startDate] = 0
            it[finishDate] = 0
            it[user] = userId
        }
    }

    private fun insertExtension(
        pkgName: String,
        storeIndexUrl: String,
    ): EntityID<Int> =
        ExtensionTable.insertAndGetId {
            it[apkName] = "$pkgName.apk"
            it[name] = pkgName
            it[ExtensionTable.pkgName] = pkgName
            it[versionName] = "1.0"
            it[versionCode] = 1
            it[lang] = "en"
            it[extensionLib] = "1.0"
            it[contentWarning] = 0
            it[ExtensionTable.storeIndexUrl] = storeIndexUrl
        }

    private fun insertSource(
        id: Long,
        extension: EntityID<Int>,
    ) {
        SourceTable.insert {
            it[SourceTable.id] = EntityID(id, SourceTable)
            it[name] = "Source $id"
            it[lang] = "en"
            it[SourceTable.extension] = extension
            it[contentWarning] = 0
        }
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(TrackRecordTable, ChapterTable, MangaTable)
        transaction {
            SourceTable.deleteWhere { SourceTable.id inList listOf(SOURCE_ID, SOURCE_ID + 1) }
            ExtensionTable.deleteWhere { ExtensionTable.pkgName inList extensionPkgs }
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }

    private companion object {
        const val SOURCE_ID = 1_000_000L
        const val EMPTY_SOURCE_ID = 2_000_000L
        const val EXTENSION_PKG = "eu.kanade.tachiyomi.extension.test.totals"
        const val EMPTY_EXTENSION_PKG = "eu.kanade.tachiyomi.extension.test.empty"
        const val STORE_INDEX_URL = "https://totals-test.example/index.min.json"
    }
}
