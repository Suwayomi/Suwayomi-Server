/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
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

    constructor(row: ResultRow, sourceExtension: ResultRow, catalogueSource: CatalogueSource) : this(
        id = row[SourceTable.id].value,
        name = row[SourceTable.name],
        lang = row[SourceTable.lang],
        iconUrl = Extension.getExtensionIconUrl(sourceExtension[ExtensionTable.apkName]),
        supportsLatest = catalogueSource.supportsLatest,
        isConfigurable = catalogueSource is ConfigurableSource,
        isNsfw = row[SourceTable.isNsfw],
        displayName = catalogueSource.toString()
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<MangaType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Long, List<MangaType>>("MangaForSourceDataLoader", id)
    }
}

fun SourceType(row: ResultRow): SourceType? {
    val catalogueSource = GetCatalogueSource
        .getCatalogueSourceOrNull(row[SourceTable.id].value)
        ?: return null
    val sourceExtension = ExtensionTable
        .select { ExtensionTable.id eq row[SourceTable.extension] }
        .first()
    return SourceType(row, sourceExtension, catalogueSource)
}
