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
class M0007_ChapterIsDownloaded : Migration() {
    /** this migration added IS_DOWNLOADED to CHAPTER */
    override fun run() {
        with(TransactionManager.current()) {
            exec("ALTER TABLE CHAPTER ADD COLUMN IS_DOWNLOADED BOOLEAN DEFAULT FALSE")
            commit()
            currentDialect.resetCaches()
        }
    }
}
