/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.execution.DataFetcherResult
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.IReaderSource
import suwayomi.tachidesk.manga.model.table.IReaderSourceMetaTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

@GraphQLDescription("IReader source preference value types")
enum class IReaderPreferenceType {
    STRING,
    INT,
    LONG,
    FLOAT,
    BOOLEAN,
    STRING_SET,
}

@GraphQLDescription("IReader source preference")
data class IReaderSourcePreference(
    @GraphQLDescription("Preference key")
    val key: String,
    @GraphQLDescription("Display title (same as key for IReader)")
    val title: String,
    @GraphQLDescription("Description/summary")
    val summary: String?,
    @GraphQLDescription("Default value as string")
    val defaultValue: String?,
    @GraphQLDescription("Current value as string")
    val currentValue: String?,
    @GraphQLDescription("Value type")
    val valueType: IReaderPreferenceType,
)

class IReaderSourcePreferencesQuery {
    @RequireAuth
    @GraphQLDescription("Get preferences for an IReader source")
    fun ireaderSourcePreferences(
        @GraphQLDescription("Source ID")
        sourceId: Long,
    ): CompletableFuture<DataFetcherResult<List<IReaderSourcePreference>?>> =
        future {
            asDataFetcherResult {
                IReaderSource.getSource(sourceId)
                    ?: throw IllegalArgumentException("Source not found: $sourceId")

                // Get stored preferences from the meta table
                val storedPrefs = transaction {
                    IReaderSourceMetaTable.selectAll()
                        .where { IReaderSourceMetaTable.ref eq sourceId }
                        .map { row ->
                            IReaderSourcePreference(
                                key = row[IReaderSourceMetaTable.key],
                                title = row[IReaderSourceMetaTable.key],
                                summary = null,
                                defaultValue = null,
                                currentValue = row[IReaderSourceMetaTable.value],
                                valueType = inferValueType(row[IReaderSourceMetaTable.value]),
                            )
                        }
                }

                storedPrefs
            }
        }

    private fun inferValueType(value: String): IReaderPreferenceType {
        return when {
            value == "true" || value == "false" -> IReaderPreferenceType.BOOLEAN
            value.toLongOrNull() != null -> IReaderPreferenceType.LONG
            value.toDoubleOrNull() != null -> IReaderPreferenceType.FLOAT
            value.startsWith("[") && value.endsWith("]") -> IReaderPreferenceType.STRING_SET
            else -> IReaderPreferenceType.STRING
        }
    }
}
