package suwayomi.tachidesk.opds.impl

import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsCategoryNavEntry
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsChapterMetadataAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsGenreNavEntry
import suwayomi.tachidesk.opds.dto.OpdsLanguageNavEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.dto.OpdsRootNavEntry
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.dto.OpdsSourceNavEntry
import suwayomi.tachidesk.opds.dto.OpdsStatusNavEntry
import suwayomi.tachidesk.opds.model.OpdsAuthorXml
import suwayomi.tachidesk.opds.model.OpdsCategoryXml
import suwayomi.tachidesk.opds.model.OpdsContentXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsFeedXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.model.OpdsSummaryXml
import suwayomi.tachidesk.opds.repository.ChapterRepository
import suwayomi.tachidesk.opds.repository.MangaRepository
import suwayomi.tachidesk.opds.repository.NavigationRepository
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodeForOpdsURL
import suwayomi.tachidesk.opds.util.OpdsStringUtil.formatFileSizeForOpds
import suwayomi.tachidesk.opds.util.OpdsXmlUtil
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale

object OpdsFeedBuilder {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

    private val feedAuthor = OpdsAuthorXml("Suwayomi", "https://suwayomi.org/")

    private fun currentFormattedTime() = OpdsDateUtil.formatCurrentInstantForOpds()

    // --- Main Feed Generators ---

    fun getRootFeed(
        baseUrl: String,
        locale: Locale,
    ): String {
        val navItems = NavigationRepository.getRootNavigationItems(locale)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "root",
                title = MR.strings.opds_feeds_root.localized(locale),
                locale = locale,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                pageNum = null, // Root is never paginated
            ).apply {
                totalResults = navItems.size.toLong()
                entries.addAll(
                    navItems.map { item: OpdsRootNavEntry ->
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
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getMangasFeed(
        criteria: OpdsSearchCriteria?,
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String =
        if (criteria != null) {
            getMangaSearchResultsFeed(criteria, baseUrl, locale)
        } else {
            getAllMangasFeed(baseUrl, pageNum, locale)
        }

    private fun getAllMangasFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (mangaEntries, total) = MangaRepository.getAllManga(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "mangas",
                title = MR.strings.opds_feeds_all_manga_title.localized(locale),
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                entries.addAll(mangaEntries.map { mangaAcqEntryToEntry(it, baseUrl, locale) })
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    private fun getMangaSearchResultsFeed(
        criteria: OpdsSearchCriteria,
        baseUrl: String,
        locale: Locale,
    ): String {
        val (mangaEntries, total) = MangaRepository.findMangaByCriteria(criteria)
        val queryParams = mutableListOf<String>()
        criteria.query?.let { queryParams.add("query=${it.encodeForOpdsURL()}") }
        criteria.author?.let { queryParams.add("author=${it.encodeForOpdsURL()}") }
        criteria.title?.let { queryParams.add("title=${it.encodeForOpdsURL()}") }

        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "mangas",
                title = MR.strings.opds_feeds_search_results.localized(locale),
                locale = locale,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                explicitQueryParams = queryParams.joinToString("&").ifEmpty { null },
                pageNum = 1, // Search results always start at page 1 for this link
            ).apply {
                totalResults = total
                entries.addAll(mangaEntries.map { mangaAcqEntryToEntry(it, baseUrl, locale) })
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getSourcesFeed(
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (sourceNavEntries, total) = NavigationRepository.getSources(pageNum)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "sources",
                title = MR.strings.opds_feeds_sources_title.localized(locale),
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = total
                entries.addAll(
                    sourceNavEntries.map { entry: OpdsSourceNavEntry ->
                        OpdsEntryXml(
                            id = "urn:suwayomi:navigation:sources:${entry.id}",
                            title = entry.name,
                            updated = currentFormattedTime(),
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        OpdsConstants.LINK_REL_SUBSECTION,
                                        "$baseUrl/source/${entry.id}?lang=${locale.toLanguageTag()}",
                                        OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                        entry.name,
                                    ),
                                ),
                            // Consider adding icon as artwork link if needed
                        )
                    },
                )
            }
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
                baseUrl = baseUrl,
                idPath = "categories",
                title = MR.strings.opds_feeds_categories_title.localized(locale),
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = total
                entries.addAll(
                    categoryNavEntries.map { entry: OpdsCategoryNavEntry ->
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
                                    ),
                                ),
                        )
                    },
                )
            }
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
                baseUrl = baseUrl,
                idPath = "genres",
                title = MR.strings.opds_feeds_genres_title.localized(locale),
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = total
                entries.addAll(
                    genreNavEntries.map { entry: OpdsGenreNavEntry ->
                        OpdsEntryXml(
                            id = "urn:suwayomi:navigation:genres:${entry.id}", // Already encoded
                            title = entry.title,
                            updated = currentFormattedTime(),
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        OpdsConstants.LINK_REL_SUBSECTION,
                                        "$baseUrl/genre/${entry.id}?lang=${locale.toLanguageTag()}",
                                        OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                        entry.title,
                                    ),
                                ),
                        )
                    },
                )
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    // `pageNum` is ignored, always fetches all, and sets pageNum = null in builder.
    fun getStatusFeed(
        baseUrl: String,
        @Suppress("UNUSED_PARAMETER") pageNum: Int,
        locale: Locale,
    ): String {
        val statuses = NavigationRepository.getStatuses(locale)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "status",
                title = MR.strings.opds_feeds_status_title.localized(locale),
                locale = locale,
                pageNum = null, // Status feed is not paginated
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = statuses.size.toLong()
                entries.addAll(
                    statuses.map { entry: OpdsStatusNavEntry ->
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
                                    ),
                                ),
                        )
                    },
                )
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getLanguagesFeed(
        baseUrl: String,
        uiLocale: Locale,
    ): String {
        val languages = NavigationRepository.getContentLanguages(uiLocale)
        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "languages",
                title = MR.strings.opds_feeds_languages_title.localized(uiLocale),
                locale = uiLocale,
                pageNum = null, // Language feed is not paginated
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = languages.size.toLong()
                entries.addAll(
                    languages.map { entry: OpdsLanguageNavEntry ->
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
                                    ),
                                ),
                        )
                    },
                )
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    // --- Specific Acquisition Feed Generators ---

    fun getMangaFeed(
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
                    "manga/$mangaId",
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

        val (chapterEntries, totalChapters) =
            ChapterRepository.getChaptersForManga(
                mangaId,
                pageNum,
                sortColumn,
                currentSortOrder,
                currentFilter,
            )

        val actualSortParamForLinks =
            sortParam ?: run {
                val prefix = if (sortColumn == ChapterTable.sourceOrder) "number" else "date"
                val suffix = if (currentSortOrder == SortOrder.ASC) "asc" else "desc"
                "${prefix}_$suffix"
            }

        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "manga/$mangaId/chapters",
                title = MR.strings.opds_feeds_manga_chapters.localized(locale, mangaDetails.title),
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                currentSort = actualSortParamForLinks,
                currentFilter = currentFilter,
            ).apply {
                totalResults = totalChapters
                icon = mangaDetails.thumbnailUrl?.let { proxyThumbnailUrl(mangaDetails.id) }
                icon?.let {
                    links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
                    links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
                }
                addChapterSortAndFilterFacets(
                    this,
                    "$baseUrl/manga/$mangaId",
                    actualSortParamForLinks,
                    currentFilter,
                    locale,
                    sortColumn,
                    currentSortOrder,
                )
                entries.addAll(chapterEntries.map { chapter -> createChapterListEntry(chapter, mangaDetails, baseUrl, false, locale) })
            }
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
                    "manga/$mangaId/chapter/$chapterSourceOrder/metadata",
                    MR.strings.opds_error_manga_not_found.localized(locale, mangaId),
                    locale,
                )

        val chapterMetadata =
            ChapterRepository.getChapterDetailsForMetadataFeed(mangaId, chapterSourceOrder)
                ?: return buildNotFoundFeed(
                    baseUrl,
                    "manga/$mangaId/chapter/$chapterSourceOrder/metadata",
                    MR.strings.opds_error_chapter_not_found.localized(locale, chapterSourceOrder),
                    locale,
                )

        val builder =
            FeedBuilderInternal(
                baseUrl = baseUrl,
                idPath = "manga/$mangaId/chapter/${chapterMetadata.sourceOrder}/metadata",
                title = MR.strings.opds_feeds_chapter_details.localized(locale, mangaDetails.title, chapterMetadata.name),
                locale = locale,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                pageNum = null, // Metadata feed is single entry, not paginated
            ).apply {
                totalResults = 1
                icon = mangaDetails.thumbnailUrl?.let { proxyThumbnailUrl(mangaDetails.id) }
                icon?.let {
                    links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
                    links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
                }
                entries.add(createChapterMetadataEntry(chapterMetadata, mangaDetails, locale))
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getSourceFeed(
        sourceId: Long,
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (mangaEntries, total) = MangaRepository.getMangaBySource(sourceId, pageNum)
        val sourceNavEntry = NavigationRepository.getSources(1).first.find { it.id == sourceId }
        val sourceNameOrId = sourceNavEntry?.name ?: sourceId.toString()
        val feedTitle =
            MR.strings.opds_feeds_source_specific_title.localized(
                locale,
                sourceNameOrId,
            )

        val builder =
            FeedBuilderInternal(
                baseUrl,
                "source/$sourceId",
                feedTitle,
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                icon = sourceNavEntry?.iconUrl
                entries.addAll(mangaEntries.map { mangaAcqEntryToEntry(it, baseUrl, locale) })
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getCategoryFeed(
        categoryId: Int,
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (mangaEntries, total) = MangaRepository.getMangaByCategory(categoryId, pageNum)
        val categoryNavEntry = NavigationRepository.getCategories(1).first.find { it.id == categoryId }
        val feedTitle = MR.strings.opds_feeds_category_specific_title.localized(locale, categoryNavEntry?.name ?: categoryId.toString())

        val builder =
            FeedBuilderInternal(
                baseUrl,
                "category/$categoryId",
                feedTitle,
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                entries.addAll(mangaEntries.map { mangaAcqEntryToEntry(it, baseUrl, locale) })
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getGenreFeed(
        genre: String,
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val (mangaEntries, total) = MangaRepository.getMangaByGenre(genre, pageNum)
        val feedTitle = MR.strings.opds_feeds_genre_specific_title.localized(locale, genre)

        val builder =
            FeedBuilderInternal(
                baseUrl,
                "genre/${genre.encodeForOpdsURL()}",
                feedTitle,
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                entries.addAll(mangaEntries.map { mangaAcqEntryToEntry(it, baseUrl, locale) })
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getStatusMangaFeed(
        statusDbId: Long,
        baseUrl: String,
        pageNum: Int,
        locale: Locale,
    ): String {
        val statusNavEntry = NavigationRepository.getStatuses(locale).find { it.id == statusDbId.toInt() }
        val statusName = statusNavEntry?.title ?: statusDbId.toString()
        val (mangaEntries, total) = MangaRepository.getMangaByStatus(statusDbId.toInt(), pageNum)
        val feedTitle = MR.strings.opds_feeds_status_specific_title.localized(locale, statusName)

        val builder =
            FeedBuilderInternal(
                baseUrl,
                "status/$statusDbId",
                feedTitle,
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                entries.addAll(mangaEntries.map { mangaAcqEntryToEntry(it, baseUrl, locale) })
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getLanguageFeed(
        contentLangCode: String,
        baseUrl: String,
        pageNum: Int,
        uiLocale: Locale,
    ): String {
        val (mangaEntries, total) = MangaRepository.getMangaByContentLanguage(contentLangCode, pageNum)
        val contentLanguageDisplayName = Locale.forLanguageTag(contentLangCode).getDisplayName(uiLocale)
        val feedTitle = MR.strings.opds_feeds_language_specific_title.localized(uiLocale, contentLanguageDisplayName)

        val builder =
            FeedBuilderInternal(
                baseUrl,
                "language/$contentLangCode",
                feedTitle,
                locale = uiLocale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                entries.addAll(mangaEntries.map { mangaAcqEntryToEntry(it, baseUrl, uiLocale) })
            }
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
                locale = locale,
                pageNum = pageNum,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                entries.addAll(
                    updateItems.map { item ->
                        val mangaDetails = OpdsMangaDetails(item.mangaId, item.mangaTitle, item.mangaThumbnailUrl, item.mangaAuthor)
                        createChapterListEntry(item.chapter, mangaDetails, baseUrl, true, locale)
                    },
                )
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    // --- Entry Creation Helpers ---

    private fun mangaAcqEntryToEntry(
        entry: OpdsMangaAcqEntry,
        baseUrl: String,
        locale: Locale,
    ): OpdsEntryXml {
        val displayThumbnailUrl = entry.thumbnailUrl?.let { proxyThumbnailUrl(entry.id) }
        return OpdsEntryXml(
            id = "urn:suwayomi:manga:${entry.id}",
            title = entry.title,
            updated = currentFormattedTime(),
            authors = entry.author?.let { listOf(OpdsAuthorXml(name = it)) },
            categories =
                entry.genres.filter { it.isNotBlank() }.map { genre ->
                    OpdsCategoryXml(
                        term = genre.lowercase().replace(" ", "_"),
                        label = genre,
                        scheme = "$baseUrl/genres",
                    )
                },
            summary = entry.description?.let { OpdsSummaryXml(value = it) },
            link =
                listOfNotNull(
                    OpdsLinkXml(
                        OpdsConstants.LINK_REL_SUBSECTION,
                        "$baseUrl/manga/${entry.id}?lang=${locale.toLanguageTag()}",
                        OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        entry.title,
                    ),
                    displayThumbnailUrl?.let { OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG) },
                    displayThumbnailUrl?.let { OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG) },
                ),
            language = entry.sourceLang,
        )
    }

    private fun createChapterListEntry(
        chapter: OpdsChapterListAcqEntry,
        manga: OpdsMangaDetails,
        baseUrl: String,
        addMangaTitle: Boolean,
        locale: Locale,
    ): OpdsEntryXml {
        val statusKey =
            when {
                chapter.read -> MR.strings.opds_chapter_status_read
                chapter.lastPageRead > 0 -> MR.strings.opds_chapter_status_in_progress
                else -> MR.strings.opds_chapter_status_unread
            }
        val titlePrefix = statusKey.localized(locale)
        val entryTitle = titlePrefix + (if (addMangaTitle) " ${manga.title}:" else "") + " ${chapter.name}"

        val details =
            buildString {
                append(MR.strings.opds_chapter_details_base.localized(locale, manga.title, chapter.name))
                chapter.scanlator?.takeIf { it.isNotBlank() }?.let {
                    append(
                        MR.strings.opds_chapter_details_scanlator.localized(locale, it),
                    )
                }
                if (chapter.pageCount > 0) {
                    append(
                        MR.strings.opds_chapter_details_progress.localized(
                            locale,
                            chapter.lastPageRead,
                            chapter.pageCount,
                        ),
                    )
                }
            }

        return OpdsEntryXml(
            id = "urn:suwayomi:chapter:${chapter.id}",
            title = entryTitle,
            updated = OpdsDateUtil.formatEpochMillisForOpds(chapter.uploadDate),
            authors =
                listOfNotNull(
                    manga.author?.let { OpdsAuthorXml(name = it) },
                    chapter.scanlator?.takeIf { it.isNotBlank() }?.let { OpdsAuthorXml(name = it) },
                ),
            summary = OpdsSummaryXml(value = details),
            link =
                listOf(
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                        href = "$baseUrl/manga/${manga.id}/chapter/${chapter.sourceOrder}/fetch?lang=${locale.toLanguageTag()}",
                        type = OpdsConstants.TYPE_ATOM_XML_ENTRY_PROFILE_OPDS,
                        title = MR.strings.opds_linktitle_view_chapter_details.localized(locale),
                    ),
                ),
        )
    }

    private suspend fun createChapterMetadataEntry(
        chapter: OpdsChapterMetadataAcqEntry,
        manga: OpdsMangaDetails,
        locale: Locale,
    ): OpdsEntryXml {
        val statusKey =
            when {
                chapter.downloaded -> MR.strings.opds_chapter_status_downloaded
                chapter.read -> MR.strings.opds_chapter_status_read
                chapter.lastPageRead > 0 -> MR.strings.opds_chapter_status_in_progress
                else -> MR.strings.opds_chapter_status_unread
            }
        val titlePrefix = statusKey.localized(locale)
        val entryTitle = "$titlePrefix ${chapter.name}"

        val details =
            buildString {
                append(MR.strings.opds_chapter_details_base.localized(locale, manga.title, chapter.name))
                chapter.scanlator?.takeIf { it.isNotBlank() }?.let {
                    append(
                        MR.strings.opds_chapter_details_scanlator.localized(locale, it),
                    )
                }
                val pageCountDisplay = chapter.pageCount.takeIf { it > 0 } ?: "?"
                append(
                    MR.strings.opds_chapter_details_progress.localized(
                        locale,
                        chapter.lastPageRead,
                        pageCountDisplay,
                    ),
                )
            }

        val links = mutableListOf<OpdsLinkXml>()
        var cbzFileSize: Long? = null

        if (chapter.downloaded) {
            val cbzStreamPair =
                withContext(
                    Dispatchers.IO,
                ) { runCatching { ChapterDownloadHelper.getArchiveStreamWithSize(manga.id, chapter.id) }.getOrNull() }
            cbzFileSize = cbzStreamPair?.second
            cbzStreamPair?.let {
                links.add(
                    OpdsLinkXml(
                        OpdsConstants.LINK_REL_ACQUISITION_OPEN_ACCESS,
                        "/api/v1/chapter/${chapter.id}/download?markAsRead=${serverConfig.opdsMarkAsReadOnDownload.value}",
                        OpdsConstants.TYPE_CBZ,
                        MR.strings.opds_linktitle_download_cbz.localized(locale),
                    ),
                )
            }
        }

        if (chapter.pageCount > 0) {
            links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_PSE_STREAM,
                    "/api/v1/manga/${manga.id}/chapter/${chapter.sourceOrder}/page/{pageNumber}?updateProgress=${serverConfig.opdsEnablePageReadProgress.value}",
                    OpdsConstants.TYPE_IMAGE_JPEG,
                    MR.strings.opds_linktitle_stream_pages.localized(locale),
                    pseCount = chapter.pageCount,
                    pseLastRead =
                        chapter.lastPageRead.takeIf {
                            it > 0
                        },
                    pseLastReadDate = chapter.lastReadAt.takeIf { it > 0 }?.let { OpdsDateUtil.formatEpochMillisForOpds(it * 1000) },
                ),
            )
            links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_IMAGE,
                    "/api/v1/manga/${manga.id}/chapter/${chapter.sourceOrder}/page/0",
                    OpdsConstants.TYPE_IMAGE_JPEG,
                    MR.strings.opds_linktitle_chapter_cover.localized(locale),
                ),
            )
        }

        return OpdsEntryXml(
            id = "urn:suwayomi:chapter:${chapter.id}:metadata",
            title = entryTitle,
            updated = OpdsDateUtil.formatEpochMillisForOpds(chapter.uploadDate),
            authors =
                listOfNotNull(
                    manga.author?.let { OpdsAuthorXml(name = it) },
                    chapter.scanlator?.takeIf { it.isNotBlank() }?.let { OpdsAuthorXml(name = it) },
                ),
            summary = OpdsSummaryXml(value = details),
            link = links,
            extent = cbzFileSize?.let { formatFileSizeForOpds(it) },
            format = if (cbzFileSize != null) "CBZ" else null,
        )
    }

    // --- Helpers & Internal Builder ---

    private fun addChapterSortAndFilterFacets(
        feedBuilder: FeedBuilderInternal,
        baseMangaUrl: String,
        currentSort: String,
        currentFilter: String,
        locale: Locale,
        sortColumn: org.jetbrains.exposed.sql.Column<*>,
        currentSortOrder: SortOrder,
    ) {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val filterGroup = MR.strings.opds_facetgroup_read_status.localized(locale)

        val addFacet = { rel: String, href: String, titleKey: StringResource, group: String, isActive: Boolean ->
            feedBuilder.links.add(
                OpdsLinkXml(
                    rel,
                    href,
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    titleKey.localized(locale),
                    facetGroup = group,
                    activeFacet = isActive,
                ),
            )
        }

        addFacet(
            OpdsConstants.LINK_REL_FACET,
            "$baseMangaUrl?sort=number_asc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_oldest_first,
            sortGroup,
            sortColumn == ChapterTable.sourceOrder && currentSortOrder == SortOrder.ASC,
        )
        addFacet(
            OpdsConstants.LINK_REL_FACET,
            "$baseMangaUrl?sort=number_desc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_newest_first,
            sortGroup,
            sortColumn == ChapterTable.sourceOrder && currentSortOrder == SortOrder.DESC,
        )
        addFacet(
            OpdsConstants.LINK_REL_FACET,
            "$baseMangaUrl?sort=date_asc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_date_asc,
            sortGroup,
            sortColumn == ChapterTable.date_upload && currentSortOrder == SortOrder.ASC,
        )
        addFacet(
            OpdsConstants.LINK_REL_FACET,
            "$baseMangaUrl?sort=date_desc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_date_desc,
            sortGroup,
            sortColumn == ChapterTable.date_upload && currentSortOrder == SortOrder.DESC,
        )

        addFacet(
            OpdsConstants.LINK_REL_FACET,
            "$baseMangaUrl?filter=all&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_all_chapters,
            filterGroup,
            currentFilter == "all",
        )
        addFacet(
            OpdsConstants.LINK_REL_FACET,
            "$baseMangaUrl?filter=unread&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_unread_only,
            filterGroup,
            currentFilter == "unread",
        )
        addFacet(
            OpdsConstants.LINK_REL_FACET,
            "$baseMangaUrl?filter=read&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_read_only,
            filterGroup,
            currentFilter == "read",
        )
    }

    private fun buildNotFoundFeed(
        baseUrl: String,
        idPath: String,
        title: String,
        locale: Locale,
    ): String =
        FeedBuilderInternal(baseUrl, idPath, title, locale, feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION, pageNum = null)
            .apply { totalResults = 0L }
            .build()
            .let(OpdsXmlUtil::serializeFeedToString)

    private class FeedBuilderInternal(
        val baseUrl: String,
        val idPath: String,
        val title: String,
        val locale: Locale,
        val feedType: String,
        var pageNum: Int? = 1, // Nullable, default to 1 if needed, null means no pagination
        var explicitQueryParams: String? = null,
        val currentSort: String? = null,
        val currentFilter: String? = null,
    ) {
        val feedGeneratedAt: String = currentFormattedTime()
        var totalResults: Long = 0
        var icon: String? = null
        val links = mutableListOf<OpdsLinkXml>()
        val entries = mutableListOf<OpdsEntryXml>()

        private fun buildUrlWithParams(
            baseHrefPath: String,
            page: Int?,
        ): String {
            val sb = StringBuilder("$baseUrl/$baseHrefPath")
            val queryParamsList = mutableListOf<String>()

            explicitQueryParams?.takeIf { it.isNotBlank() }?.let {
                queryParamsList.add(it)
            }
            // Only add pageNumber if pagination is active (pageNum is not null)
            page?.let {
                queryParamsList.add("pageNumber=$it")
            }

            currentSort?.let { queryParamsList.add("sort=$it") }
            currentFilter?.let { queryParamsList.add("filter=$it") }
            queryParamsList.add("lang=${locale.toLanguageTag()}")

            if (queryParamsList.isNotEmpty()) {
                sb.append("?").append(queryParamsList.joinToString("&"))
            }
            return sb.toString()
        }

        fun build(): OpdsFeedXml {
            val actualPageNum = pageNum ?: 1
            // val needsPagination = pageNum != null && totalResults > opdsItemsPerPageBounded

            val selfLinkHref = buildUrlWithParams(idPath, if (pageNum != null) actualPageNum else null)
            val feedLinks = mutableListOf<OpdsLinkXml>()
            feedLinks.addAll(this.links)

            feedLinks.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_SELF,
                    selfLinkHref,
                    feedType,
                    MR.strings.opds_linktitle_self_feed.localized(locale),
                ),
            )
            feedLinks.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_START,
                    "$baseUrl?lang=${locale.toLanguageTag()}",
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_linktitle_catalog_root.localized(locale),
                ),
            )
            feedLinks.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_SEARCH,
                    "$baseUrl/search?lang=${locale.toLanguageTag()}",
                    OpdsConstants.TYPE_OPENSEARCH_DESCRIPTION,
                    MR.strings.opds_linktitle_search_catalog.localized(locale),
                ),
            )

            if (pageNum != null) { // Only add pagination links if pageNum was provided (meaning it's paginatable)
                if (actualPageNum > 1) {
                    feedLinks.add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_PREV,
                            buildUrlWithParams(idPath, actualPageNum - 1),
                            feedType,
                            MR.strings.opds_linktitle_previous_page.localized(locale),
                        ),
                    )
                }
                if (totalResults > actualPageNum * opdsItemsPerPageBounded) {
                    feedLinks.add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_NEXT,
                            buildUrlWithParams(idPath, actualPageNum + 1),
                            feedType,
                            MR.strings.opds_linktitle_next_page.localized(locale),
                        ),
                    )
                }
            }

            val urnParams = mutableListOf<String>()
            urnParams.add(locale.toLanguageTag())
            pageNum?.let { urnParams.add("page$it") }
            explicitQueryParams?.let { urnParams.add(it.replace("&", ":").replace("=", "_")) }
            currentSort?.let { urnParams.add("sort_$it") }
            currentFilter?.let { urnParams.add("filter_$it") }
            val urnSuffix = if (urnParams.isNotEmpty()) ":${urnParams.joinToString(":")}" else ""

            val showPaginationFields = pageNum != null && totalResults > 0

            return OpdsFeedXml(
                id = "urn:suwayomi:feed:${idPath.replace('/',':')}$urnSuffix",
                title = title,
                updated = feedGeneratedAt,
                icon = icon,
                author = feedAuthor,
                links = feedLinks,
                entries = entries,
                totalResults = totalResults.takeIf { showPaginationFields },
                itemsPerPage = if (showPaginationFields) opdsItemsPerPageBounded else null,
                startIndex = if (showPaginationFields) ((actualPageNum - 1) * opdsItemsPerPageBounded + 1) else null,
            )
        }
    }
}
