package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IdTable

object IReaderSourceTable : IdTable<Long>() {
    override val id = long("id").entityId()
    override val primaryKey = PrimaryKey(id)
    val name = varchar("name", 128)
    val lang = varchar("lang", 32)
    val extension = reference("extension", IReaderExtensionTable)
    val isNsfw = bool("is_nsfw").default(false)
}
