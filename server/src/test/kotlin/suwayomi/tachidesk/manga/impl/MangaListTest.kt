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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import kotlin.test.Test
import kotlin.test.assertEquals

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
