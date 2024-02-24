package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences
import io.javalin.plugin.json.JsonMapper
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.unregisterCatalogueSource
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.androidimpl.CustomContext

object Source {
    private val logger = KotlinLogging.logger {}

    fun getSourceList(): List<SourceDataClass> {
        return transaction {
            SourceTable.selectAll().mapNotNull {
                val catalogueSource = getCatalogueSourceOrNull(it[SourceTable.id].value) ?: return@mapNotNull null
                val sourceExtension = ExtensionTable.select { ExtensionTable.id eq it[SourceTable.extension] }.first()

                SourceDataClass(
                    it[SourceTable.id].value.toString(),
                    it[SourceTable.name],
                    it[SourceTable.lang],
                    getExtensionIconUrl(sourceExtension[ExtensionTable.apkName]),
                    catalogueSource.supportsLatest,
                    catalogueSource is ConfigurableSource,
                    it[SourceTable.isNsfw],
                    catalogueSource.toString(),
                )
            }
        }
    }

    fun getSource(sourceId: Long): SourceDataClass? { // all the data extracted fresh form the source instance
        return transaction {
            val source = SourceTable.select { SourceTable.id eq sourceId }.firstOrNull() ?: return@transaction null
            val catalogueSource = getCatalogueSourceOrNull(sourceId) ?: return@transaction null
            val extension = ExtensionTable.select { ExtensionTable.id eq source[SourceTable.extension] }.first()

            SourceDataClass(
                sourceId.toString(),
                source[SourceTable.name],
                source[SourceTable.lang],
                getExtensionIconUrl(
                    extension[ExtensionTable.apkName],
                ),
                catalogueSource.supportsLatest,
                catalogueSource is ConfigurableSource,
                source[SourceTable.isNsfw],
                catalogueSource.toString(),
            )
        }
    }

    private val context by DI.global.instance<CustomContext>()

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
    fun getSourcePreferences(sourceId: Long): List<PreferenceObject> {
        return getSourcePreferencesRaw(sourceId).map {
            PreferenceObject(it::class.java.simpleName, it)
        }
    }

    fun getSourcePreferencesRaw(sourceId: Long): List<Preference> {
        val source = getCatalogueSourceOrStub(sourceId)

        if (source is ConfigurableSource) {
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

    private val jsonMapper by DI.global.instance<JsonMapper>()

    fun setSourcePreference(
        sourceId: Long,
        position: Int,
        value: String,
        getValue: (Preference) -> Any = { pref ->
            println(jsonMapper::class.java.name)
            @Suppress("UNCHECKED_CAST")
            when (pref.defaultValueType) {
                "String" -> value
                "Boolean" -> value.toBoolean()
                "Set<String>" -> jsonMapper.fromJsonString(value, List::class.java as Class<List<String>>).toSet()
                else -> throw RuntimeException("Unsupported type conversion")
            }
        },
    ) {
        val screen = preferenceScreenMap[sourceId]!!
        val pref = screen.preferences[position]

        val newValue = getValue(pref)

        pref.saveNewValue(newValue)
        pref.callChangeListener(newValue)

        // must reload the source because a preference was changed
        unregisterCatalogueSource(sourceId)
    }

    fun modifyMeta(
        sourceId: Long,
        key: String,
        value: String,
    ) {
        transaction {
            val meta =
                transaction {
                    SourceMetaTable.select { (SourceMetaTable.ref eq sourceId) and (SourceMetaTable.key eq key) }
                }.firstOrNull()

            if (meta == null) {
                SourceMetaTable.insert {
                    it[SourceMetaTable.key] = key
                    it[SourceMetaTable.value] = value
                    it[SourceMetaTable.ref] = sourceId
                }
            } else {
                SourceMetaTable.update({ (SourceMetaTable.ref eq sourceId) and (SourceMetaTable.key eq key) }) {
                    it[SourceMetaTable.value] = value
                }
            }
        }
    }
}
