package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.Migration
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect

@Suppress("ClassName", "unused")
class M0009_ChapterLastReadAt : Migration() {
    /** this migration added PAGE_COUNT to CHAPTER */
    override fun run() {
        with(TransactionManager.current()) {
            // BIGINT == Long
            exec("ALTER TABLE CHAPTER ADD COLUMN LAST_READ_AT BIGINT DEFAULT 0")
            commit()
            currentDialect.resetCaches()
        }
    }
}
