package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.Migration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

class M0010_MangaAndChapterMeta : Migration() {
    private class ChapterMetaTable : IntIdTable() {
        val key = varchar("key", 256)
        val value = varchar("value", 4096)
        val ref = reference("chapter_ref", ChapterTable, ReferenceOption.CASCADE)
    }
    private class MangaMetaTable : IntIdTable() {
        val key = varchar("key", 256)
        val value = varchar("value", 4096)
        val ref = reference("manga_ref", MangaTable, ReferenceOption.CASCADE)
    }

    override fun run() {
        transaction {
            SchemaUtils.create(
                ChapterMetaTable(),
                MangaMetaTable()
            )
        }
    }
}
