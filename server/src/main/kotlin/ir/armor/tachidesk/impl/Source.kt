package ir.armor.tachidesk.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.impl.Extension.getExtensionIconUrl
import ir.armor.tachidesk.impl.util.GetHttpSource.getHttpSource
import ir.armor.tachidesk.model.database.table.ExtensionTable
import ir.armor.tachidesk.model.database.table.SourceTable
import ir.armor.tachidesk.model.dataclass.SourceDataClass
import mu.KotlinLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object Source {
    private val logger = KotlinLogging.logger {}

    fun getSourceList(): List<SourceDataClass> {
        return transaction {
            SourceTable.selectAll().map {
                SourceDataClass(
                    it[SourceTable.id].value.toString(),
                    it[SourceTable.name],
                    it[SourceTable.lang],
                    getExtensionIconUrl(ExtensionTable.select { ExtensionTable.id eq it[SourceTable.extension] }.first()[ExtensionTable.apkName]),
                    getHttpSource(it[SourceTable.id].value).supportsLatest
                )
            }
        }
    }

    fun getSource(sourceId: Long): SourceDataClass {
        return transaction {
            val source = SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()

            SourceDataClass(
                sourceId.toString(),
                source?.get(SourceTable.name),
                source?.get(SourceTable.lang),
                source?.let { ExtensionTable.select { ExtensionTable.id eq source[SourceTable.extension] }.first()[ExtensionTable.iconUrl] },
                source?.let { getHttpSource(sourceId).supportsLatest }
            )
        }
    }
}
