/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import java.util.concurrent.CompletableFuture

class SourceType(
    val id: Long,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val isConfigurable: Boolean,
    val isNsfw: Boolean,
    val displayName: String
) {
    constructor(source: SourceDataClass) : this(
        id = source.id.toLong(),
        name = source.name,
        lang = source.lang,
        iconUrl = source.iconUrl,
        supportsLatest = source.supportsLatest,
        isConfigurable = source.isConfigurable,
        isNsfw = source.isNsfw,
        displayName = source.displayName
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<MangaType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, List<MangaType>>("MangaForSourceDataLoader", id)
    }
}
