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
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.Edge
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.graphql.server.primitives.NodeList
import suwayomi.tachidesk.graphql.server.primitives.PageInfo
import suwayomi.tachidesk.graphql.types.SourceType
import suwayomi.tachidesk.manga.impl.IReaderSource
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension
import suwayomi.tachidesk.manga.model.table.IReaderExtensionTable
import suwayomi.tachidesk.manga.model.table.IReaderSourceTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

@GraphQLDescription("Type of content source")
enum class SourceContentType {
    @GraphQLDescription("Manga/Comic source (Tachiyomi)")
    MANGA,

    @GraphQLDescription("Novel/Light Novel source (IReader)")
    NOVEL,
}

@GraphQLDescription("Unified source representation for both Tachiyomi and IReader sources")
data class UnifiedSourceType(
    @GraphQLDescription("Unique source ID")
    val id: String,
    @GraphQLDescription("Source name")
    val name: String,
    @GraphQLDescription("Language code")
    val lang: String,
    @GraphQLDescription("Icon URL")
    val iconUrl: String?,
    @GraphQLDescription("Whether source supports latest updates")
    val supportsLatest: Boolean,
    @GraphQLDescription("Whether source has configurable settings")
    val isConfigurable: Boolean,
    @GraphQLDescription("Whether source contains NSFW content")
    val isNsfw: Boolean,
    @GraphQLDescription("Display name")
    val displayName: String,
    @GraphQLDescription("Base URL of the source")
    val baseUrl: String?,
    @GraphQLDescription("Content type (MANGA or NOVEL)")
    val contentType: SourceContentType,
    @GraphQLDescription("Whether the source extension is installed")
    val isInstalled: Boolean,
) : Node

data class UnifiedSourceNodeList(
    override val nodes: List<UnifiedSourceType>,
    override val edges: List<UnifiedSourceEdge>,
    override val pageInfo: PageInfo,
    override val totalCount: Int,
) : NodeList() {
    data class UnifiedSourceEdge(
        override val cursor: Cursor,
        override val node: UnifiedSourceType,
    ) : Edge()
}

@GraphQLDescription("Filter for unified source listing")
data class UnifiedSourceFilter(
    @GraphQLDescription("Filter by content type")
    val contentType: SourceContentType? = null,
    @GraphQLDescription("Filter by language")
    val lang: String? = null,
    @GraphQLDescription("Filter by NSFW status")
    val isNsfw: Boolean? = null,
    @GraphQLDescription("Filter by installed status")
    val isInstalled: Boolean? = null,
    @GraphQLDescription("Search by name")
    val nameContains: String? = null,
)

class UnifiedSourceQuery {
    @RequireAuth
    @GraphQLDescription("Get a unified list of all sources (both Tachiyomi manga and IReader novel sources)")
    fun allSources(
        @GraphQLDescription("Filter sources")
        filter: UnifiedSourceFilter? = null,
        @GraphQLDescription("Maximum number of results")
        first: Int? = null,
        @GraphQLDescription("Offset for pagination")
        offset: Int? = null,
    ): CompletableFuture<DataFetcherResult<UnifiedSourceNodeList?>> =
        future {
            asDataFetcherResult {
                val allSources = mutableListOf<UnifiedSourceType>()

                // Get Tachiyomi sources (MANGA)
                if (filter?.contentType == null || filter.contentType == SourceContentType.MANGA) {
                    val tachiyomiSources =
                        transaction {
                            SourceTable.selectAll().mapNotNull { row ->
                                val sourceType = SourceType(row) ?: return@mapNotNull null
                                UnifiedSourceType(
                                    id = sourceType.id.toString(),
                                    name = sourceType.name,
                                    lang = sourceType.lang,
                                    iconUrl = sourceType.iconUrl,
                                    supportsLatest = sourceType.supportsLatest ?: false,
                                    isConfigurable = sourceType.isConfigurable ?: false,
                                    isNsfw = sourceType.isNsfw ?: false,
                                    displayName = sourceType.displayName ?: sourceType.name,
                                    baseUrl = null, // Would need to load source to get this
                                    contentType = SourceContentType.MANGA,
                                    isInstalled = true, // Sources in table are installed
                                )
                            }
                        }
                    allSources.addAll(tachiyomiSources)
                }

                // Get IReader sources (NOVEL)
                if (filter?.contentType == null || filter.contentType == SourceContentType.NOVEL) {
                    val ireaderSources =
                        transaction {
                            IReaderSourceTable
                                .innerJoin(IReaderExtensionTable)
                                .selectAll()
                                .where { IReaderExtensionTable.isInstalled eq true }
                                .map { row ->
                                    val sourceId = row[IReaderSourceTable.id].value
                                    val catalogSource = IReaderSource.getCatalogueSourceOrNull(sourceId)

                                    UnifiedSourceType(
                                        id = sourceId.toString(),
                                        name = row[IReaderSourceTable.name],
                                        lang = row[IReaderSourceTable.lang],
                                        iconUrl = IReaderExtension.getExtensionIconUrl(row[IReaderExtensionTable.apkName]),
                                        supportsLatest = true,
                                        isConfigurable = true,
                                        isNsfw = row[IReaderSourceTable.isNsfw],
                                        displayName = catalogSource?.name ?: row[IReaderSourceTable.name],
                                        baseUrl = extractIReaderBaseUrl(catalogSource),
                                        contentType = SourceContentType.NOVEL,
                                        isInstalled = true,
                                    )
                                }
                        }
                    allSources.addAll(ireaderSources)
                }

                // Apply filters
                var filtered = allSources.asSequence()

                filter?.lang?.let { lang ->
                    filtered = filtered.filter { it.lang.equals(lang, ignoreCase = true) }
                }

                filter?.isNsfw?.let { nsfw ->
                    filtered = filtered.filter { it.isNsfw == nsfw }
                }

                filter?.isInstalled?.let { installed ->
                    filtered = filtered.filter { it.isInstalled == installed }
                }

                filter?.nameContains?.let { search ->
                    filtered = filtered.filter {
                        it.name.contains(search, ignoreCase = true) ||
                            it.displayName.contains(search, ignoreCase = true)
                    }
                }

                // Sort by name
                val sortedList = filtered.sortedBy { it.name.lowercase() }.toList()

                // Apply pagination
                val total = sortedList.size
                val paginatedList =
                    sortedList
                        .drop(offset ?: 0)
                        .take(first ?: Int.MAX_VALUE)

                UnifiedSourceNodeList(
                    nodes = paginatedList,
                    edges =
                        paginatedList.mapIndexed { index, source ->
                            UnifiedSourceNodeList.UnifiedSourceEdge(
                                cursor = Cursor((offset ?: 0 + index).toString()),
                                node = source,
                            )
                        },
                    pageInfo =
                        PageInfo(
                            hasNextPage = (offset ?: 0) + paginatedList.size < total,
                            hasPreviousPage = (offset ?: 0) > 0,
                            startCursor = paginatedList.firstOrNull()?.let { Cursor((offset ?: 0).toString()) },
                            endCursor = paginatedList.lastOrNull()?.let { Cursor(((offset ?: 0) + paginatedList.size - 1).toString()) },
                        ),
                    totalCount = total,
                )
            }
        }

    @RequireAuth
    @GraphQLDescription("Get statistics about all sources")
    fun sourceStats(): CompletableFuture<DataFetcherResult<SourceStats?>> =
        future {
            asDataFetcherResult {
                val mangaCount =
                    transaction {
                        SourceTable.selectAll().count().toInt()
                    }

                val novelCount =
                    transaction {
                        IReaderSourceTable
                            .innerJoin(IReaderExtensionTable)
                            .selectAll()
                            .where { IReaderExtensionTable.isInstalled eq true }
                            .count()
                            .toInt()
                    }

                val languageStats =
                    transaction {
                        val mangaLangs =
                            SourceTable.selectAll()
                                .groupBy { it[SourceTable.lang] }
                                .mapValues { it.value.size }

                        val novelLangs =
                            IReaderSourceTable
                                .innerJoin(IReaderExtensionTable)
                                .selectAll()
                                .where { IReaderExtensionTable.isInstalled eq true }
                                .groupBy { it[IReaderSourceTable.lang] }
                                .mapValues { it.value.size }

                        // Merge language counts
                        (mangaLangs.keys + novelLangs.keys).distinct().map { lang ->
                            LanguageSourceCount(
                                language = lang,
                                mangaCount = mangaLangs[lang] ?: 0,
                                novelCount = novelLangs[lang] ?: 0,
                                totalCount = (mangaLangs[lang] ?: 0) + (novelLangs[lang] ?: 0),
                            )
                        }.sortedByDescending { it.totalCount }
                    }

                SourceStats(
                    totalMangaSources = mangaCount,
                    totalNovelSources = novelCount,
                    totalSources = mangaCount + novelCount,
                    byLanguage = languageStats,
                )
            }
        }

    private fun extractIReaderBaseUrl(source: ireader.core.source.CatalogSource?): String? {
        if (source == null) return null
        return try {
            val method = source.javaClass.getMethod("getBaseUrl")
            method.invoke(source) as? String
        } catch (e: Exception) {
            null
        }
    }
}

@GraphQLDescription("Statistics about available sources")
data class SourceStats(
    @GraphQLDescription("Total number of manga sources")
    val totalMangaSources: Int,
    @GraphQLDescription("Total number of novel sources")
    val totalNovelSources: Int,
    @GraphQLDescription("Total number of all sources")
    val totalSources: Int,
    @GraphQLDescription("Source counts by language")
    val byLanguage: List<LanguageSourceCount>,
)

@GraphQLDescription("Source count for a specific language")
data class LanguageSourceCount(
    @GraphQLDescription("Language code")
    val language: String,
    @GraphQLDescription("Number of manga sources")
    val mangaCount: Int,
    @GraphQLDescription("Number of novel sources")
    val novelCount: Int,
    @GraphQLDescription("Total sources for this language")
    val totalCount: Int,
)
