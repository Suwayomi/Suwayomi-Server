package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MangaListTest : GraphQLTest() {
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val createdMangaIds = mutableListOf<Int>()

    @Test
    fun browseUpdatesChangedCoversAlongsideUnchangedAndMissingUrls() {
        val sourceId = 456L
        val changedUrl = "/browse-cover-changed"
        val sameUrl = "/browse-cover-same"
        val missingUrl = "/browse-cover-missing"
        val changedId = insertBrowseManga(sourceId, changedUrl, "https://covers.test/old.png", 100, 1)
        val sameId = insertBrowseManga(sourceId, sameUrl, "https://covers.test/same.png", 200, 2)
        val missingId = insertBrowseManga(sourceId, missingUrl, "https://covers.test/kept.png", 300, 3)
        listOf(applicationDirs.tempThumbnailCacheRoot, applicationDirs.thumbnailDownloadsRoot).forEach { root ->
            File(root).mkdirs()
            File(root, "$changedId.png").writeBytes(byteArrayOf(1))
        }

        val entries =
            listOf(
                browseEntry(changedUrl, "https://covers.test/new.png"),
                browseEntry(sameUrl, "https://covers.test/same.png"),
                browseEntry(missingUrl, null),
            )

        assertEquals(listOf(changedId, sameId, missingId), MangasPage(entries, false).insertOrUpdate(sourceId))

        val rows =
            transaction {
                MangaTable
                    .selectAll()
                    .where { MangaTable.id inList listOf(changedId, sameId, missingId) }
                    .associateBy { it[MangaTable.id].value }
            }
        val changed = rows.getValue(changedId)
        assertEquals("https://covers.test/new.png", changed[MangaTable.thumbnail_url])
        assertTrue(changed[MangaTable.thumbnailUrlVersion] > 100)
        assertTrue(changed[MangaTable.thumbnailUrlLastFetched] > 1)
        assertEquals("Updated", changed[MangaTable.title])
        assertEquals("Updated artist", changed[MangaTable.artist])
        assertEquals("Updated author", changed[MangaTable.author])
        assertEquals("Updated description", changed[MangaTable.description])
        assertEquals("Updated genre", changed[MangaTable.genre])
        assertEquals(SManga.ONGOING, changed[MangaTable.status])
        assertEquals("updated", changed[MangaTable.memo].getValue("key").jsonPrimitive.content)
        assertFalse(File(applicationDirs.tempThumbnailCacheRoot, "$changedId.png").exists())
        assertFalse(File(applicationDirs.thumbnailDownloadsRoot, "$changedId.png").exists())

        val same = rows.getValue(sameId)
        assertEquals("https://covers.test/same.png", same[MangaTable.thumbnail_url])
        assertEquals(200, same[MangaTable.thumbnailUrlVersion])
        assertEquals(2, same[MangaTable.thumbnailUrlLastFetched])

        val missing = rows.getValue(missingId)
        assertEquals("https://covers.test/kept.png", missing[MangaTable.thumbnail_url])
        assertEquals(300, missing[MangaTable.thumbnailUrlVersion])
        assertEquals(3, missing[MangaTable.thumbnailUrlLastFetched])
    }

    @Test
    fun browseCoverVersionStaysMonotonicAheadOfTheClock() {
        val sourceId = 457L
        val url = "/browse-cover-monotonic"
        val futureVersion = System.currentTimeMillis() + 24 * 60 * 60 * 1_000L
        val mangaId = insertBrowseManga(sourceId, url, "https://covers.test/old.png", futureVersion, 1)

        MangasPage(listOf(browseEntry(url, "https://covers.test/new.png")), false).insertOrUpdate(sourceId)

        val version =
            transaction {
                MangaTable
                    .selectAll()
                    .where { MangaTable.id eq mangaId }
                    .first()[MangaTable.thumbnailUrlVersion]
            }
        assertTrue(version > futureVersion)
    }

    @Test
    fun browseBatchShapeAllChangedCovers() = checkBrowseBatchShape(461L, "all-changed", listOf(true, true, true))

    @Test
    fun browseBatchShapeAllUnchangedCovers() = checkBrowseBatchShape(462L, "all-unchanged", listOf(false, false, false))

    @Test
    fun browseBatchShapeMixedChangedAndUnchangedCovers() = checkBrowseBatchShape(463L, "mixed", listOf(true, true, false, false))

    @Test
    fun browseUpdatesMangaWithoutUserRows() = checkBrowse("none", false, "Updated")

    @Test
    fun browseUpdatesUnsavedManga() = checkBrowse("false", false, "Updated")

    @Test
    fun browsePreservesSavedManga() = checkBrowse("true", false, "Original")

    @Test
    fun browsePreservesMangaSavedByLaterUser() = checkBrowse("false:true", false, "Original")

    @Test
    fun browsePreservesMangaSavedByEarlierUser() = checkBrowse("true:false", false, "Original")

    @Test
    fun browseUpdatesMangaUnsavedByAllUsers() = checkBrowse("false:false", false, "Updated")

    @Test
    fun localBrowseUpdatesMangaWithoutUserRows() = checkBrowse("none", true, "Updated")

    @Test
    fun localBrowseUpdatesSavedManga() = checkBrowse("true", true, "Updated")

    @Test
    fun localBrowseUpdatesMangaSavedByAnotherUser() = checkBrowse("false:true", true, "Updated")

    private fun insertBrowseManga(
        sourceId: Long,
        url: String,
        thumbnailUrl: String,
        thumbnailUrlVersion: Long,
        thumbnailUrlLastFetched: Long,
    ): Int =
        transaction {
            MangaTable
                .insertAndGetId {
                    it[MangaTable.url] = url
                    it[title] = "Original"
                    it[artist] = "Original artist"
                    it[author] = "Original author"
                    it[description] = "Original description"
                    it[genre] = "Original genre"
                    it[status] = SManga.COMPLETED
                    it[memo] = buildJsonObject { put("key", "original") }
                    it[MangaTable.sourceReference] = sourceId
                    it[thumbnail_url] = thumbnailUrl
                    it[MangaTable.thumbnailUrlVersion] = thumbnailUrlVersion
                    it[MangaTable.thumbnailUrlLastFetched] = thumbnailUrlLastFetched
                }.value
        }.also(createdMangaIds::add)

    private fun browseEntry(
        url: String,
        thumbnailUrl: String?,
        variant: String? = null,
        updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
        status: Int = SManga.ONGOING,
    ): SManga =
        SManga.create().apply {
            val suffix = variant?.let { " $it" }.orEmpty()
            val memoValue = "updated" + (variant?.let { "-$it" } ?: "")
            this.url = url
            title = "Updated$suffix"
            artist = "Updated artist$suffix"
            author = "Updated author$suffix"
            description = "Updated description$suffix"
            genre = "Updated genre$suffix"
            this.status = status
            memo = buildJsonObject { put("key", memoValue) }
            thumbnail_url = thumbnailUrl
            update_strategy = updateStrategy
        }

    private fun checkBrowseBatchShape(
        sourceId: Long,
        name: String,
        coverChanged: List<Boolean>,
    ) {
        val statuses = listOf(SManga.ONGOING, SManga.COMPLETED, SManga.ON_HIATUS, SManga.CANCELLED)
        val fixtures =
            coverChanged.mapIndexed { index, changed ->
                val url = "/browse-$name-$index"
                val thumbnailUrl = "https://covers.test/$name-$index.png"
                val id = insertBrowseManga(sourceId, url, thumbnailUrl, 100L + index, index + 1L)
                Fixture(
                    id,
                    url,
                    thumbnailUrl,
                    100L + index,
                    index + 1L,
                    changed,
                    "$name-$index",
                    statuses[index % statuses.size],
                    if (index % 2 == 0) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE,
                )
            }
        val entries =
            fixtures.map { fixture ->
                browseEntry(
                    fixture.url,
                    if (fixture.changed) "${fixture.thumbnailUrl}?new" else fixture.thumbnailUrl,
                    variant = fixture.variant,
                    updateStrategy = fixture.updateStrategy,
                    status = fixture.status,
                )
            }

        MangasPage(entries, false).insertOrUpdate(sourceId)

        val rows =
            transaction {
                MangaTable
                    .selectAll()
                    .where { MangaTable.id inList fixtures.map { it.id } }
                    .associateBy { it[MangaTable.id].value }
            }
        fixtures.forEach { fixture ->
            val row = rows.getValue(fixture.id)
            val suffix = " ${fixture.variant}"
            assertEquals("Updated$suffix", row[MangaTable.title])
            assertEquals("Updated artist$suffix", row[MangaTable.artist])
            assertEquals("Updated author$suffix", row[MangaTable.author])
            assertEquals("Updated description$suffix", row[MangaTable.description])
            assertEquals("Updated genre$suffix", row[MangaTable.genre])
            assertEquals(fixture.status, row[MangaTable.status])
            assertEquals(fixture.updateStrategy.name, row[MangaTable.updateStrategy])
            assertEquals("updated-${fixture.variant}", row[MangaTable.memo].getValue("key").jsonPrimitive.content)
            if (fixture.changed) {
                assertEquals("${fixture.thumbnailUrl}?new", row[MangaTable.thumbnail_url])
                assertTrue(row[MangaTable.thumbnailUrlVersion] > fixture.version)
                assertTrue(row[MangaTable.thumbnailUrlLastFetched] > fixture.lastFetched)
            } else {
                assertEquals(fixture.thumbnailUrl, row[MangaTable.thumbnail_url])
                assertEquals(fixture.version, row[MangaTable.thumbnailUrlVersion])
                assertEquals(fixture.lastFetched, row[MangaTable.thumbnailUrlLastFetched])
            }
        }
    }

    private data class Fixture(
        val id: Int,
        val url: String,
        val thumbnailUrl: String,
        val version: Long,
        val lastFetched: Long,
        val changed: Boolean,
        val variant: String,
        val status: Int,
        val updateStrategy: UpdateStrategy,
    )

    private fun checkBrowse(
        memberships: String,
        local: Boolean,
        expectedTitle: String,
    ) {
        val sourceId = if (local) LocalSource.ID else 123L
        val url = "/browse-$memberships-$local"
        val mangaId =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[MangaTable.url] = url
                        it[title] = "Original"
                        it[sourceReference] = sourceId
                        it[author] = "Existing author"
                    }.value
            }
        if (memberships != "none") {
            memberships.split(":").forEachIndexed { index, membership ->
                val userId = createTestUser("browse-$memberships-$local-$index")
                transaction {
                    MangaUserTable.insert {
                        it[manga] = mangaId
                        it[user] = userId
                        it[inLibrary] = membership.toBoolean()
                    }
                }
            }
        }
        val entry =
            SManga.create().apply {
                this.url = url
                title = "Updated"
            }

        assertEquals(listOf(mangaId), MangasPage(listOf(entry), false).insertOrUpdate(sourceId))
        transaction {
            val manga = MangaTable.selectAll().where { MangaTable.id eq mangaId }.single()
            assertEquals(expectedTitle, manga[MangaTable.title])
            assertEquals("Existing author", manga[MangaTable.author])
        }
    }

    @AfterEach
    fun tearDown() {
        createdMangaIds.forEach { Manga.clearThumbnail(it) }
        createdMangaIds.clear()
        clearTables(
            MangaUserTable,
            MangaTable,
        )
    }
}
