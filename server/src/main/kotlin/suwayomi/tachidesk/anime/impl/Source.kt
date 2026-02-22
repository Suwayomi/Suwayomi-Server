package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import io.javalin.json.JsonMapper
import io.javalin.json.fromJsonString
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.anime.impl.extension.AnimeExtension.getExtensionIconUrl
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.anime.impl.util.source.GetAnimeCatalogueSource.unregisterCatalogueSource
import suwayomi.tachidesk.anime.model.dataclass.AnimeSourceDataClass
import suwayomi.tachidesk.anime.model.table.AnimeExtensionTable
import suwayomi.tachidesk.anime.model.table.AnimeSourceTable
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.androidimpl.CustomContext

object Source {
    fun getSourceList(): List<AnimeSourceDataClass> {
        return transaction {
            AnimeSourceTable.selectAll().mapNotNull {
                val catalogueSource = getCatalogueSourceOrNull(it[AnimeSourceTable.id].value) ?: return@mapNotNull null
                val sourceExtension = AnimeExtensionTable.selectAll().where { AnimeExtensionTable.id eq it[AnimeSourceTable.extension] }.first()

                AnimeSourceDataClass(
                    id = it[AnimeSourceTable.id].value.toString(),
                    name = it[AnimeSourceTable.name],
                    lang = it[AnimeSourceTable.lang],
                    iconUrl = getExtensionIconUrl(sourceExtension[AnimeExtensionTable.apkName]),
                    supportsLatest = catalogueSource.supportsLatest,
                    isConfigurable = catalogueSource is ConfigurableAnimeSource,
                    isNsfw = it[AnimeSourceTable.isNsfw],
                    displayName = catalogueSource.toString(),
                    baseUrl = runCatching { (catalogueSource as? AnimeHttpSource)?.baseUrl }.getOrNull(),
                )
            }
        }
    }

    fun getSource(sourceId: Long): AnimeSourceDataClass? {
        return transaction {
            val source = AnimeSourceTable.selectAll().where { AnimeSourceTable.id eq sourceId }.firstOrNull() ?: return@transaction null
            val catalogueSource = getCatalogueSourceOrNull(sourceId) ?: return@transaction null
            val extension = AnimeExtensionTable.selectAll().where { AnimeExtensionTable.id eq source[AnimeSourceTable.extension] }.first()

            AnimeSourceDataClass(
                id = sourceId.toString(),
                name = source[AnimeSourceTable.name],
                lang = source[AnimeSourceTable.lang],
                iconUrl = getExtensionIconUrl(extension[AnimeExtensionTable.apkName]),
                supportsLatest = catalogueSource.supportsLatest,
                isConfigurable = catalogueSource is ConfigurableAnimeSource,
                isNsfw = source[AnimeSourceTable.isNsfw],
                displayName = catalogueSource.toString(),
                baseUrl = runCatching { (catalogueSource as? AnimeHttpSource)?.baseUrl }.getOrNull(),
            )
        }
    }

    private val context: CustomContext by injectLazy()

    /**
     * (2021-11) Clients should support these types for extensions to work properly
     * - EditTextPreference
     * - SwitchPreferenceCompat
     * - ListPreference
     * - CheckBoxPreference
     * - MultiSelectListPreference
     */
    data class PreferenceObject(
        val type: String,
        val props: Any,
    )

    var preferenceScreenMap: MutableMap<Long, PreferenceScreen> = mutableMapOf()

    /**
     *  Gets a source's PreferenceScreen, puts the result into [preferenceScreenMap]
     */
    fun getSourcePreferences(sourceId: Long): List<PreferenceObject> =
        getSourcePreferencesRaw(sourceId).map {
            PreferenceObject(it::class.java.simpleName, it)
        }

    fun getSourcePreferencesRaw(sourceId: Long): List<Preference> {
        val source = getCatalogueSourceOrStub(sourceId)

        if (source is ConfigurableAnimeSource) {
            val sourceShardPreferences = source.sourcePreferences()

            val screen = PreferenceScreen(context)
            screen.sharedPreferences = sourceShardPreferences

            source.setupPreferenceScreen(screen)

            preferenceScreenMap[sourceId] = screen

            return screen.preferences
        }
        return emptyList()
    }

    data class SourcePreferenceChange(
        val position: Int,
        val value: String,
    )

    private val jsonMapper: JsonMapper by injectLazy()

    fun setSourcePreference(
        sourceId: Long,
        position: Int,
        value: String,
        getValue: (Preference) -> Any = { pref ->
            when (pref.defaultValueType) {
                "String" -> value
                "Boolean" -> value.toBoolean()
                "Set<String>" -> jsonMapper.fromJsonString<List<String>>(value).toSet()
                else -> throw RuntimeException("Unsupported type conversion")
            }
        },
    ) {
        val screen = preferenceScreenMap[sourceId]!!
        val pref = screen.preferences[position]

        if (!pref.isEnabled) {
            return
        }

        val newValue = getValue(pref)

        pref.saveNewValue(newValue)
        pref.callChangeListener(newValue)

        // must reload the source because a preference was changed
        unregisterCatalogueSource(sourceId)
    }
}
