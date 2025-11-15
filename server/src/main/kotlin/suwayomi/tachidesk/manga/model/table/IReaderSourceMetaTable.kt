package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable

object IReaderSourceMetaTable : IntIdTable() {
    val ref = long("source_ref").references(IReaderSourceTable.id)
    val key = varchar("key", 256)
    val value = varchar("value", 4096)
}
