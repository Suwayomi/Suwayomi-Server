package suwayomi.server.database.migration

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.currentDialect
import suwayomi.server.database.migration.lib.Migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

class M0007_AddPageOffset : Migration() {
    override fun run() {
        transaction {
            with(TransactionManager.current()) {
                exec("ALTER TABLE CHAPTER ADD COLUMN LAST_PAGE_READ_OFFSET INTEGER DEFAULT 0")
                commit()
                currentDialect.resetCaches()
            }
        }
    }
}
