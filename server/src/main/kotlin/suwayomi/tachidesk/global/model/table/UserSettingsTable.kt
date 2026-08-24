package suwayomi.tachidesk.global.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Per-user setting overrides. One row per (user, overridden setting). The value is a JSON-encoded string.
 *
 * Settings without a row here fall back to the global [ServerConfig] value.
 */
object UserSettingsTable : Table() {
    val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
    val key = varchar("key", 256)
    val value = varchar("value", 16384)
}
