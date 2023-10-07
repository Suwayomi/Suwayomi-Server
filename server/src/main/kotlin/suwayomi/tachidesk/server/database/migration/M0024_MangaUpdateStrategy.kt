package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddColumnMigration
import eu.kanade.tachiyomi.source.model.UpdateStrategy

@Suppress("ClassName", "unused")
class M0024_MangaUpdateStrategy : AddColumnMigration(
    "Manga",
    "update_strategy",
    "VARCHAR(256)",
    "'${UpdateStrategy.ALWAYS_UPDATE.name}'",
)
