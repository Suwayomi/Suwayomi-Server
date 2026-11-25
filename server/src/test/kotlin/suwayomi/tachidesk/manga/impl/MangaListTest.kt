package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MangaListTest : GraphQLTest() {
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

    @Test
    fun latestOnASourceWithoutLatestSupportIsABadRequest() =
        runTest {
            val sourceId = 424242L
            val source =
                mockk<Source> {
                    every { supportsLatest } returns false
                }
            GetSource.registerSource(sourceId to source)
            try {
                val e =
                    assertFailsWith<BadRequestResponse> {
                        MangaList.getMangaList(sourceId, pageNum = 1, popular = false)
                    }
                assertEquals("Source does not support latest", e.message)
                coVerify(exactly = 0) { source.getLatestUpdates(any()) }
            } finally {
                GetSource.unregisterSource(sourceId)
            }
        }

    @Test
    fun latestOnAMissingSourceIsNotFound() =
        runTest {
            assertFailsWith<NotFoundResponse> {
                MangaList.getMangaList(987654321L, pageNum = 1, popular = false)
            }
        }

    @Test
    fun latestOnAStubSourceIsNotFound() =
        runTest {
            val sourceId = 424243L
            GetSource.registerSource(sourceId to StubSource(sourceId))
            try {
                assertFailsWith<NotFoundResponse> {
                    MangaList.getMangaList(sourceId, pageNum = 1, popular = false)
                }
            } finally {
                GetSource.unregisterSource(sourceId)
            }
        }

    @Test
    fun latestOnASourceThatFailsToLoadIsAnIllegalState() =
        runTest {
            val sourceId = 424244L
            val extensionId =
                transaction {
                    ExtensionTable.insertAndGetId {
                        it[apkName] = "broken.apk"
                        it[name] = "broken"
                        it[pkgName] = "broken"
                        it[versionName] = "1.0"
                        it[versionCode] = 1
                        it[lang] = "en"
                        it[contentWarning] = 0
                        it[extensionLib] = "1.5"
                        it[classFQName] = "broken.Extension"
                    }
                }
            transaction {
                SourceTable.insert {
                    it[id] = EntityID(sourceId, SourceTable)
                    it[name] = "broken"
                    it[lang] = "en"
                    it[extension] = extensionId
                }
            }
            try {
                // IllegalStateException has no dedicated Javalin handler, so the
                // broken source lands on the generic 500 — never 404 or 400
                val e =
                    assertFailsWith<IllegalStateException> {
                        MangaList.getMangaList(sourceId, pageNum = 1, popular = false)
                    }
                assertEquals("Source $sourceId failed to load", e.message)
                assertNotNull(e.cause)
            } finally {
                GetSource.unregisterSource(sourceId)
                transaction {
                    SourceTable.deleteWhere { SourceTable.id eq sourceId }
                    ExtensionTable.deleteWhere { ExtensionTable.id eq extensionId }
                }
            }
        }

    @Test
    fun latestOnAnExtensionWithoutAnApkIsALoadFailure() =
        runTest {
            val sourceId = 424246L
            val extensionId =
                transaction {
                    ExtensionTable.insertAndGetId {
                        it[apkName] = null
                        it[name] = "noapk"
                        it[pkgName] = "noapk"
                        it[versionName] = "1.0"
                        it[versionCode] = 1
                        it[lang] = "en"
                        it[contentWarning] = 0
                        it[extensionLib] = "1.5"
                        it[classFQName] = "noapk.Extension"
                    }
                }
            transaction {
                SourceTable.insert {
                    it[id] = EntityID(sourceId, SourceTable)
                    it[name] = "noapk"
                    it[lang] = "en"
                    it[extension] = extensionId
                }
            }
            try {
                // the missing-apkName NPE must stay a load failure, not collapse into 404
                val e =
                    assertFailsWith<IllegalStateException> {
                        MangaList.getMangaList(sourceId, pageNum = 1, popular = false)
                    }
                assertEquals("Source $sourceId failed to load", e.message)
                assertIs<NullPointerException>(e.cause)
            } finally {
                GetSource.unregisterSource(sourceId)
                transaction {
                    SourceTable.deleteWhere { SourceTable.id eq sourceId }
                    ExtensionTable.deleteWhere { ExtensionTable.id eq extensionId }
                }
            }
        }

    @Test
    fun latestOnASupportingSourceSucceeds() =
        runTest {
            val sourceId = 424245L
            val entry =
                SManga.create().apply {
                    url = "/latest-manga"
                    title = "Latest"
                }
            val source =
                mockk<Source> {
                    every { supportsLatest } returns true
                    coEvery { getLatestUpdates(1) } returns MangasPage(listOf(entry), false)
                }
            GetSource.registerSource(sourceId to source)
            try {
                val result = MangaList.getMangaList(sourceId, pageNum = 1, popular = false)
                assertEquals(listOf("Latest"), result.mangaList.map { it.title })
                coVerify(exactly = 1) { source.getLatestUpdates(1) }
            } finally {
                GetSource.unregisterSource(sourceId)
            }
        }

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
        clearTables(
            MangaUserTable,
            MangaTable,
        )
    }
}
