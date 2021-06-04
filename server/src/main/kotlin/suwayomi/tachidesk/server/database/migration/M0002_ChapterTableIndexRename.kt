package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import suwayomi.tachidesk.server.database.migration.lib.Migration

@Suppress("ClassName", "unused")
class M0002_ChapterTableIndexRename : Migration() {
    /** this migration renamed ChapterTable.NUMBER_IN_LIST to ChapterTable.INDEX */
    override fun run() {
        with(TransactionManager.current()) {
            exec("ALTER TABLE CHAPTER ALTER COLUMN NUMBER_IN_LIST RENAME TO INDEX")
            commit()
            currentDialect.resetCaches()
        }
    }
}
