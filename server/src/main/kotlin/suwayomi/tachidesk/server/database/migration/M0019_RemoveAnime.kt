@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0019_RemoveAnime : SQLMigration() {
    val Anime = "ANIME"
    val AnimeExtension = "ANIMEEXTENSION"
    val AnimeSource = "ANIMESOURCE"
    val Episode = "EPISODE"

    override val sql =
        """
        DROP TABLE $AnimeSource;
        DROP TABLE $AnimeExtension;
        DROP TABLE $Episode;
        DROP TABLE $Anime;
        """.trimIndent()
}
