package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.dao.id.IdTable

object SourceTable : IdTable<Long>() {
    override val id = long("id").entityId()
    val name = varchar("name", 128)
    val lang = varchar("lang", 32)
    val extension = reference("extension", ExtensionTable)
    val contentWarning = integer("content_warning").default(0)
}
