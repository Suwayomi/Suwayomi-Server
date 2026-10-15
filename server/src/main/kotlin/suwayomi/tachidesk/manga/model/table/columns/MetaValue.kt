package suwayomi.tachidesk.manga.model.table.columns

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.BadRequestResponse

/**
 * Shared width contract: every `*META` table (MangaMetaTable, SourceMetaTable,
 * CategoryMetaTable, ChapterMetaTable, GlobalMetaTable) declares `value` as
 * `varchar(4096)`.
 */
const val META_VALUE_MAX_LENGTH = 4096

/**
 * Rejects a meta value that would overflow `varchar(4096)`. Writing it anyway would end in
 * an opaque SQL data-truncation error, so user-facing meta write points validate up front
 * and answer 400 instead.
 */
fun validateMetaValueLength(value: String) {
    if (value.length > META_VALUE_MAX_LENGTH) {
        throw BadRequestResponse("Meta value exceeds the $META_VALUE_MAX_LENGTH character limit (${value.length})")
    }
}
