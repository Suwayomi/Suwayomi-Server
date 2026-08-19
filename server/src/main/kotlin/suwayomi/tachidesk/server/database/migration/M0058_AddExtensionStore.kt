package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

@Suppress("ClassName", "unused")
class M0058_AddExtensionStore : AddTableMigration() {
    private class ExtensionStoreTable : IntIdTable() {
        val indexUrl = varchar("index_url", 2048).uniqueIndex()
        val name = varchar("name", 256)
        val badgeLabel = varchar("badge_label", 32)
        val signingKey = varchar("signing_key", 512)
        val contactWebsite = varchar("contact_website", 2048)
        val contactDiscord = varchar("contact_discord", 2048).nullable()
        val isLegacy = bool("is_legacy").default(false)
        val extensionListUrl = varchar("extension_list_url", 2048).nullable()
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                ExtensionStoreTable(),
            )
}
