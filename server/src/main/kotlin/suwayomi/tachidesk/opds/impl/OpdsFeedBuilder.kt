package suwayomi.tachidesk.opds.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.model.OpdsContentXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.repository.ChapterRepository
import suwayomi.tachidesk.opds.repository.MangaRepository
import suwayomi.tachidesk.opds.repository.NavigationRepository
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodeForOpdsURL
import suwayomi.tachidesk.opds.util.OpdsXmlUtil
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale

object OpdsFeedBuilder {
    private fun currentFormattedTime() = OpdsDateUtil.formatCurrentInstantForOpds()

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

    fun getHistoryFeed(
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
                val mangaDetails =
                    OpdsMangaDetails(item.mangaId, item.mangaTitle, item.mangaThumbnailUrl, item.mangaAuthor)
                OpdsEntryBuilder.createChapterListEntry(item.chapter, mangaDetails, baseUrl, true, locale)
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getSeriesFeed(
        criteria: OpdsSearchCriteria?,
        baseUrl: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
        locale: Locale,
    ): String {
        val currentSort = sort ?: "alpha_asc"
        val currentFilter = filter ?: "all"

        val (mangaEntries, total) =
            if (criteria != null) {
                MangaRepository.findMangaByCriteria(criteria)
            } else {
                MangaRepository.getAllManga(pageNum, currentSort, currentFilter)
            }

        val title =
            if (criteria != null) {
                MR.strings.opds_feeds_search_results.localized(locale)
            } else {
                MR.strings.opds_feeds_all_series_in_library_title.localized(locale)
            }

        val filterCounts = MangaRepository.getLibraryFilterCounts()
        val feedUrl = "library/series"
        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                title,
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = currentSort,
                currentFilter = currentFilter,
            )
        builder.totalResults = total
        OpdsEntryBuilder.addLibraryMangaSortAndFilterFacets(builder, "$baseUrl/$feedUrl", currentSort, currentFilter, locale, filterCounts)
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

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
//                                MR.strings.opds_facet_sort_popular.localized(locale),
                            ),
//                            OpdsLinkXml(
//                                OpdsConstants.LINK_REL_SORT_NEW,
//                                "$baseUrl/source/${entry.id}?sort=latest&lang=${locale.toLanguageTag()}",
//                                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
//                                MR.strings.opds_facet_sort_latest.localized(locale),
//                            ),
                        ),
                )
            },
        )
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

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

    fun getLibrarySourceFeed(
        sourceId: Long,
        baseUrl: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
        locale: Locale,
    ): String {
        val currentSort = sort ?: "alpha_asc"
        val currentFilter = filter ?: "all"
        val (mangaEntries, total) = MangaRepository.getLibraryMangaBySource(sourceId, pageNum, currentSort, currentFilter)
        val sourceNavEntry = NavigationRepository.getLibrarySources(1).first.find { it.id == sourceId }
        val sourceNameOrId = sourceNavEntry?.name ?: sourceId.toString()
        val feedTitle = MR.strings.opds_feeds_library_source_specific_title.localized(locale, sourceNameOrId)

        val filterCounts = MangaRepository.getLibraryFilterCounts()
        val feedUrl = "library/source/$sourceId"
        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                feedTitle,
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = currentSort,
                currentFilter = currentFilter,
            )
        builder.totalResults = total
        builder.icon = sourceNavEntry?.iconUrl
        OpdsEntryBuilder.addLibraryMangaSortAndFilterFacets(builder, "$baseUrl/$feedUrl", currentSort, currentFilter, locale, filterCounts)
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

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

    fun getStatusFeed(
        baseUrl: String,
        @Suppress("UNUSED_PARAMETER") pageNum: Int,
        locale: Locale,
    ): String {
        val statuses = NavigationRepository.getStatuses(locale)
        val builder =
            FeedBuilderInternal(
                baseUrl,
                "library/status",
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

    fun getLibraryUpdatesFeed(
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

    fun getCategoryFeed(
        categoryId: Int,
        baseUrl: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
        locale: Locale,
    ): String {
        val currentSort = sort ?: "alpha_asc"
        val currentFilter = filter ?: "all"
        val (mangaEntries, total) = MangaRepository.getMangaByCategory(categoryId, pageNum, currentSort, currentFilter)
        val categoryNavEntry = NavigationRepository.getCategories(1).first.find { it.id == categoryId }
        val feedTitle = MR.strings.opds_feeds_category_specific_title.localized(locale, categoryNavEntry?.name ?: categoryId.toString())
        val filterCounts = MangaRepository.getLibraryFilterCounts()
        val feedUrl = "category/$categoryId"
        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                feedTitle,
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = currentSort,
                currentFilter = currentFilter,
            )
        builder.totalResults = total
        OpdsEntryBuilder.addLibraryMangaSortAndFilterFacets(builder, "$baseUrl/$feedUrl", currentSort, currentFilter, locale, filterCounts)
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getGenreFeed(
        genre: String,
        baseUrl: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
        locale: Locale,
    ): String {
        val currentSort = sort ?: "alpha_asc"
        val currentFilter = filter ?: "all"
        val (mangaEntries, total) = MangaRepository.getMangaByGenre(genre, pageNum, currentSort, currentFilter)
        val feedTitle = MR.strings.opds_feeds_genre_specific_title.localized(locale, genre)
        val filterCounts = MangaRepository.getLibraryFilterCounts()
        val feedUrl = "genre/${genre.encodeForOpdsURL()}"
        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                feedTitle,
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = currentSort,
                currentFilter = currentFilter,
            )
        builder.totalResults = total
        OpdsEntryBuilder.addLibraryMangaSortAndFilterFacets(builder, "$baseUrl/$feedUrl", currentSort, currentFilter, locale, filterCounts)
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getStatusMangaFeed(
        statusDbId: Long,
        baseUrl: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
        locale: Locale,
    ): String {
        val currentSort = sort ?: "alpha_asc"
        val currentFilter = filter ?: "all"
        val statusNavEntry = NavigationRepository.getStatuses(locale).find { it.id == statusDbId.toInt() }
        val statusName = statusNavEntry?.title ?: statusDbId.toString()
        val (mangaEntries, total) = MangaRepository.getMangaByStatus(statusDbId.toInt(), pageNum, currentSort, currentFilter)
        val feedTitle = MR.strings.opds_feeds_status_specific_title.localized(locale, statusName)
        val filterCounts = MangaRepository.getLibraryFilterCounts()
        val feedUrl = "status/$statusDbId"
        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                feedTitle,
                locale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = currentSort,
                currentFilter = currentFilter,
            )
        builder.totalResults = total
        OpdsEntryBuilder.addLibraryMangaSortAndFilterFacets(builder, "$baseUrl/$feedUrl", currentSort, currentFilter, locale, filterCounts)
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, locale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getLanguageFeed(
        contentLangCode: String,
        baseUrl: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
        uiLocale: Locale,
    ): String {
        val currentSort = sort ?: "alpha_asc"
        val currentFilter = filter ?: "all"
        val (mangaEntries, total) = MangaRepository.getMangaByContentLanguage(contentLangCode, pageNum, currentSort, currentFilter)
        val contentLanguageDisplayName = Locale.forLanguageTag(contentLangCode).getDisplayName(uiLocale)
        val feedTitle = MR.strings.opds_feeds_language_specific_title.localized(uiLocale, contentLanguageDisplayName)
        val filterCounts = MangaRepository.getLibraryFilterCounts()
        val feedUrl = "language/$contentLangCode"
        val builder =
            FeedBuilderInternal(
                baseUrl,
                feedUrl,
                feedTitle,
                uiLocale,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum,
                currentSort = currentSort,
                currentFilter = currentFilter,
            )
        builder.totalResults = total
        OpdsEntryBuilder.addLibraryMangaSortAndFilterFacets(
            builder,
            "$baseUrl/$feedUrl",
            currentSort,
            currentFilter,
            uiLocale,
            filterCounts,
        )
        builder.entries.addAll(mangaEntries.map { OpdsEntryBuilder.mangaAcqEntryToEntry(it, baseUrl, uiLocale) })
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

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

        if (chapterEntries.isEmpty() && totalChapters == 0L) {
            try {
                // Chapters are not in the database, fetch them from the source.
                suwayomi.tachidesk.manga.impl.Chapter
                    .fetchChapterList(mangaId)

                // Re-query the database to get the now-populated, sorted, and paginated list.
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
                // Log the exception, but don't crash the feed generation.
                // It will correctly return an empty feed.
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
