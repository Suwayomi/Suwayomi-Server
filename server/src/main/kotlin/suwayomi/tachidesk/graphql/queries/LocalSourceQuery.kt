/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.LocalSourceTools

class LocalSourceQuery {
    data class LocalSourceEntryType(
        val name: String,
        val type: String,
        val sizeBytes: Long,
        val itemCount: Int,
        val lastModified: Long,
    )

    @RequireAuth
    fun localSourceEntries(): List<LocalSourceEntryType> =
        LocalSourceTools.listEntries().map {
            LocalSourceEntryType(
                name = it.name,
                type = it.type,
                sizeBytes = it.sizeBytes,
                itemCount = it.itemCount,
                lastModified = it.lastModified,
            )
        }
}
