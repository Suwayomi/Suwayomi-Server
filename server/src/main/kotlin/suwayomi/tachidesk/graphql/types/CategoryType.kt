/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.model.table.CategoryTable
import java.util.concurrent.CompletableFuture

class CategoryType(
    val id: Int,
    val order: Int,
    val name: String,
    val default: Boolean
) {
    constructor(row: ResultRow) : this(
        row[CategoryTable.id].value,
        row[CategoryTable.order],
        row[CategoryTable.name],
        row[CategoryTable.isDefault]
    )

    fun manga(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<MangaType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, List<MangaType>>("MangaForCategoryDataLoader", id)
    }

    fun meta(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<MetaType> {
        return dataFetchingEnvironment.getValueFromDataLoader<Int, MetaType>("CategoryMetaDataLoader", id)
    }
}
