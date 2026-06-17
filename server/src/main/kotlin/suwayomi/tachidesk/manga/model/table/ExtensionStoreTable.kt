package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object ExtensionStoreTable : IntIdTable() {
    val indexUrl = varchar("index_url", 2048)
    val name = varchar("name", 256)
    val badgeLabel = varchar("badge_label", 32)
    val signingKey = varchar("signing_key", 512)
    val contactWebsite = varchar("contact_website", 2048)
    val contactDiscord = varchar("contact_discord", 2048).nullable()
    val isLegacy = bool("is_legacy").default(false)
}
