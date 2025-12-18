package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

@Suppress("ClassName", "unused")
class M0055_IReaderChapterTable : AddTableMigration() {
    private object IReaderNovelTable : IntIdTable()

    private class IReaderChapterTable : IntIdTable() {
        val url = varchar("url", 2048)
        val name = varchar("name", 512)
        val dateUpload = long("date_upload").default(0)
        val chapterNumber = float("chapter_number").default(-1f)
        val scanlator = varchar("scanlator", 256).nullable()

        val isRead = bool("read").default(false)
        val isBookmarked = bool("bookmark").default(false)
        val lastPageRead = integer("last_page_read").default(0)
        val lastReadAt = long("last_read_at").default(0)
        val fetchedAt = long("fetched_at").default(0)

        val sourceOrder = integer("source_order")

        val novel = reference("novel", IReaderNovelTable, ReferenceOption.CASCADE)
    }

    override val tables: Array<Table>
        get() = arrayOf(IReaderChapterTable())
}
