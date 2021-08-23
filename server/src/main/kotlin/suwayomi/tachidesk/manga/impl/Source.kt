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
import eu.kanade.tachiyomi.source.getPreferenceKey
import eu.kanade.tachiyomi.source.online.HttpSource
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

    val HttpSource.isNsfw: Boolean
        get() = this::class.annotations.any { it.toString() == "@eu.kanade.tachiyomi.annotations.Nsfw()" }

    fun getSourceList(): List<SourceDataClass> {
        return transaction {
            SourceTable.selectAll().map {
                val httpSource = getHttpSource(it[SourceTable.id].value)

                SourceDataClass(
                    it[SourceTable.id].value.toString(),
                    it[SourceTable.name],
                    it[SourceTable.lang],
                    getExtensionIconUrl(
                        ExtensionTable.select { ExtensionTable.id eq it[SourceTable.extension] }
                            .first()[ExtensionTable.apkName]
                    ),
                    httpSource.supportsLatest,
                    httpSource is ConfigurableSource,
                    httpSource.isNsfw
                )
            }
        }
    }

    fun getSource(sourceId: Long): SourceDataClass {
        return transaction {
            val source = SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()
            val httpSource = source?.let { getHttpSource(sourceId) }

            SourceDataClass(
                sourceId.toString(),
                source?.get(SourceTable.name),
                source?.get(SourceTable.lang),
                source?.let {
                    getExtensionIconUrl(
                        ExtensionTable.select { ExtensionTable.id eq source[SourceTable.extension] }
                            .first()[ExtensionTable.apkName]
                    )
                },
                httpSource?.supportsLatest,
                httpSource?.let { it is ConfigurableSource },
                httpSource?.isNsfw
            )
        }
    }

    private val context by DI.global.instance<CustomContext>()

    /**
     * Clients should support these types for extensions to work properly (in order of importance)
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
