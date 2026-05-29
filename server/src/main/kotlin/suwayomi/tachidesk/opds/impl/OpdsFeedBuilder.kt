package suwayomi.tachidesk.opds.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.dto.PrimaryFilterType
import suwayomi.tachidesk.opds.model.OpdsContentXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.repository.ChapterRepository
import suwayomi.tachidesk.opds.repository.MangaRepository
import suwayomi.tachidesk.opds.repository.NavigationRepository
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.opds.util.OpdsStringUtil
import suwayomi.tachidesk.opds.util.OpdsXmlUtil
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale

/**
 * Builds OPDS feeds by fetching data from repositories and converting it into XML format.
 */
object OpdsFeedBuilder {
    private fun currentFormattedTime() = OpdsDateUtil.formatCurrentInstantForOpds()

    /**
     * Generates the root navigation feed for the OPDS catalog.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @return An XML string representing the root feed.
     */
    fun getRootFeed(
        baseUrl: String,
        locale: Locale,
    ): String {
        val navItems = NavigationRepository.getRootNavigationItems(locale)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "", // Root path is empty
                title = MR.strings.opds_feeds_root.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            )
        builder.totalResults = navItems.size.toLong()
        builder.entries.addAll(
            navItems.map { item ->
                OpdsEntryXml(
                    id = "urn:suwayomi:navigation:root:${item.id}",
                    title = item.title,
                    updated = currentFormattedTime(),
                    link =
                        listOf(
                            OpdsLinkXml(
                                rel = OpdsConstants.LINK_REL_SUBSECTION,
                                href = "$baseUrl/${item.id}?lang=${locale.toLanguageTag()}",
                                type = item.linkType,
                                title = item.title,
                            ),
                        ),
                    content = OpdsContentXml(type = "text", value = item.description),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates the history feed showing recently read chapters.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the history feed.
     */
    suspend fun getHistoryFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (historyItems, total) = ChapterRepository.getHistory(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "history",
                title = MR.strings.opds_feeds_history_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        val skipMetadata = serverConfig.opdsSkipChapterMetadataFeed.value
        builder.entries.addAll(
            historyItems.map { item ->
                val mangaDetails =
                    OpdsMangaDetails(item.mangaId, item.mangaTitle, item.mangaThumbnailUrl, item.mangaAuthor, item.mangaTotalChapters)
                OpdsEntryBuilder.createChapterListEntry(
                    baseUrl,
                    locale,
                    item.chapter,
                    mangaDetails,
                    true,
                    skipMetadata,
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a feed for search results based on the provided criteria.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param criteria The search criteria.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the search results feed.
     */
    fun getSearchFeed(
        baseUrl: String,
        locale: Locale,
        criteria: OpdsSearchCriteria,
        pageNum: Int,
    ): String {
        val (mangaEntries, total) = MangaRepository.findMangaByCriteria(criteria)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "library/series",
                title = MR.strings.opds_feeds_search_results_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = pageNum,
                isSearchFeed = true,
            )
        builder.totalResults = total
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(baseUrl, locale, it) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a generic library feed based on various filtering and sorting criteria.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param criteria The filtering criteria.
     * @param isSearch Indicates if it's a search feed.
     * @param pageNum The page number for pagination.
     * @param sort The sorting parameter.
     * @param filter The filtering parameter.
     * @return An XML string representing the library feed.
     */
    fun getLibraryFeed(
        baseUrl: String,
        locale: Locale,
        criteria: OpdsMangaFilter,
        isSearch: Boolean,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): String {
        val result = MangaRepository.getLibraryManga(criteria, pageNum, sort, filter)

        val feedTitle =
            when (criteria.primaryFilter) {
                PrimaryFilterType.SOURCE -> {
                    MR.strings.opds_feeds_library_source_specific_title.localized(
                        locale,
                        result.feedTitleComponent ?: criteria.sourceId.toString(),
                    )
                }

                PrimaryFilterType.CATEGORY -> {
                    MR.strings.opds_feeds_category_specific_title.localized(
                        locale,
                        result.feedTitleComponent ?: criteria.categoryId.toString(),
                    )
                }

                PrimaryFilterType.GENRE -> {
                    MR.strings.opds_feeds_genre_specific_title.localized(
                        locale,
                        result.feedTitleComponent ?: "Unknown",
                    )
                }

                PrimaryFilterType.STATUS -> {
                    val statusName =
                        NavigationRepository
                            .getStatuses(locale, pageNum = null, activeFilters = criteria)
                            .first
                            .find { it.id == criteria.statusId }
                            ?.title
                    MR.strings.opds_feeds_status_specific_title.localized(locale, statusName ?: criteria.statusId.toString())
                }

                PrimaryFilterType.LANGUAGE -> {
                    val langName = Locale.forLanguageTag(criteria.langCode ?: "").getDisplayName(locale)
                    MR.strings.opds_feeds_language_specific_title.localized(locale, langName)
                }

                else -> {
                    MR.strings.opds_feeds_all_series_in_library_title.localized(locale)
                }
            }

        val feedUrl =
            when (criteria.primaryFilter) {
                PrimaryFilterType.SOURCE -> "source/${criteria.sourceId}"
                PrimaryFilterType.CATEGORY -> "category/${criteria.categoryId}"
                PrimaryFilterType.GENRE -> "genre/${criteria.genre}"
                PrimaryFilterType.STATUS -> "status/${criteria.statusId}"
                PrimaryFilterType.LANGUAGE -> "language/${criteria.langCode}"
                else -> "library/series"
            }

        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = feedUrl,
                title = feedTitle,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = pageNum,
                explicitQueryParams = criteria.toCrossFilterQueryParameters(),
                currentSort = criteria.sort,
                currentFilter = criteria.filter,
                isSearchFeed = isSearch,
            )
        builder.totalResults = result.totalCount

        // Add all library facets (sort, filter, and cross-filtering)
        OpdsEntryBuilder.addLibraryFacets(builder, baseUrl, locale, criteria)

        builder.entries.addAll(result.mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(baseUrl, locale, it) })

        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed listing all available sources for exploration.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the explore sources feed.
     */
    fun getExploreSourcesFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (sourceNavEntries, total) = NavigationRepository.getExploreSources(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "sources",
                title = MR.strings.opds_feeds_sources_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            sourceNavEntries.map { entry ->
                OpdsEntryXml(
                    id = "urn:suwayomi:navigation:sources:${entry.id}",
                    title = entry.name,
                    updated = currentFormattedTime(),
                    link =
                        listOf(
                            OpdsLinkXml(
                                OpdsConstants.LINK_REL_SUBSECTION,
                                "$baseUrl/explore/source/${entry.id}?sort=popular&lang=${locale.toLanguageTag()}",
                                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                            ),
                        ),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed listing sources for series present in the library.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the library sources feed.
     */
    fun getLibrarySourcesFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (sourceNavEntries, total) = NavigationRepository.getLibrarySources(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "library/sources",
                title = MR.strings.opds_feeds_library_sources_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            sourceNavEntries.map { entry ->
                OpdsEntryXml(
                    id = "urn:suwayomi:navigation:library:sources:${entry.id}",
                    title = entry.name,
                    updated = currentFormattedTime(),
                    link =
                        listOf(
                            OpdsLinkXml(
                                OpdsConstants.LINK_REL_SUBSECTION,
                                "$baseUrl/source/${entry.id}?lang=${locale.toLanguageTag()}",
                                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                entry.name,
                                thrCount = entry.mangaCount?.toInt(),
                            ),
                        ),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates an acquisition feed for manga from a specific source (explore context).
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param sourceId The ID of the source.
     * @param pageNum The page number for pagination.
     * @param sort The sorting parameter ('popular' or 'latest').
     * @return An XML string representing the source-specific feed.
     */
    suspend fun getExploreSourceFeed(
        baseUrl: String,
        locale: Locale,
        sourceId: Long,
        pageNum: Int,
        sort: String,
    ): String {
        val (mangaEntries, hasNextPage) = MangaRepository.getMangaBySource(sourceId, pageNum, sort)
        val sourceInfo = NavigationRepository.getSourceDetails(sourceId)
        val sourceName = sourceInfo?.first ?: sourceId.toString()
        val titleRes =
            if (sort ==
                "latest"
            ) {
                MR.strings.opds_feeds_source_specific_latest_title
            } else {
                MR.strings.opds_feeds_source_specific_popular_title
            }
        val feedTitle = titleRes.localized(locale, sourceName)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "explore/source/$sourceId",
                title = feedTitle,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = pageNum,
                currentSort = sort,
            )
        builder.totalResults =
            if (hasNextPage) {
                (pageNum * serverConfig.opdsItemsPerPage.value + 1).toLong()
            } else {
                ((pageNum - 1) * serverConfig.opdsItemsPerPage.value + mangaEntries.size).toLong()
            }
        builder.icon = sourceInfo?.second
        OpdsEntryBuilder.addSourceSortFacets(builder, "$baseUrl/explore/source/$sourceId", locale, sort)
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(baseUrl, locale, it) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed for library categories.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the categories navigation feed.
     */
    fun getCategoriesFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (categoryNavEntries, total) = NavigationRepository.getCategories(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "library/categories",
                title = MR.strings.opds_feeds_categories_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            categoryNavEntries.map { entry ->
                OpdsEntryXml(
                    id = "urn:suwayomi:navigation:categories:${entry.id}",
                    title = entry.name,
                    updated = currentFormattedTime(),
                    link =
                        listOf(
                            OpdsLinkXml(
                                OpdsConstants.LINK_REL_SUBSECTION,
                                "$baseUrl/category/${entry.id}?lang=${locale.toLanguageTag()}",
                                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                entry.name,
                                thrCount = entry.mangaCount.toInt(),
                            ),
                        ),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed for library genres.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the genres navigation feed.
     */
    fun getGenresFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (genreNavEntries, total) = NavigationRepository.getGenres(locale, pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "library/genres",
                title = MR.strings.opds_feeds_genres_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            genreNavEntries.map { entry ->
                OpdsEntryXml(
                    id = "urn:suwayomi:navigation:genres:${entry.id}",
                    title = entry.title,
                    updated = currentFormattedTime(),
                    link =
                        listOf(
                            OpdsLinkXml(
                                OpdsConstants.LINK_REL_SUBSECTION,
                                "$baseUrl/genre/${entry.id}?lang=${locale.toLanguageTag()}",
                                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                entry.title,
                                thrCount = entry.mangaCount.toInt(),
                            ),
                        ),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed for manga publication statuses.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param pageNum The page number (currently unused).
     * @return An XML string representing the status navigation feed.
     */
    fun getStatusFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (statuses, total) = NavigationRepository.getStatuses(locale, pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "library/statuses",
                title = MR.strings.opds_feeds_status_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            statuses.map { entry ->
                OpdsEntryXml(
                    id = "urn:suwayomi:navigation:status:${entry.id}",
                    title = entry.title,
                    updated = currentFormattedTime(),
                    link =
                        listOf(
                            OpdsLinkXml(
                                OpdsConstants.LINK_REL_SUBSECTION,
                                "$baseUrl/status/${entry.id}?lang=${locale.toLanguageTag()}",
                                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                entry.title,
                                thrCount = entry.mangaCount.toInt(),
                            ),
                        ),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed for content languages available in the library.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for the user interface.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the languages navigation feed.
     */
    fun getLanguagesFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (languages, total) = NavigationRepository.getContentLanguages(locale, pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "library/languages",
                title = MR.strings.opds_feeds_languages_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            languages.map { entry ->
                OpdsEntryXml(
                    id = "urn:suwayomi:navigation:language:${entry.id}",
                    title = entry.title,
                    updated = currentFormattedTime(),
                    link =
                        listOf(
                            OpdsLinkXml(
                                OpdsConstants.LINK_REL_SUBSECTION,
                                "$baseUrl/language/${entry.id}?lang=${locale.toLanguageTag()}",
                                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                entry.title,
                                thrCount = entry.mangaCount.toInt(),
                            ),
                        ),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates an acquisition feed of recent chapter updates for series in the library.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param pageNum The page number for pagination.
     * @return An XML string representing the library updates feed.
     */
    suspend fun getLibraryUpdatesFeed(
        baseUrl: String,
        locale: Locale,
        pageNum: Int,
    ): String {
        val (updateItems, total) = ChapterRepository.getLibraryUpdates(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "library-updates",
                title = MR.strings.opds_feeds_library_updates_title.localized(locale),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = pageNum,
            )
        builder.totalResults = total
        val skipMetadata = serverConfig.opdsSkipChapterMetadataFeed.value
        builder.entries.addAll(
            updateItems.map { item ->
                val mangaDetails =
                    OpdsMangaDetails(item.mangaId, item.mangaTitle, item.mangaThumbnailUrl, item.mangaAuthor, item.mangaTotalChapters)
                OpdsEntryBuilder.createChapterListEntry(
                    baseUrl,
                    locale,
                    item.chapter,
                    mangaDetails,
                    true,
                    skipMetadata,
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates an acquisition feed for all chapters of a specific manga.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param mangaId The ID of the manga.
     * @param pageNum The page number for pagination.
     * @param sortParam The sorting parameter for chapters.
     * @param filterParam The filtering parameter for chapters.
     * @return An XML string representing the series' chapters feed.
     */
    suspend fun getSeriesChaptersFeed(
        baseUrl: String,
        locale: Locale,
        mangaId: Int,
        pageNum: Int,
        sortParam: String?,
        filterParam: String?,
    ): String {
        val mangaDetails =
            MangaRepository.getMangaDetails(mangaId)
                ?: return buildNotFoundFeed(
                    baseUrl,
                    locale,
                    "series/$mangaId/chapters",
                    MR.strings.opds_error_manga_not_found.localized(locale, mangaId),
                )
        val (sortColumn, currentSortOrder) =
            when (sortParam?.lowercase()) {
                "asc", "number_asc" -> ChapterTable.sourceOrder to SortOrder.ASC
                "desc", "number_desc" -> ChapterTable.sourceOrder to SortOrder.DESC
                "date_asc" -> ChapterTable.date_upload to SortOrder.ASC
                "date_desc" -> ChapterTable.date_upload to SortOrder.DESC
                else -> ChapterTable.sourceOrder to (serverConfig.opdsChapterSortOrder.value)
            }
        val currentFilter = filterParam?.lowercase() ?: if (serverConfig.opdsShowOnlyUnreadChapters.value) "unread" else "all"
        val skipMetadata = serverConfig.opdsSkipChapterMetadataFeed.value
        var (chapterEntries, totalChapters) =
            ChapterRepository.getChaptersForManga(
                mangaId,
                sortColumn,
                currentSortOrder,
                currentFilter,
                pageNum,
                skipMetadata,
            )

        // If no chapters are found in the database, attempt to fetch them from the source.
        if (chapterEntries.isEmpty() && totalChapters == 0L) {
            try {
                suwayomi.tachidesk.manga.impl.Chapter
                    .fetchChapterList(mangaId)

                // Re-query after fetching.
                val (refetchedChapters, refetchedTotal) =
                    ChapterRepository.getChaptersForManga(
                        mangaId,
                        sortColumn,
                        currentSortOrder,
                        currentFilter,
                        pageNum,
                        skipMetadata,
                    )
                chapterEntries = refetchedChapters
                totalChapters = refetchedTotal
            } catch (e: Exception) {
                KotlinLogging.logger { }.error(e) { "Failed to fetch chapters online for mangaId: $mangaId" }
            }
        }

        val actualSortParamForLinks =
            sortParam ?: run {
                val prefix = if (sortColumn == ChapterTable.sourceOrder) "number" else "date"
                val suffix = if (currentSortOrder == SortOrder.ASC) "asc" else "desc"
                "${prefix}_$suffix"
            }
        val filterCounts = ChapterRepository.getChapterFilterCounts(mangaId)
        val feedUrl = "series/$mangaId/chapters"
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = feedUrl,
                title = MR.strings.opds_feeds_manga_chapters.localized(locale, mangaDetails.title),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = pageNum,
                currentSort = actualSortParamForLinks,
                currentFilter = currentFilter,
            )
        builder.totalResults = totalChapters
        mangaDetails.thumbnailUrl?.let { proxyThumbnailUrl(mangaDetails.id) }?.also {
            builder.icon = it
            builder.links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
            builder.links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
        }
        OpdsEntryBuilder.addChapterSortAndFilterFacets(
            builder,
            "$baseUrl/$feedUrl",
            locale,
            actualSortParamForLinks,
            currentFilter,
            filterCounts,
        )
        builder.entries.addAll(
            chapterEntries.map { chapter ->
                OpdsEntryBuilder.createChapterListEntry(
                    baseUrl,
                    locale,
                    chapter,
                    mangaDetails,
                    false,
                    skipMetadata,
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates an acquisition feed with detailed metadata for a single chapter.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param mangaId The ID of the manga.
     * @param chapterSourceOrder The source order index of the chapter.
     * @return An XML string representing the chapter's metadata feed.
     */
    suspend fun getChapterMetadataFeed(
        baseUrl: String,
        locale: Locale,
        mangaId: Int,
        chapterSourceOrder: Int,
    ): String {
        val mangaDetails =
            MangaRepository.getMangaDetails(mangaId)
                ?: return buildNotFoundFeed(
                    baseUrl,
                    locale,
                    "series/$mangaId/chapter/$chapterSourceOrder/metadata",
                    MR.strings.opds_error_manga_not_found.localized(locale, mangaId),
                )
        val chapterMetadata =
            ChapterRepository.getChapterDetailsForMetadataFeed(mangaId, chapterSourceOrder)
                ?: return buildNotFoundFeed(
                    baseUrl,
                    locale,
                    "series/$mangaId/chapter/$chapterSourceOrder/metadata",
                    MR.strings.opds_error_chapter_not_found.localized(locale, chapterSourceOrder),
                )

        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                locale = locale,
                idPath = "series/$mangaId/chapter/${chapterMetadata.sourceOrder}/metadata",
                title = MR.strings.opds_feeds_chapter_details.localized(locale, mangaDetails.title, chapterMetadata.name),
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = null,
            )

        mangaDetails.thumbnailUrl?.let { proxyThumbnailUrl(mangaDetails.id) }?.also {
            builder.icon = it
            builder.links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
            builder.links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
        }

        val (primaryEntry, conflictEntry) =
            OpdsEntryBuilder.createChapterMetadataEntries(
                baseUrl = baseUrl,
                locale = locale,
                chapter = chapterMetadata,
                manga = mangaDetails,
            )

        builder.entries.add(primaryEntry)
        if (conflictEntry != null) {
            builder.entries.add(conflictEntry)
            builder.totalResults = 2
        } else {
            builder.totalResults = 1
        }

        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Builds a simple OPDS feed to indicate that a resource was not found.
     * @param baseUrl The base URL.
     * @param locale The locale for localization.
     * @param idPath The path that was not found.
     * @param title The title for the feed (e.g., an error message).
     * @return An XML string representing the 'not found' feed.
     */
    fun buildNotFoundFeed(
        baseUrl: String,
        locale: Locale,
        idPath: String,
        title: String,
    ): String =
        FeedBuilderInternal(
            baseUrl = baseUrl,
            locale = locale,
            idPath = idPath,
            title = title,
            feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            pageNum = null,
        ).apply { totalResults = 0L }
            .build()
            .let(OpdsXmlUtil::serializeFeedToString)
}
