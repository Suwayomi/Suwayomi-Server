package suwayomi.tachidesk.manga.impl.extension.ireader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.model.dataclass.IReaderExtensionDataClass
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable

object IReaderExtensionsList {
    private val logger = KotlinLogging.logger {}

    val updateMap = mutableMapOf<String, IReaderExtensionDataClass>()

    fun extensionTableAsDataClass(): List<IReaderExtensionDataClass> {
        return transaction {
            IReaderExtensionTable.selectAll().map {
                IReaderExtensionDataClass(
                    repo = it[IReaderExtensionTable.repo],
                    apkName = it[IReaderExtensionTable.apkName],
                    iconUrl = it[IReaderExtensionTable.iconUrl],
                    name = it[IReaderExtensionTable.name],
                    pkgName = it[IReaderExtensionTable.pkgName],
                    versionName = it[IReaderExtensionTable.versionName],
                    versionCode = it[IReaderExtensionTable.versionCode],
                    lang = it[IReaderExtensionTable.lang],
                    isNsfw = it[IReaderExtensionTable.isNsfw],
                    installed = it[IReaderExtensionTable.isInstalled],
                    hasUpdate = it[IReaderExtensionTable.hasUpdate],
                    obsolete = it[IReaderExtensionTable.isObsolete],
                )
            }
        }
    }

    suspend fun getExtensionList(): List<IReaderExtensionDataClass> {
        return extensionTableAsDataClass()
    }
}
