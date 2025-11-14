/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderSourceType
import suwayomi.tachidesk.manga.impl.IReaderSource

class IReaderSourceQuery {
    @RequireAuth
    @GraphQLDescription("Get list of all IReader sources with optional filtering")
    fun ireaderSources(
        @GraphQLDescription("Filter by language")
        lang: String? = null,
        @GraphQLDescription("Filter by NSFW status")
        isNsfw: Boolean? = null,
    ): List<IReaderSourceType> =
        IReaderSource
            .getSourceList()
            .filter { source ->
                (lang == null || source.lang == lang) &&
                    (isNsfw == null || source.isNsfw == isNsfw)
            }.map { IReaderSourceType(it) }

    @RequireAuth
    @GraphQLDescription("Get a specific IReader source by ID")
    fun ireaderSource(
        @GraphQLDescription("Source ID")
        sourceId: String,
    ): IReaderSourceType? {
        return IReaderSource.getSource(sourceId.toLongOrNull() ?: return null)?.let { IReaderSourceType(it) }
    }

    @RequireAuth
    @GraphQLDescription("Get available languages for IReader sources")
    fun ireaderSourceLanguages(): List<String> =
        IReaderSource
            .getSourceList()
            .map { it.lang }
            .distinct()
            .sorted()

    @RequireAuth
    @GraphQLDescription("Get statistics about IReader sources")
    fun ireaderSourceStats(): IReaderSourceStats {
        val sources = IReaderSource.getSourceList()
        return IReaderSourceStats(
            total = sources.size,
            supportsLatest = sources.count { it.supportsLatest },
            configurable = sources.count { it.isConfigurable },
            nsfw = sources.count { it.isNsfw },
            byLanguage =
                sources
                    .groupBy { it.lang }
                    .map { (lang, srcs) -> LanguageCount(lang, srcs.size) }
                    .sortedByDescending { it.count },
        )
    }
}

@GraphQLDescription("Statistics about IReader sources")
data class IReaderSourceStats(
    val total: Int,
    val supportsLatest: Int,
    val configurable: Int,
    val nsfw: Int,
    @GraphQLDescription("Source count by language, sorted by count descending")
    val byLanguage: List<LanguageCount>,
)
