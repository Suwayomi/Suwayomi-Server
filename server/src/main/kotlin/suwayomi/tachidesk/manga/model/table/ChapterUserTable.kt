package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.leftJoin
import suwayomi.tachidesk.global.model.table.UserAccountTable

object ChapterUserTable : IntIdTable() {
    val chapter = reference("chapter", ChapterTable, ReferenceOption.CASCADE)
    val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)

    val isRead = bool("read").default(false)
    val isBookmarked = bool("bookmark").default(false)
    val lastPageRead = integer("last_page_read").default(0)
    val lastReadAt = long("last_read_at").default(0)
}

fun ChapterTable.getWithUserData(userId: Int) =
    leftJoin(
        ChapterUserTable,
        onColumn = { ChapterTable.id },
        otherColumn = { ChapterUserTable.chapter },
        additionalConstraint = { ChapterUserTable.user eq userId },
    )
