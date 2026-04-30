package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object KindleSendQueueTable : IntIdTable() {
    val chapterRef = reference("chapter_ref", ChapterTable, ReferenceOption.CASCADE)

    /** PENDING | SENDING | SENT | FAILED | TOO_LARGE */
    val status = varchar("status", 16).default("PENDING")

    val attempts = integer("attempts").default(0)

    /** Manual / auto trigger source. */
    val triggerSource = varchar("trigger_source", 16).default("AUTO")

    /** Optional override of the destination address (defaults to global kindleEmail). */
    val destination = varchar("destination", 320).nullable()

    val lastError = varchar("last_error", 1024).nullable()

    val enqueuedAt = long("enqueued_at")
    val lastAttemptAt = long("last_attempt_at").nullable()

    /** When the worker is allowed to attempt this row again (epoch millis). */
    val nextAttemptAt = long("next_attempt_at")
}
