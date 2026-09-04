package suwayomi.tachidesk.global.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

/**
 * One-time codes issued by admins: `RECOVERY` codes are bound to an existing user,
 * `REGISTRATION` codes are unbound. Codes are stored bcrypt-hashed and handed to the
 * user in plaintext exactly once.
 */
object UserCodeTable : IntIdTable() {
    val user = reference("user_id", UserAccountTable.id, ReferenceOption.CASCADE).nullable()
    val type = varchar("type", 32)
    val codeHash = varchar("code_hash", 90)
    val createdBy = integer("created_by").references(UserAccountTable.id)
    val createdAt = long("created_at").default(0)
    val expiresAt = long("expires_at").default(0)
    val consumedAt = long("consumed_at").nullable()

    init {
        index(isUnique = false, user, type, consumedAt)
    }
}
