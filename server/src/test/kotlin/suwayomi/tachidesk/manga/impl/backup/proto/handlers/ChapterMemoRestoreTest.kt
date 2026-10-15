package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.SyncRestoreMode
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U3-N1: chapter-level memos go through the same defensive [decodeMemo] path
 * as the manga-level memo — non-JSON wire bytes must land as an empty memo
 * instead of throwing inside the chapter batchInsert and failing the whole
 * manga into `errors`.
 */
class ChapterMemoRestoreTest : ApplicationTest() {
    private val flags =
        BackupFlags(
            includeManga = true,
            includeCategories = false,
            includeChapters = true,
            includeTracking = false,
            includeHistory = false,
            includeClientData = false,
            includeServerSettings = false,
            includeUserSettings = false,
        )

    @AfterEach
    fun tearDown() {
        clearTables(
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }

    @Test
    fun `non-json chapter memo bytes restore as an empty memo`() {
        val backup =
            BackupManga(
                source = 1,
                url = "memo_chapter_manga",
                title = "memo_chapter_manga",
                chapters =
                    listOf(
                        BackupChapter(url = "1", name = "1", memo = "not-json{".encodeToByteArray()),
                        BackupChapter(url = "2", name = "2", memo = """{"note":"hi"}""".encodeToByteArray()),
                    ),
            )

        val errors = mutableListOf<Pair<Date, String>>()
        BackupMangaHandler.restore(1, backup, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.NONE)

        assertTrue(errors.isEmpty(), errors.joinToString())

        val chaptersByUrl =
            transaction {
                val mangaId =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.url eq "memo_chapter_manga" }
                        .single()[MangaTable.id]
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .associate { it[ChapterTable.url] to it[ChapterTable.memo] }
            }
        assertEquals(JsonObject.EMPTY, chaptersByUrl["1"])
        assertEquals(JsonObject(mapOf("note" to JsonPrimitive("hi"))), chaptersByUrl["2"])
    }
}
