package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar

object IReaderChapterTable : IntIdTable() {
    val url = varchar("url", 2048)
    val name = truncatingVarchar("name", 512)
    val dateUpload = long("date_upload").default(0)
    val chapterNumber = float("chapter_number").default(-1f)
    val scanlator = truncatingVarchar("scanlator", 256).nullable()

    val isRead = bool("read").default(false)
    val isBookmarked = bool("bookmark").default(false)
    val lastPageRead = integer("last_page_read").default(0)
    val lastReadAt = long("last_read_at").default(0)
    val fetchedAt = long("fetched_at").default(0)

    val sourceOrder = integer("source_order")

    val novel = reference("novel", IReaderNovelTable, ReferenceOption.CASCADE)
}
