package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MangaTest : ApplicationTest() {
    private val applicationDirs: ApplicationDirs by injectLazy()

    private val originalThumbnailUrl = "https://example.com/cover-original.jpg"

    private fun createMangaWithCachedCovers(): Pair<Int, List<File>> {
        val mangaId = createLibraryManga("COVER_TEST")
        transaction {
            MangaTable.update({ MangaTable.id eq mangaId }) {
                it[thumbnail_url] = originalThumbnailUrl
                it[thumbnailUrlLastFetched] = 100L
            }
        }

        val coverFiles =
            listOf(applicationDirs.tempThumbnailCacheRoot, applicationDirs.thumbnailDownloadsRoot).map { dir ->
                File(dir).mkdirs()
                File(dir, "$mangaId.jpg").apply { writeText("cover") }
            }
        return mangaId to coverFiles
    }

    private fun updateMangaDatabaseWith(
        mangaId: Int,
        remoteThumbnailUrl: String?,
    ) {
        val mangaEntry = getStoredManga(mangaId)
        val remoteManga =
            SManga.create().apply {
                title = "COVER_TEST"
                url = "COVER_TEST"
                thumbnail_url = remoteThumbnailUrl
            }

        runBlocking { Manga.updateMangaDatabase(mangaEntry, mockk<Source>(relaxed = true), remoteManga) }
    }

    private fun getStoredManga(mangaId: Int) = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }

    private fun assertCoversKept(coverFiles: List<File>) {
        coverFiles.forEach { assertTrue(it.exists(), "Cover ${it.path} should be kept") }
        coverFiles.forEach { assertEquals("cover", it.readText()) }
    }

    @Test
    fun updateMangaDatabaseKeepsCoversAndRegistersTheFetchWhenThumbnailUrlIsUnchanged() {
        val (mangaId, coverFiles) = createMangaWithCachedCovers()

        updateMangaDatabaseWith(mangaId, originalThumbnailUrl)

        assertCoversKept(coverFiles)
        val storedManga = getStoredManga(mangaId)
        assertEquals(originalThumbnailUrl, storedManga[MangaTable.thumbnail_url])
        assertTrue(storedManga[MangaTable.thumbnailUrlLastFetched] > 100L)
    }

    @Test
    fun updateMangaDatabaseKeepsCoversAndStoresTheNewUrlWhenThumbnailUrlChanged() {
        val (mangaId, coverFiles) = createMangaWithCachedCovers()
        val newThumbnailUrl = "https://example.com/cover-new.jpg"

        updateMangaDatabaseWith(mangaId, newThumbnailUrl)

        assertCoversKept(coverFiles)
        val storedManga = getStoredManga(mangaId)
        assertEquals(newThumbnailUrl, storedManga[MangaTable.thumbnail_url])
        assertTrue(storedManga[MangaTable.thumbnailUrlLastFetched] > 100L)
    }

    @Test
    fun updateMangaDatabaseKeepsCoversAndUrlWhenRemoteThumbnailUrlIsNull() {
        val (mangaId, coverFiles) = createMangaWithCachedCovers()

        updateMangaDatabaseWith(mangaId, null)

        assertCoversKept(coverFiles)
        val storedManga = getStoredManga(mangaId)
        assertEquals(originalThumbnailUrl, storedManga[MangaTable.thumbnail_url])
        assertEquals(100L, storedManga[MangaTable.thumbnailUrlLastFetched])
    }

    @Test
    fun updateMangaDatabaseKeepsCoversAndUrlWhenRemoteThumbnailUrlIsEmpty() {
        val (mangaId, coverFiles) = createMangaWithCachedCovers()

        updateMangaDatabaseWith(mangaId, "")

        assertCoversKept(coverFiles)
        val storedManga = getStoredManga(mangaId)
        assertEquals(originalThumbnailUrl, storedManga[MangaTable.thumbnail_url])
        assertEquals(100L, storedManga[MangaTable.thumbnailUrlLastFetched])
    }

    @Test
    fun getMangaMeta() {
        val metaManga = createLibraryManga("META_TEST")
        val emptyMeta = Manga.getMangaMetaMap(1, metaManga).size
        assertEquals(0, emptyMeta, "Default Manga meta should be empty at start")

        Manga.modifyMangaMeta(1, metaManga, "test", "value")
        assertEquals(1, Manga.getMangaMetaMap(1, metaManga).size, "Manga meta should have one member")
        assertEquals("value", Manga.getMangaMetaMap(1, metaManga)["test"], "Manga meta use the value 'value' for key 'test'")

        Manga.modifyMangaMeta(1, metaManga, "test", "newValue")
        assertEquals(
            1,
            Manga.getMangaMetaMap(1, metaManga).size,
            "Manga meta should still only have one pair",
        )
        assertEquals(
            "newValue",
            Manga.getMangaMetaMap(1, metaManga)["test"],
            "Manga meta with key 'test' should use the value `newValue`",
        )

        Manga.modifyMangaMeta(1, metaManga, "test2", "value2")
        assertEquals(
            2,
            Manga.getMangaMetaMap(1, metaManga).size,
            "Manga Meta should have an additional pair",
        )
        assertEquals(
            "value2",
            Manga.getMangaMetaMap(1, metaManga)["test2"],
            "Manga Meta for key 'test2' should be 'value2'",
        )
    }

    @AfterEach
    internal fun tearDown() {
        File(applicationDirs.tempThumbnailCacheRoot).deleteRecursively()
        File(applicationDirs.thumbnailDownloadsRoot).deleteRecursively()
        clearTables(
            MangaMetaTable,
            MangaTable,
        )
    }
}
