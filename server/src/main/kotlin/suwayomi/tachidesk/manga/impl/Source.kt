package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.getPreferenceKey
import mu.KotlinLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.util.GetHttpSource.getHttpSource
import suwayomi.tachidesk.manga.impl.util.GetHttpSource.invalidateSourceCache
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.androidimpl.CustomContext

object Source {
    private val logger = KotlinLogging.logger {}

    fun getSourceList(): List<SourceDataClass> {
        return transaction {
            SourceTable.selectAll().map {
                val httpSource = getHttpSource(it[SourceTable.id].value)
                val sourceExtension = ExtensionTable.select { ExtensionTable.id eq it[SourceTable.extension] }.first()

                SourceDataClass(
                    it[SourceTable.id].value.toString(),
                    it[SourceTable.name],
                    it[SourceTable.lang],
                    getExtensionIconUrl(sourceExtension[ExtensionTable.apkName]),
                    httpSource.supportsLatest,
                    httpSource is ConfigurableSource,
                    it[SourceTable.isNsfw],
                    httpSource.toString(),
                )
            }
        }
    }

    fun getSource(sourceId: Long): SourceDataClass { // all the data extracted fresh form the source instance
        return transaction {
            if (sourceId == LocalSource.ID) {
                // initialize local source
                getHttpSource(sourceId)
            }

            val source = SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()
            val httpSource = source?.let { getHttpSource(sourceId) }
            val extension = source?.let {
                ExtensionTable.select { ExtensionTable.id eq source[SourceTable.extension] }.first()
            }

            SourceDataClass(
                sourceId.toString(),
                source?.get(SourceTable.name),
                source?.get(SourceTable.lang),
                source?.let {
                    getExtensionIconUrl(
                        extension!![ExtensionTable.apkName]
                    )
                },
                httpSource?.supportsLatest,
                httpSource?.let { it is ConfigurableSource },
                source?.get(SourceTable.isNsfw),
                httpSource?.toString()
            )
        }
    }

    private val context by DI.global.instance<CustomContext>()

    /**
     * (2021-08) Clients should support these types for extensions to work properly
     * - EditTextPreference
     * - SwitchPreferenceCompat
     * - ListPreference
     * - CheckBoxPreference
     */
    data class PreferenceObject(
        val type: String,
        val props: Any
    )

    var preferenceScreenMap: MutableMap<Long, PreferenceScreen> = mutableMapOf()

    /**
     *  Gets a source's PreferenceScreen, puts the result into [preferenceScreenMap]
     */
    fun getSourcePreferences(sourceId: Long): List<PreferenceObject> {
        val source = getHttpSource(sourceId)

        if (source is ConfigurableSource) {
            val sourceShardPreferences =
                Injekt.get<Application>().getSharedPreferences(source.getPreferenceKey(), Context.MODE_PRIVATE)

            val screen = PreferenceScreen(context)
            screen.sharedPreferences = sourceShardPreferences

            source.setupPreferenceScreen(screen)

            preferenceScreenMap[sourceId] = screen

            return screen.preferences.map {
                PreferenceObject(it::class.java.simpleName, it)
            }
        }
        return emptyList()
    }

    data class SourcePreferenceChange(
        val position: Int,
        val value: String
    )

    fun setSourcePreference(sourceId: Long, change: SourcePreferenceChange) {
        val screen = preferenceScreenMap[sourceId]!!
        val pref = screen.preferences[change.position]

        val newValue = when (pref.defaultValueType) {
            "String" -> change.value
            "Boolean" -> change.value.toBoolean()
            else -> throw RuntimeException("Unsupported type conversion")
        }

        pref.saveNewValue(newValue)
        pref.callChangeListener(newValue)

        // must reload the source cache because a preference was changed
        invalidateSourceCache(sourceId)
    }
}
