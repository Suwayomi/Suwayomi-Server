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
import suwayomi.tachidesk.manga.model.table.ChapterTable

@Suppress("ClassName", "unused")
class M0057_KindleSendQueue : AddTableMigration() {
    private class KindleSendQueueTable : IntIdTable() {
        val chapterRef = reference("chapter_ref", ChapterTable, ReferenceOption.CASCADE)
        val status = varchar("status", 16).default("PENDING")
        val attempts = integer("attempts").default(0)
        val triggerSource = varchar("trigger_source", 16).default("AUTO")
        val destination = varchar("destination", 320).nullable()
        val lastError = varchar("last_error", 1024).nullable()
        val enqueuedAt = long("enqueued_at")
        val lastAttemptAt = long("last_attempt_at").nullable()
        val nextAttemptAt = long("next_attempt_at")
    }

    override val tables: Array<Table>
        get() = arrayOf(KindleSendQueueTable())
}
