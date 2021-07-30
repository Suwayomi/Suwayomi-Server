package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import mu.KotlinLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import xyz.nulldev.androidcompat.androidimpl.CustomContext

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
                    getHttpSource(it[SourceTable.id].value).supportsLatest,
                    getHttpSource(it[SourceTable.id].value) is ConfigurableSource
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
                source?.let { getHttpSource(sourceId).supportsLatest },
                source?.let { getHttpSource(sourceId) is ConfigurableSource },
            )
        }
    }

    private val context by DI.global.instance<CustomContext>()

    data class PreferenceObject(
        val type: String,
        val props: Any
    )

    fun getSourcePreferences(sourceId: Long): List<PreferenceObject> {
        val source = getHttpSource(sourceId)

        if (source is ConfigurableSource) {
            val screen = PreferenceScreen(context)

            source.setupPreferenceScreen(screen)

            return screen.preferences.map {
                PreferenceObject(it::class.java.name, it)
            }
        }
        return emptyList()
    }
}
