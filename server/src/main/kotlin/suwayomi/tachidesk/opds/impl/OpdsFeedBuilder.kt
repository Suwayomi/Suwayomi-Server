package suwayomi.tachidesk.opds.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
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
                baseUrl,
                "", // Root path is empty
                MR.strings.opds_feeds_root.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                null,
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
     * @param pageNum The page number for pagination.
     * @param locale The locale for localization.
     * @return An XML string representing the history feed.
     */
    suspend fun getHistoryFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (historyItems, total) = ChapterRepository.getHistory(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "history",
                MR.strings.opds_feeds_history_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            historyItems.map { item ->
                val mangaDetails = OpdsMangaDetails(item.mangaId, item.mangaTitle, item.mangaThumbnailUrl, item.mangaAuthor)
                OpdsEntryBuilder.createChapterListEntry(item.chapter, mangaDetails, baseUrl, true, locale)
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a feed for search results based on the provided criteria.
     * @param criteria The search criteria.
     * @param baseUrl The base URL for constructing links.
     * @param pageNum The page number for pagination.
     * @param locale The locale for localization.
     * @return An XML string representing the search results feed.
     */
    fun getSearchFeed(
        criteria: OpdsSearchCriteria,
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (mangaEntries, total) = MangaRepository.findMangaByCriteria(criteria)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library/series",
                MR.strings.opds_feeds_search_results_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a generic library feed based on various filtering and sorting criteria.
     * @param criteria The filtering criteria.
     * @param baseUrl The base URL for constructing links.
     * @param pageNum The page number for pagination.
     * @param sort The sorting parameter.
     * @param filter The filtering parameter.
     * @param locale The locale for localization.
     * @return An XML string representing the library feed.
     */
    fun getLibraryFeed(
        criteria: OpdsMangaFilter,
        baseUrl: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
        locale: Locale,
    ): String {
        val result = MangaRepository.getLibraryManga(pageNum, sort, filter, criteria)

        val feedTitle =
            when (criteria.primaryFilter) {
                PrimaryFilterType.SOURCE ->
                    MR.strings.opds_feeds_library_source_specific_title.localized(
                        locale,
                        result.feedTitleComponent ?: criteria.sourceId.toString(),
                    )
                PrimaryFilterType.CATEGORY ->
                    MR.strings.opds_feeds_category_specific_title.localized(
                        locale,
                        result.feedTitleComponent ?: criteria.categoryId.toString(),
                    )
                PrimaryFilterType.GENRE ->
                    MR.strings.opds_feeds_genre_specific_title.localized(
                        locale,
                        result.feedTitleComponent ?: "Unknown",
                    )
                PrimaryFilterType.STATUS -> {
                    val statusName = NavigationRepository.getStatuses(locale).find { it.id == criteria.statusId }?.title
                    MR.strings.opds_feeds_status_specific_title.localized(locale, statusName ?: criteria.statusId.toString())
                }
                PrimaryFilterType.LANGUAGE -> {
                    val langName = Locale.forLanguageTag(criteria.langCode ?: "").getDisplayName(locale)
                    MR.strings.opds_feeds_language_specific_title.localized(locale, langName)
                }
                else -> MR.strings.opds_feeds_all_series_in_library_title.localized(locale)
            }

        val feedUrl =
            when (criteria.primaryFilter) {
                PrimaryFilterType.SOURCE -> "library/source/${criteria.sourceId}"
                PrimaryFilterType.CATEGORY -> "category/${criteria.categoryId}"
                PrimaryFilterType.GENRE -> "genre/${criteria.genre}"
                PrimaryFilterType.STATUS -> "status/${criteria.statusId}"
                PrimaryFilterType.LANGUAGE -> "language/${criteria.langCode}"
                else -> "library/series"
            }

        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                feedTitle,
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = criteria.sort,
                currentFilter = criteria.filter,
                explicitQueryParams = criteria.toCrossFilterQueryParameters(),
            )
        builder.totalResults = result.totalCount

        // Add all library facets (sort, filter, and cross-filtering)
        OpdsEntryBuilder.addLibraryFacets(builder, baseUrl, criteria, locale)

        builder.entries.addAll(result.mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })

        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed listing all available sources for exploration.
     * @param baseUrl The base URL for constructing links.
     * @param pageNum The page number for pagination.
     * @param locale The locale for localization.
     * @return An XML string representing the explore sources feed.
     */
    fun getExploreSourcesFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (sourceNavEntries, total) = NavigationRepository.getExploreSources(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "sources",
                MR.strings.opds_feeds_sources_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum,
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
                                "$baseUrl/source/${entry.id}?sort=popular&lang=${locale.toLanguageTag()}",
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
     * @param pageNum The page number for pagination.
     * @param locale The locale for localization.
     * @return An XML string representing the library sources feed.
     */
    fun getLibrarySourcesFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (sourceNavEntries, total) = NavigationRepository.getLibrarySources(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library/sources",
                MR.strings.opds_feeds_library_sources_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum,
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
                                "$baseUrl/library/source/${entry.id}?lang=${locale.toLanguageTag()}",
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
     * @param sourceId The ID of the source.
     * @param baseUrl The base URL for constructing links.
     * @param pageNum The page number for pagination.
     * @param sort The sorting parameter ('popular' or 'latest').
     * @param locale The locale for localization.
     * @return An XML string representing the source-specific feed.
     */
    suspend fun getExploreSourceFeed(
        sourceId: Long,
        baseUrl: String,
        pageNum: Int,
        sort: String,
        locale: Locale,
    ): String {
        val (mangaEntries, hasNextPage) = MangaRepository.getMangaBySource(sourceId, pageNum, sort)
        val sourceNavEntry = NavigationRepository.getExploreSources(1).first.find { it.id == sourceId }
        val sourceNameOrId = sourceNavEntry?.name ?: sourceId.toString()
        val titleRes =
            if (sort == "latest") {
                MR.strings.opds_feeds_source_specific_latest_title
            } else {
                MR.strings.opds_feeds_source_specific_popular_title
            }
        val feedTitle = titleRes.localized(locale, sourceNameOrId)
        val feedUrl = "source/$sourceId"
        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                feedTitle,
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = sort,
            )
        builder.totalResults =
            if (hasNextPage) {
                (pageNum * serverConfig.opdsItemsPerPage.value + 1).toLong()
            } else {
                (
                    (pageNum - 1) *
                        serverConfig.opdsItemsPerPage.value +
                        mangaEntries.size
                ).toLong()
            }
        builder.icon = sourceNavEntry?.iconUrl
        OpdsEntryBuilder.addSourceSortFacets(builder, "$baseUrl/$feedUrl", sort, locale)
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates a navigation feed for library categories.
     * @param baseUrl The base URL for constructing links.
     * @param pageNum The page number for pagination.
     * @param locale The locale for localization.
     * @return An XML string representing the categories navigation feed.
     */
    fun getCategoriesFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (categoryNavEntries, total) = NavigationRepository.getCategories(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library/categories",
                MR.strings.opds_feeds_categories_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum,
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
     * @param pageNum The page number for pagination.
     * @param locale The locale for localization.
     * @return An XML string representing the genres navigation feed.
     */
    fun getGenresFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (genreNavEntries, total) = NavigationRepository.getGenres(pageNum, locale)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library/genres",
                MR.strings.opds_feeds_genres_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum,
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
     * @param pageNum The page number (currently unused).
     * @param locale The locale for localization.
     * @return An XML string representing the status navigation feed.
     */
    fun getStatusFeed(
        baseUrl: String,
        @Suppress("UNUSED_PARAMETER") pageNum: Int,
        locale: Locale,
    ): String {
        val statuses = NavigationRepository.getStatuses(locale)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library/statuses",
                MR.strings.opds_feeds_status_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                null,
            )
        builder.totalResults = statuses.size.toLong()
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
     * @param uiLocale The locale for the user interface.
     * @return An XML string representing the languages navigation feed.
     */
    fun getLanguagesFeed(
        baseUrl: String,
        uiLocale: Locale,
    ): String {
        val languages = NavigationRepository.getContentLanguages(uiLocale)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library/languages",
                MR.strings.opds_feeds_languages_title.localized(uiLocale),
                uiLocale,
                OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                null,
            )
        builder.totalResults = languages.size.toLong()
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
                                "$baseUrl/language/${entry.id}?lang=${uiLocale.toLanguageTag()}",
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
     * Generates an acquisition feed for recent chapter updates in the library.
     * @param baseUrl The base URL for constructing links.
     * @param pageNum The page number for pagination.
     * @param locale The locale for localization.
     * @return An XML string representing the library updates feed.
     */
    suspend fun getLibraryUpdatesFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (updateItems, total) = ChapterRepository.getLibraryUpdates(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library-updates",
                MR.strings.opds_feeds_library_updates_title.localized(locale),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
            )
        builder.totalResults = total
        builder.entries.addAll(
            updateItems.map { item ->
                val mangaDetails = OpdsMangaDetails(item.mangaId, item.mangaTitle, item.mangaThumbnailUrl, item.mangaAuthor)
                OpdsEntryBuilder.createChapterListEntry(item.chapter, mangaDetails, baseUrl, true, locale)
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates an acquisition feed for all chapters of a specific manga.
     * @param mangaId The ID of the manga.
     * @param baseUrl The base URL for constructing links.
     * @param pageNum The page number for pagination.
     * @param sortParam The sorting parameter for chapters.
     * @param filterParam The filtering parameter for chapters.
     * @param locale The locale for localization.
     * @return An XML string representing the series' chapters feed.
     */
    suspend fun getSeriesChaptersFeed(
        mangaId: Int,
        baseUrl: String,
        pageNum: Int,
        sortParam: String?,
        filterParam: String?,
        locale: Locale,
    ): String {
        val mangaDetails =
            MangaRepository.getMangaDetails(mangaId)
                ?: return buildNotFoundFeed(
                    baseUrl,
                    "series/$mangaId/chapters",
                    MR.strings.opds_error_manga_not_found.localized(locale, mangaId),
                    locale,
                )
        val (sortColumn, currentSortOrder) =
            when (sortParam?.lowercase()) {
                "asc", "number_asc" -> ChapterTable.sourceOrder to SortOrder.ASC
                "desc", "number_desc" -> ChapterTable.sourceOrder to SortOrder.DESC
                "date_asc" -> ChapterTable.date_upload to SortOrder.ASC
                "date_desc" -> ChapterTable.date_upload to SortOrder.DESC
                else -> ChapterTable.sourceOrder to (serverConfig.opdsChapterSortOrder.value ?: SortOrder.ASC)
            }
        val currentFilter = filterParam?.lowercase() ?: if (serverConfig.opdsShowOnlyUnreadChapters.value) "unread" else "all"
        var (chapterEntries, totalChapters) =
            ChapterRepository.getChaptersForManga(
                mangaId,
                pageNum,
                sortColumn,
                currentSortOrder,
                currentFilter,
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
                        pageNum,
                        sortColumn,
                        currentSortOrder,
                        currentFilter,
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
                baseUrl,
                feedUrl,
                MR.strings.opds_feeds_manga_chapters.localized(locale, mangaDetails.title),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
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
            actualSortParamForLinks,
            currentFilter,
            locale,
            filterCounts,
        )
        builder.entries.addAll(
            chapterEntries.map { chapter ->
                OpdsEntryBuilder.createChapterListEntry(chapter, mangaDetails, baseUrl, false, locale)
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Generates an acquisition feed with detailed metadata for a single chapter.
     * @param mangaId The ID of the manga.
     * @param chapterSourceOrder The source order index of the chapter.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @return An XML string representing the chapter's metadata feed.
     */
    suspend fun getChapterMetadataFeed(
        mangaId: Int,
        chapterSourceOrder: Int,
        baseUrl: String,
        locale: Locale,
    ): String {
        val mangaDetails =
            MangaRepository.getMangaDetails(mangaId)
                ?: return buildNotFoundFeed(
                    baseUrl,
                    "series/$mangaId/chapter/$chapterSourceOrder/metadata",
                    MR.strings.opds_error_manga_not_found.localized(locale, mangaId),
                    locale,
                )
        val chapterMetadata =
            ChapterRepository.getChapterDetailsForMetadataFeed(mangaId, chapterSourceOrder)
                ?: return buildNotFoundFeed(
                    baseUrl,
                    "series/$mangaId/chapter/$chapterSourceOrder/metadata",
                    MR.strings.opds_error_chapter_not_found.localized(locale, chapterSourceOrder),
                    locale,
                )
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "series/$mangaId/chapter/${chapterMetadata.sourceOrder}/metadata",
                MR.strings.opds_feeds_chapter_details.localized(locale, mangaDetails.title, chapterMetadata.name),
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                null,
            )
        builder.totalResults = 1
        mangaDetails.thumbnailUrl?.let { proxyThumbnailUrl(mangaDetails.id) }?.also {
            builder.icon = it
            builder.links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
            builder.links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
        }
        builder.entries.add(OpdsEntryBuilder.createChapterMetadataEntry(chapterMetadata, mangaDetails, baseUrl, locale))
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    /**
     * Builds a simple OPDS feed to indicate that a resource was not found.
     * @param baseUrl The base URL.
     * @param idPath The path that was not found.
     * @param title The title for the feed (e.g., an error message).
     * @param locale The locale for localization.
     * @return An XML string representing the 'not found' feed.
     */
    fun buildNotFoundFeed(
        baseUrl: String,
        idPath: String,
        title: String,
        locale: Locale,
    ): String =
        FeedBuilderInternal(baseUrl, idPath, title, locale, feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION, pageNum = null)
            .apply { totalResults = 0L }
            .build()
            .let(OpdsXmlUtil::serializeFeedToString)
}
