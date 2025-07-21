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
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.json.JsonMapper
import io.javalin.json.fromJsonString
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrNull
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.getCatalogueSourceOrStub
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.unregisterCatalogueSource
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.androidimpl.CustomContext

object Source {
    private val logger = KotlinLogging.logger {}

    fun getSourceList(): List<SourceDataClass> {
        return transaction {
            SourceTable.selectAll().mapNotNull {
                val catalogueSource = getCatalogueSourceOrNull(it[SourceTable.id].value) ?: return@mapNotNull null
                val sourceExtension = ExtensionTable.selectAll().where { ExtensionTable.id eq it[SourceTable.extension] }.first()

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
            val source = SourceTable.selectAll().where { SourceTable.id eq sourceId }.firstOrNull() ?: return@transaction null
            val catalogueSource = getCatalogueSourceOrNull(sourceId) ?: return@transaction null
            val extension = ExtensionTable.selectAll().where { ExtensionTable.id eq source[SourceTable.extension] }.first()

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

    private val jsonMapper: JsonMapper by injectLazy()

    fun setSourcePreference(
        sourceId: Long,
        position: Int,
        value: String,
        getValue: (Preference) -> Any = { pref ->
            println(jsonMapper::class.java.name)
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

    fun getSourcesMetaMaps(ids: List<Long>): Map<Long, Map<String, String>> =
        transaction {
            SourceMetaTable
                .selectAll()
                .where { SourceMetaTable.ref inList ids }
                .groupBy { it[SourceMetaTable.ref] }
                .mapValues { it.value.associate { it[SourceMetaTable.key] to it[SourceMetaTable.value] } }
                .withDefault { emptyMap() }
        }

    fun modifyMeta(
        sourceId: Long,
        key: String,
        value: String,
    ) {
        modifySourceMetas(mapOf(sourceId to mapOf(key to value)))
    }

    fun modifySourceMetas(metaBySourceIds: Map<Long, Map<String, String>>) {
        transaction {
            val sourceIds = metaBySourceIds.keys
            val metaKeys = metaBySourceIds.flatMap { it.value.keys }

            val dbMetaBySourceId =
                SourceMetaTable
                    .selectAll()
                    .where { (SourceMetaTable.ref inList sourceIds) and (SourceMetaTable.key inList metaKeys) }
                    .groupBy { it[SourceMetaTable.ref] }

            val existingMetaByMetaId =
                sourceIds.flatMap { sourceId ->
                    val metaByKey = dbMetaBySourceId[sourceId].orEmpty().associateBy { it[SourceMetaTable.key] }
                    val existingMetas = metaBySourceIds[sourceId].orEmpty().filter { (key) -> key in metaByKey.keys }

                    existingMetas.map { entry ->
                        val metaId = metaByKey[entry.key]!![SourceMetaTable.id].value

                        metaId to entry
                    }
                }

            val newMetaBySourceId =
                sourceIds.flatMap { sourceId ->
                    val metaByKey = dbMetaBySourceId[sourceId].orEmpty().associateBy { it[SourceMetaTable.key] }

                    metaBySourceIds[sourceId]
                        .orEmpty()
                        .filter { entry -> entry.key !in metaByKey.keys }
                        .map { entry -> sourceId to entry }
                }

            if (existingMetaByMetaId.isNotEmpty()) {
                BatchUpdateStatement(SourceMetaTable).apply {
                    existingMetaByMetaId.forEach { (metaId, entry) ->
                        addBatch(EntityID(metaId, SourceMetaTable))
                        this[SourceMetaTable.value] = entry.value
                    }
                    execute(this@transaction)
                }
            }

            if (newMetaBySourceId.isNotEmpty()) {
                SourceMetaTable.batchInsert(newMetaBySourceId) { (sourceId, entry) ->
                    this[SourceMetaTable.ref] = sourceId
                    this[SourceMetaTable.key] = entry.key
                    this[SourceMetaTable.value] = entry.value
                }
            }
        }
    }
}
