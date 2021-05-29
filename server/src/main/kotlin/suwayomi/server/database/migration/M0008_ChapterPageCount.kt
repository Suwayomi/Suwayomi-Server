package suwayomi.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import suwayomi.server.database.migration.lib.Migration

@Suppress("ClassName", "unused")
class M0008_ChapterPageCount : Migration() {
    /** this migration added PAGE_COUNT to CHAPTER */
    override fun run() {
        with(TransactionManager.current()) {
            exec("ALTER TABLE CHAPTER ADD COLUMN PAGE_COUNT INT DEFAULT -1")
            commit()
            currentDialect.resetCaches()
        }
    }
}
