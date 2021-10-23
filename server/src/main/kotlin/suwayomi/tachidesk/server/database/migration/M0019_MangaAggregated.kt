package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0019_MangaAggregated : SQLMigration() {
    override val sql = """
        CREATE VIEW "MANGA_AGGREGATED"
                    ("CATEGORY", "ID", "URL", "TITLE", "INITIALIZED", "ARTIST", "AUTHOR", "DESCRIPTION", "GENRE", "STATUS",
                     "THUMBNAIL_URL", "IN_LIBRARY", "DEFAULT_CATEGORY", "SOURCE", "REAL_URL", "IN_LIBRARY_AT", "UNREAD_COUNT",
                     "DOWNLOAD_COUNT")
        AS
        SELECT "C"."CATEGORY",
               "M"."ID",
               "M"."URL",
               "M"."TITLE",
               "M"."INITIALIZED",
               "M"."ARTIST",
               "M"."AUTHOR",
               "M"."DESCRIPTION",
               "M"."GENRE",
               "M"."STATUS",
               "M"."THUMBNAIL_URL",
               "M"."IN_LIBRARY",
               "M"."DEFAULT_CATEGORY",
               "M"."SOURCE",
               "M"."REAL_URL",
               "M"."IN_LIBRARY_AT",
               COUNT("C2"."ID") AS "UNREAD_COUNT",
               COUNT("C3"."ID") AS "DOWNLOAD_COUNT"
        FROM "PUBLIC"."MANGA" "M"
                 LEFT OUTER JOIN "PUBLIC"."CATEGORYMANGA" "C"
                                 ON "M"."ID" = "C"."MANGA"
                 LEFT OUTER JOIN "PUBLIC"."CHAPTER" "C2"
                                 ON ("C2"."READ" = FALSE)
                                     AND ("M"."ID" = "C2"."MANGA")
                 LEFT OUTER JOIN "PUBLIC"."CHAPTER" "C3"
                                 ON ("C2"."IS_DOWNLOADED" = TRUE)
                                     AND ("M"."ID" = "C2"."MANGA")
        GROUP BY "M"."ID", "CATEGORY";
    """.trimIndent()
}
