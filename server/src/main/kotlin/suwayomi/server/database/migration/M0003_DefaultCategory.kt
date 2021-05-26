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
class M0003_DefaultCategory : Migration() {
    /** this migration renamed CategoryTable.IS_LANDING to ChapterTable.IS_DEFAULT */
    override fun run() {
        with(TransactionManager.current()) {
            exec("ALTER TABLE CATEGORY ALTER COLUMN IS_LANDING RENAME TO IS_DEFAULT")
            commit()
            currentDialect.resetCaches()
        }
    }
}
