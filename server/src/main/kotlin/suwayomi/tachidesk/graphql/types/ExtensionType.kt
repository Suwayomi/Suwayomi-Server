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
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import java.util.concurrent.CompletableFuture

class ExtensionType(
    val apkName: String,
    val iconUrl: String,

    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val isNsfw: Boolean,

    val installed: Boolean,
    val hasUpdate: Boolean,
    val obsolete: Boolean
) {
    constructor(row: ResultRow) : this(
        apkName = row[ExtensionTable.apkName],
        iconUrl = row[ExtensionTable.iconUrl],
        name = row[ExtensionTable.name],
        pkgName = row[ExtensionTable.pkgName],
        versionName = row[ExtensionTable.versionName],
        versionCode = row[ExtensionTable.versionCode],
        lang = row[ExtensionTable.lang],
        isNsfw = row[ExtensionTable.isNsfw],
        installed = row[ExtensionTable.isInstalled],
        hasUpdate = row[ExtensionTable.hasUpdate],
        obsolete = row[ExtensionTable.isObsolete]
    )

    fun source(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<SourceType>> {
        return dataFetchingEnvironment.getValueFromDataLoader<String, List<SourceType>>("SourcesForExtensionDataLoader", pkgName)
    }
}
