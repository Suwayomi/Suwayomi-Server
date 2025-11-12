/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import org.jetbrains.exposed.sql.ResultRow
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension
import suwayomi.tachidesk.manga.model.dataclass.IReaderExtensionDataClass
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable

@GraphQLDescription("Represents an IReader extension")
data class IReaderExtensionType(
    val repo: String?,
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
    val obsolete: Boolean,
) {
    constructor(dataClass: IReaderExtensionDataClass) : this(
        repo = dataClass.repo,
        apkName = dataClass.apkName,
        iconUrl = dataClass.iconUrl,
        name = dataClass.name,
        pkgName = dataClass.pkgName,
        versionName = dataClass.versionName,
        versionCode = dataClass.versionCode,
        lang = dataClass.lang,
        isNsfw = dataClass.isNsfw,
        installed = dataClass.installed,
        hasUpdate = dataClass.hasUpdate,
        obsolete = dataClass.obsolete,
    )

    companion object {
        fun fromResultRow(row: ResultRow): IReaderExtensionType =
            IReaderExtensionType(
                repo = row[IReaderExtensionTable.repo],
                apkName = row[IReaderExtensionTable.apkName],
                iconUrl = IReaderExtension.getExtensionIconUrl(row[IReaderExtensionTable.apkName]),
                name = row[IReaderExtensionTable.name],
                pkgName = row[IReaderExtensionTable.pkgName],
                versionName = row[IReaderExtensionTable.versionName],
                versionCode = row[IReaderExtensionTable.versionCode],
                lang = row[IReaderExtensionTable.lang],
                isNsfw = row[IReaderExtensionTable.isNsfw],
                installed = row[IReaderExtensionTable.isInstalled],
                hasUpdate = row[IReaderExtensionTable.hasUpdate],
                obsolete = row[IReaderExtensionTable.isObsolete],
            )
    }
}
