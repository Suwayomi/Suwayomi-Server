package suwayomi.tachidesk.opds.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.i18n.LocalizationService
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper.getArchiveStreamWithSize
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReady
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.model.OpdsAuthorXml
import suwayomi.tachidesk.opds.model.OpdsCategoryXml
import suwayomi.tachidesk.opds.model.OpdsContentXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsFeedXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.model.OpdsSummaryXml
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodeForOpdsURL
import suwayomi.tachidesk.opds.util.OpdsStringUtil.formatFileSizeForOpds
import suwayomi.tachidesk.opds.util.OpdsXmlUtil
import suwayomi.tachidesk.server.serverConfig

object OpdsFeedBuilder {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

    private val formattedNow: String get() = OpdsDateUtil.formatCurrentInstantForOpds()

    fun getRootFeed(
        baseUrl: String,
        langCode: String,
    ): String {
        val rootSections =
            mapOf(
                "mangas" to (OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION to "opds.feeds.allManga"),
                "sources" to (OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION to "opds.feeds.sources"),
                "categories" to (OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION to "opds.feeds.categories"),
                "genres" to (OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION to "opds.feeds.genres"),
                "status" to (OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION to "opds.feeds.status"),
                "languages" to (OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION to "opds.feeds.languages"),
                "library-updates" to (OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION to "opds.feeds.libraryUpdates"),
            )

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = 1,
                idWithoutParams = "root",
                titleKeyOrActual = "opds.feeds.root",
                langCode = langCode,
                isPaginated = false,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = rootSections.size.toLong()
                entries +=
                    rootSections.map { (id, typeAndTitleKey) ->
                        val (entryLinkType, titleKeyBase) = typeAndTitleKey
                        val localizedTitle =
                            LocalizationService.getString(
                                langCode,
                                "$titleKeyBase.title",
                                defaultValue = id.replaceFirstChar { it.titlecase() },
                            )
                        OpdsEntryXml(
                            id = "section_$id",
                            title = localizedTitle,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                                        href = "$baseUrl/$id?lang=$langCode",
                                        type = entryLinkType,
                                        title = localizedTitle,
                                    ),
                                ),
                            content =
                                OpdsContentXml(
                                    type = "text",
                                    value =
                                        LocalizationService.getString(
                                            langCode,
                                            "$titleKeyBase.description",
                                            defaultValue = "Browse $localizedTitle",
                                        ),
                                ),
                        )
                    }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getMangasFeed(
        criteria: OpdsSearchCriteria?,
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val (mangas, total) =
            transaction {
                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .join(SourceTable, JoinType.INNER, onColumn = MangaTable.sourceReference, otherColumn = SourceTable.id)
                        .select(MangaTable.columns + SourceTable.lang)
                        .where {
                            val conditions = mutableListOf<Op<Boolean>>()
                            conditions += (MangaTable.inLibrary eq true)
                            criteria?.query?.takeIf { it.isNotBlank() }?.let { q ->
                                val lowerQ = q.lowercase()
                                conditions += (
                                    (MangaTable.title.lowerCase() like "%$lowerQ%") or
                                        (MangaTable.author.lowerCase() like "%$lowerQ%") or
                                        (MangaTable.genre.lowerCase() like "%$lowerQ%")
                                )
                            }
                            criteria?.author?.takeIf { it.isNotBlank() }?.let { author ->
                                conditions += (MangaTable.author.lowerCase() like "%${author.lowercase()}%")
                            }
                            criteria?.title?.takeIf { it.isNotBlank() }?.let { title ->
                                conditions += (MangaTable.title.lowerCase() like "%${title.lowercase()}%")
                            }
                            conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
                        }.groupBy(MangaTable.id, SourceTable.lang)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangasResult =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map { MangaTable.toDataClass(it, includeMangaMeta = false).copy(sourceLang = it[SourceTable.lang]) }
                Pair(mangasResult, totalCount)
            }

        val feedId = "mangas"
        val titleKey = if (criteria == null) "opds.feeds.allManga" else "opds.feeds.searchResults"
        val searchQuery =
            criteria
                ?.let { c ->
                    listOfNotNull(
                        c.query?.let { "query=${it.encodeForOpdsURL()}" },
                        c.author?.let { "author=${it.encodeForOpdsURL()}" },
                        c.title?.let { "title=${it.encodeForOpdsURL()}" },
                    ).joinToString("&")
                }.takeIf { it?.isNotBlank() == true }

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = pageNum,
                idWithoutParams = feedId,
                titleKeyOrActual = titleKey,
                searchQuery = criteria?.query,
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = total
                this.explicitQueryParams = searchQuery
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow, langCode) }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getSourcesFeed(
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val (sourceList, totalCount) =
            transaction {
                val query =
                    SourceTable
                        .join(MangaTable, JoinType.INNER) { MangaTable.sourceReference eq SourceTable.id }
                        .join(ChapterTable, JoinType.INNER) { ChapterTable.manga eq MangaTable.id }
                        .join(ExtensionTable, JoinType.LEFT, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                        .select(SourceTable.columns + ExtensionTable.apkName)
                        .groupBy(SourceTable.id, ExtensionTable.apkName)
                        .orderBy(SourceTable.name to SortOrder.ASC)
                val total = query.count()
                val sources =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map {
                            SourceDataClass(
                                id = it[SourceTable.id].value.toString(),
                                name = it[SourceTable.name],
                                lang = it[SourceTable.lang],
                                iconUrl = it[ExtensionTable.apkName]?.let { apkName -> getExtensionIconUrl(apkName) } ?: "",
                                supportsLatest = false,
                                isConfigurable = false,
                                isNsfw = it[SourceTable.isNsfw],
                                displayName = it[SourceTable.name],
                            )
                        }
                Pair(sources, total)
            }

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = pageNum,
                idWithoutParams = "sources",
                titleKeyOrActual = "opds.feeds.sources",
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = totalCount
                entries +=
                    sourceList.map {
                        OpdsEntryXml(
                            id = "source_nav_${it.id}",
                            title = it.name,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                                        href = "$baseUrl/source/${it.id}?lang=$langCode",
                                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                        title = it.name,
                                    ),
                                ),
//                            icon = it.iconUrl.takeIf { url -> url.isNotBlank() },
                        )
                    }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getCategoriesFeed(
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val (categoryList, total) =
            transaction {
                val query =
                    CategoryTable
                        .join(CategoryMangaTable, JoinType.INNER, onColumn = CategoryTable.id, otherColumn = CategoryMangaTable.category)
                        .join(MangaTable, JoinType.INNER, onColumn = CategoryMangaTable.manga, otherColumn = MangaTable.id)
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(CategoryTable.id, CategoryTable.name)
                        .groupBy(CategoryTable.id)
                        .orderBy(CategoryTable.order to SortOrder.ASC)
                val totalCount = query.count()
                val paginated =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map { row -> Pair(row[CategoryTable.id].value, row[CategoryTable.name]) }
                Pair(paginated, totalCount)
            }

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = pageNum,
                idWithoutParams = "categories",
                titleKeyOrActual = "opds.feeds.categories",
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = total
                entries +=
                    categoryList.map { (id, name) ->
                        val categoryTitle = LocalizationService.getString(langCode, "category.$id.name", defaultValue = name)
                        OpdsEntryXml(
                            id = "category_nav_$id",
                            title = categoryTitle,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                                        href = "$baseUrl/category/$id?lang=$langCode",
                                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                        title = categoryTitle,
                                    ),
                                ),
                        )
                    }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getGenresFeed(
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val genres =
            transaction {
                MangaTable
                    .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                    .select(MangaTable.genre)
                    .mapNotNull { it[MangaTable.genre] }
                    .flatMap { it.split(",").map(String::trim).filterNot(String::isBlank) }
                    .distinct()
                    .sorted()
            }

        val totalCount = genres.size.toLong()
        val fromIndex = ((pageNum - 1) * opdsItemsPerPageBounded)
        val toIndex = minOf(fromIndex + opdsItemsPerPageBounded, genres.size)
        val paginatedGenres = if (fromIndex < genres.size) genres.subList(fromIndex, toIndex) else emptyList()

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = pageNum,
                idWithoutParams = "genres",
                titleKeyOrActual = "opds.feeds.genres",
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                this.totalResults = totalCount
                entries +=
                    paginatedGenres.map { genre ->
                        val localizedGenre =
                            LocalizationService.getString(
                                langCode,
                                "genre.${genre.lowercase().replace(" ", "_")}",
                                defaultValue = genre,
                            )
                        OpdsEntryXml(
                            id = "genre_nav_${genre.encodeForOpdsURL()}",
                            title = localizedGenre,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                                        href = "$baseUrl/genre/${genre.encodeForOpdsURL()}?lang=$langCode",
                                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                        title = localizedGenre,
                                    ),
                                ),
                        )
                    }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getStatusFeed(
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val statuses = MangaStatus.entries.sortedBy { it.value }
        val totalCount = statuses.size.toLong()
        val isStatusListPaginated = totalCount > opdsItemsPerPageBounded
        val currentPageNum = if (isStatusListPaginated) pageNum else 1
        val fromIndex = ((currentPageNum - 1) * opdsItemsPerPageBounded)
        val toIndex = minOf(fromIndex + opdsItemsPerPageBounded, statuses.size)
        val paginatedStatuses = if (fromIndex < statuses.size) statuses.subList(fromIndex, toIndex) else emptyList()

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = currentPageNum,
                idWithoutParams = "status",
                titleKeyOrActual = "opds.feeds.status",
                langCode = langCode,
                isPaginated = isStatusListPaginated,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = totalCount
                entries +=
                    paginatedStatuses.map { status ->
                        val statusTitle =
                            LocalizationService.getString(
                                langCode,
                                "manga.status.${status.name.lowercase()}",
                                defaultValue =
                                    status.name
                                        .lowercase()
                                        .replace('_', ' ')
                                        .replaceFirstChar { it.uppercase() },
                            )
                        OpdsEntryXml(
                            id = "status_nav_val_${status.value}",
                            title = statusTitle,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                                        href = "$baseUrl/status/${status.value}?lang=$langCode",
                                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                        title = statusTitle,
                                    ),
                                ),
                        )
                    }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getLanguagesFeed(
        baseUrl: String,
        uiLangCode: String,
    ): String {
        val contentLanguages =
            transaction {
                SourceTable
                    .join(MangaTable, JoinType.INNER, onColumn = SourceTable.id, otherColumn = MangaTable.sourceReference)
                    .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                    .select(SourceTable.lang)
                    .groupBy(SourceTable.lang)
                    .map { row -> row[SourceTable.lang] }
                    .sorted()
            }

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = 1,
                idWithoutParams = "languages",
                titleKeyOrActual = "opds.feeds.languages",
                langCode = uiLangCode,
                isPaginated = false,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
            ).apply {
                totalResults = contentLanguages.size.toLong()
                entries +=
                    contentLanguages.map { contentLang ->
                        val contentLangTitle =
                            LocalizationService.getString(
                                uiLangCode,
                                "language.$contentLang",
                                defaultValue = contentLang.uppercase(),
                            )
                        OpdsEntryXml(
                            id = "language_nav_code_$contentLang",
                            title = contentLangTitle,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsLinkXml(
                                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                                        href = "$baseUrl/language/$contentLang?lang=$uiLangCode",
                                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                                        title = contentLangTitle,
                                    ),
                                ),
                        )
                    }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getMangaFeed(
        mangaId: Int,
        baseUrl: String,
        pageNum: Int,
        sortParam: String?,
        filterParam: String?,
        langCode: String,
    ): String {
        val (orderColumn, currentSortOrder) =
            when (sortParam?.lowercase()) {
                "asc", "number_asc" -> ChapterTable.sourceOrder to SortOrder.ASC
                "desc", "number_desc" -> ChapterTable.sourceOrder to SortOrder.DESC
                "date_asc" -> ChapterTable.date_upload to SortOrder.ASC
                "date_desc" -> ChapterTable.date_upload to SortOrder.DESC
                else -> ChapterTable.sourceOrder to serverConfig.opdsChapterSortOrder.value
            }

        val currentFilter = filterParam?.lowercase() ?: if (serverConfig.opdsShowOnlyUnreadChapters.value) "unread" else "all"

        val (manga, chapters, totalCount) =
            transaction {
                val mangaEntry =
                    MangaTable.selectAll().where { MangaTable.id eq mangaId }.firstOrNull()
                        ?: return@transaction Triple(null, emptyList<ChapterDataClass>(), 0L)
                val mangaData = MangaTable.toDataClass(mangaEntry, includeMangaMeta = false)

                val chapterConditions = mutableListOf<Op<Boolean>>()
                chapterConditions.add(ChapterTable.manga eq mangaId)
                when (currentFilter) {
                    "unread" -> chapterConditions.add(ChapterTable.isRead eq false)
                    "read" -> chapterConditions.add(ChapterTable.isRead eq true)
                }
                if (serverConfig.opdsShowOnlyDownloadedChapters.value) {
                    chapterConditions.add(ChapterTable.isDownloaded eq true)
                }
                val finalCondition = chapterConditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
                val chaptersQuery = ChapterTable.selectAll().where(finalCondition).orderBy(orderColumn to currentSortOrder)
                val total = chaptersQuery.count()
                val chaptersData =
                    chaptersQuery
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map { ChapterTable.toDataClass(it, includeChapterCount = false, includeChapterMeta = false) }
                Triple(mangaData, chaptersData, total)
            }

        if (manga == null) {
            val errorTitle = LocalizationService.getString(langCode, "opds.error.mangaNotFound", defaultValue = "Manga not found")
            return FeedBuilder(
                baseUrl,
                1,
                "manga/$mangaId/error",
                errorTitle,
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply { totalResults = 0 }
                .build()
                .let(OpdsXmlUtil::serializeFeedToString)
        }

        val feedTitle =
            LocalizationService.getString(
                langCode,
                "opds.feeds.mangaChapters",
                manga.title,
                defaultValue = "${manga.title} Chapters",
            )
        val actualSortParamForBuilder =
            sortParam
                ?: ((if (orderColumn == ChapterTable.sourceOrder) "number_" else "date_") + currentSortOrder.name.lowercase())

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = pageNum,
                idWithoutParams = "manga/$mangaId",
                titleKeyOrActual = feedTitle,
                currentSort = actualSortParamForBuilder,
                currentFilter = currentFilter,
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                this.totalResults = totalCount
                this.icon = manga.thumbnailUrl
                manga.thumbnailUrl?.let { url ->
                    links += OpdsLinkXml(rel = OpdsConstants.LINK_REL_IMAGE, href = url, type = OpdsConstants.TYPE_IMAGE_JPEG)
                    links +=
                        OpdsLinkXml(rel = OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, href = url, type = OpdsConstants.TYPE_IMAGE_JPEG)
                }

                val baseMangaUrlForFacets = "$baseUrl/manga/$mangaId"
                val sortFacetGroup =
                    LocalizationService.getString(
                        langCode,
                        "opds.facetgroup.sortOrder",
                        defaultValue = "Sort Order",
                    )

                // Number-based sort facets
                links +=
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_FACET,
                        href = "$baseMangaUrlForFacets?sort=number_asc&filter=$currentFilter&lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title =
                            LocalizationService.getString(
                                langCode,
                                "opds.facet.sort.oldestFirst",
                                defaultValue = "Oldest First",
                            ),
                        facetGroup = sortFacetGroup,
                        activeFacet = (currentSortOrder == SortOrder.ASC && orderColumn == ChapterTable.sourceOrder),
                    )
                links +=
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_FACET,
                        href = "$baseMangaUrlForFacets?sort=number_desc&filter=$currentFilter&lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title =
                            LocalizationService.getString(
                                langCode,
                                "opds.facet.sort.newestFirst",
                                defaultValue = "Newest First",
                            ),
                        facetGroup = sortFacetGroup,
                        activeFacet = (currentSortOrder == SortOrder.DESC && orderColumn == ChapterTable.sourceOrder),
                    )

                // Date-based sort facets
                links +=
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_FACET,
                        href = "$baseMangaUrlForFacets?sort=date_asc&filter=$currentFilter&lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title =
                            LocalizationService.getString(
                                langCode,
                                "opds.facet.sort.dateAsc",
                                defaultValue = "Date ascending",
                            ),
                        facetGroup = sortFacetGroup,
                        activeFacet = (currentSortOrder == SortOrder.ASC && orderColumn == ChapterTable.date_upload),
                    )
                links +=
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_FACET,
                        href = "$baseMangaUrlForFacets?sort=date_desc&filter=$currentFilter&lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title =
                            LocalizationService.getString(
                                langCode,
                                "opds.facet.sort.dateDesc",
                                defaultValue = "Date descending",
                            ),
                        facetGroup = sortFacetGroup,
                        activeFacet = (currentSortOrder == SortOrder.DESC && orderColumn == ChapterTable.date_upload),
                    )

                // Filter facets
                val filterFacetGroup =
                    LocalizationService.getString(
                        langCode,
                        "opds.facetgroup.readStatus",
                        defaultValue = "Read Status",
                    )
                links +=
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_FACET,
                        href = "$baseMangaUrlForFacets?filter=all&sort=$actualSortParamForBuilder&lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title =
                            LocalizationService.getString(
                                langCode,
                                "opds.facet.filter.allChapters",
                                defaultValue = "All Chapters",
                            ),
                        facetGroup = filterFacetGroup,
                        activeFacet = (currentFilter == "all"),
                    )
                links +=
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_FACET,
                        href = "$baseMangaUrlForFacets?filter=unread&sort=$actualSortParamForBuilder&lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title =
                            LocalizationService.getString(
                                langCode,
                                "opds.facet.filter.unreadOnly",
                                defaultValue = "Unread Only",
                            ),
                        facetGroup = filterFacetGroup,
                        activeFacet = (currentFilter == "unread"),
                    )
                links +=
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_FACET,
                        href = "$baseMangaUrlForFacets?filter=read&sort=$actualSortParamForBuilder&lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title =
                            LocalizationService.getString(
                                langCode,
                                "opds.facet.filter.readOnly",
                                defaultValue = "Read Only",
                            ),
                        facetGroup = filterFacetGroup,
                        activeFacet = (currentFilter == "read"),
                    )
                entries += chapters.map { createChapterEntry(it, manga, baseUrl, isMetaDataEntry = false, langCode = langCode) }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    suspend fun getChapterMetadataFeed(
        mangaId: Int,
        chapterIndexFromPath: Int,
        baseUrl: String,
        langCode: String,
    ): String {
        // 1. Get manga data
        val mangaData =
            withContext(Dispatchers.IO) {
                transaction {
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .firstOrNull()
                        ?.let { MangaTable.toDataClass(it, includeMangaMeta = false) }
                }
            }

        if (mangaData == null) {
            val errorTitle =
                LocalizationService.getString(
                    langCode,
                    "opds.error.mangaNotFound",
                    mangaId.toString(),
                    defaultValue = "Manga with ID $mangaId not found",
                )
            return FeedBuilder(
                baseUrl,
                1,
                "manga/$mangaId/chapter/$chapterIndexFromPath/error",
                errorTitle,
                langCode = langCode,
                isPaginated = false,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply { totalResults = 0 }
                .build()
                .let(OpdsXmlUtil::serializeFeedToString)
        }

        // 2. Get chapter data using mangaId and chapterIndexFromPath (sourceOrder)
        val updatedChapterData =
            try {
                getChapterDownloadReady(chapterIndex = chapterIndexFromPath, mangaId = mangaData.id)
            } catch (e: Exception) {
                val errorTitle =
                    LocalizationService.getString(
                        langCode,
                        "opds.error.chapterNotFoundForManga",
                        chapterIndexFromPath.toString(),
                        mangaData.title,
                        defaultValue = "Chapter with index $chapterIndexFromPath not found for manga \"${mangaData.title}\"",
                    )
                return FeedBuilder(
                    baseUrl,
                    1,
                    "manga/$mangaId/chapter/$chapterIndexFromPath/error",
                    errorTitle,
                    langCode = langCode,
                    isPaginated = false,
                    feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                ).apply { totalResults = 0 }
                    .build()
                    .let(OpdsXmlUtil::serializeFeedToString)
            }

        // 3. Create feed entry for this chapter
        val updatedEntry = createChapterEntry(updatedChapterData, mangaData, baseUrl, isMetaDataEntry = true, langCode = langCode)

        // 4. Build the feed
        val feedTitle =
            LocalizationService.getString(
                langCode,
                "opds.feeds.chapterDetails",
                mangaData.title,
                updatedChapterData.name,
                defaultValue = "${mangaData.title} | ${updatedChapterData.name} | Details",
            )

        val builder =
            FeedBuilder(
                baseUrl = baseUrl,
                pageNum = 1,
                idWithoutParams = "manga/$mangaId/chapter/$chapterIndexFromPath/details",
                titleKeyOrActual = feedTitle,
                langCode = langCode,
                isPaginated = false,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                totalResults = 1
                icon = mangaData.thumbnailUrl
                mangaData.thumbnailUrl?.let { url ->
                    links += OpdsLinkXml(rel = OpdsConstants.LINK_REL_IMAGE, href = url, type = OpdsConstants.TYPE_IMAGE_JPEG)
                    links +=
                        OpdsLinkXml(rel = OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, href = url, type = OpdsConstants.TYPE_IMAGE_JPEG)
                }
                entries += listOf(updatedEntry)
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    private fun createChapterEntry(
        chapter: ChapterDataClass,
        manga: MangaDataClass,
        baseUrl: String,
        isMetaDataEntry: Boolean,
        addMangaTitleInEntry: Boolean = false,
        langCode: String,
    ): OpdsEntryXml {
        val chapterStatusKey =
            when {
                isMetaDataEntry && chapter.downloaded -> "opds.chapter.status.downloaded"
                chapter.read -> "opds.chapter.status.read"
                chapter.lastPageRead > 0 -> "opds.chapter.status.inProgress"
                else -> "opds.chapter.status.unread"
            }
        val entryTitlePrefix =
            LocalizationService.getString(
                langCode,
                chapterStatusKey,
                defaultValue = chapterStatusKey.substringAfterLast('.').uppercase(),
            )
        val entryTitle = entryTitlePrefix + (if (addMangaTitleInEntry) "${manga.title}: " else "") + "${chapter.name}"

        val chapterDetails =
            buildString {
                append(
                    LocalizationService.getString(
                        langCode,
                        "opds.chapter.details.base",
                        manga.title,
                        chapter.name,
                        defaultValue = "Manga: ${manga.title}, Chapter: ${chapter.name}",
                    ),
                )
                chapter.scanlator?.takeIf { it.isNotBlank() }?.let { scanlatorName ->
                    append(
                        LocalizationService.getString(
                            langCode,
                            "opds.chapter.details.scanlator",
                            scanlatorName,
                            defaultValue = " | Scanlator: $scanlatorName",
                        ),
                    )
                }
                if (isMetaDataEntry || chapter.pageCount > 0) {
                    val pageCountDisplay = chapter.pageCount.takeIf { it > 0 } ?: "?"
                    append(
                        LocalizationService.getString(
                            langCode,
                            "opds.chapter.details.progress",
                            chapter.lastPageRead.toString(),
                            pageCountDisplay.toString(),
                            defaultValue = " | Progress: ${chapter.lastPageRead}/$pageCountDisplay",
                        ),
                    )
                }
            }

        val links = mutableListOf<OpdsLinkXml>()
        val cbzInputStreamPair =
            if (isMetaDataEntry && chapter.downloaded) {
                runCatching { getArchiveStreamWithSize(manga.id, chapter.id) }.getOrNull()
            } else {
                null
            }

        if (isMetaDataEntry) {
            cbzInputStreamPair?.let {
                links.add(
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_ACQUISITION_OPEN_ACCESS,
                        href = "/api/v1/chapter/${chapter.id}/download?markAsRead=${serverConfig.opdsMarkAsReadOnDownload.value}",
                        type = OpdsConstants.TYPE_CBZ,
                        title = LocalizationService.getString(langCode, "opds.linktitle.downloadCbz"),
                    ),
                )
            }
            val pageCountForPse = chapter.pageCount.takeIf { it > 0 }
            if (pageCountForPse != null) {
                val pageUrl =
                    "/api/v1/manga/${manga.id}/chapter/${chapter.index}/page/{pageNumber}" +
                        "?updateProgress=${serverConfig.opdsEnablePageReadProgress.value}"
                links.add(
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_PSE_STREAM,
                        href = pageUrl,
                        type = OpdsConstants.TYPE_IMAGE_JPEG,
                        title = LocalizationService.getString(langCode, "opds.linktitle.streamPages"),
                        pseCount = pageCountForPse,
                        pseLastRead = chapter.lastPageRead.takeIf { it > 0 },
                        pseLastReadDate =
                            chapter.lastReadAt.takeIf { it > 0 }?.let {
                                OpdsDateUtil.formatEpochMillisForOpds(it * 1000) // lastReadAt is in seconds
                            },
                    ),
                )
                links.add(
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_IMAGE,
                        href = "/api/v1/manga/${manga.id}/chapter/${chapter.index}/page/0",
                        type = OpdsConstants.TYPE_IMAGE_JPEG,
                        title = LocalizationService.getString(langCode, "opds.linktitle.chapterCover"),
                    ),
                )
            }
        } else {
            links.add(
                OpdsLinkXml(
                    rel = OpdsConstants.LINK_REL_SUBSECTION,
                    href = "$baseUrl/manga/${manga.id}/chapter/${chapter.index}/fetch?lang=$langCode",
                    type = OpdsConstants.TYPE_ATOM_XML_ENTRY_PROFILE_OPDS,
                    title =
                        LocalizationService.getString(
                            langCode,
                            "opds.linktitle.viewChapterDetails",
                            defaultValue = "View Chapter Details & Get Pages",
                        ),
                ),
            )
        }

        return OpdsEntryXml(
            id = "urn:suwayomi:chapter:${chapter.id}",
            title = entryTitle,
            updated = OpdsDateUtil.formatEpochMillisForOpds(chapter.uploadDate),
            authors =
                listOfNotNull(
                    manga.author?.let { OpdsAuthorXml(name = it) },
                    chapter.scanlator?.takeIf { it.isNotBlank() }?.let {
                        OpdsAuthorXml(
                            name =
                            it,
                        )
                    },
                ),
            summary = OpdsSummaryXml(value = chapterDetails),
//            content = OpdsContentXml(value = chapterDetails),
            link = links,
            extent = cbzInputStreamPair?.second?.let { formatFileSizeForOpds(it) },
            format = cbzInputStreamPair?.second?.let { "CBZ" },
        )
    }

    fun getSourceFeed(
        sourceId: Long,
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val (mangas, total, sourceInfo) =
            transaction {
                val sRow =
                    SourceTable
                        .join(ExtensionTable, JoinType.LEFT, SourceTable.extension, ExtensionTable.id)
                        .select(SourceTable.name, ExtensionTable.apkName, SourceTable.lang)
                        .where { SourceTable.id eq sourceId }
                        .firstOrNull()
                val sourceName = sRow?.get(SourceTable.name)
                val iconApkName = sRow?.get(ExtensionTable.apkName)
                val sourceContentLang = sRow?.get(SourceTable.lang)

                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where { MangaTable.sourceReference eq sourceId }
                        .groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val paginatedResults =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map { MangaTable.toDataClass(it, includeMangaMeta = false).copy(sourceLang = sourceContentLang) }
                Triple(paginatedResults, totalCount, Triple(sourceName, iconApkName, sourceContentLang))
            }

        val (sourceName, iconApkName, _) = sourceInfo
        val feedActualTitle =
            sourceName ?: LocalizationService.getString(
                langCode,
                "opds.source.unknown",
                sourceId.toString(),
                defaultValue = "Source $sourceId",
            )
        val builder =
            FeedBuilder(
                baseUrl,
                pageNum,
                "source/$sourceId",
                feedActualTitle,
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                this.totalResults = total
                this.icon = iconApkName?.let { getExtensionIconUrl(it) }
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow, langCode) }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getCategoryFeed(
        categoryId: Int,
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val (mangas, total, categoryNameFromDb) =
            transaction {
                val catRow = CategoryTable.selectAll().where { CategoryTable.id eq categoryId }.firstOrNull()
                if (catRow == null) {
                    Triple(
                        emptyList(),
                        0L,
                        LocalizationService.getString(
                            langCode,
                            "opds.category.unknown",
                            categoryId.toString(),
                            defaultValue = "Unknown Category",
                        ),
                    )
                } else {
                    val cName = catRow[CategoryTable.name]
                    val query =
                        CategoryMangaTable
                            .join(MangaTable, JoinType.INNER, CategoryMangaTable.manga, MangaTable.id)
                            .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                            .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                            .select(MangaTable.columns + SourceTable.lang)
                            .where { CategoryMangaTable.category eq categoryId }
                            .groupBy(MangaTable.id, SourceTable.lang)
                            .orderBy(MangaTable.title to SortOrder.ASC)
                    val totalCount = query.count()
                    val mangaResults =
                        query
                            .limit(opdsItemsPerPageBounded)
                            .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                            .map { MangaTable.toDataClass(it, includeMangaMeta = false).copy(sourceLang = it[SourceTable.lang]) }
                    Triple(mangaResults, totalCount, cName)
                }
            }
        val localizedCategoryName = LocalizationService.getString(langCode, "category.$categoryId.name", defaultValue = categoryNameFromDb)
        val feedTitle =
            LocalizationService.getString(
                langCode,
                "opds.feeds.categorySpecific",
                localizedCategoryName,
                defaultValue = "Category: $localizedCategoryName",
            )
        val builder =
            FeedBuilder(
                baseUrl,
                pageNum,
                "category/$categoryId",
                feedTitle,
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                this.totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow, langCode) }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getGenreFeed(
        genre: String,
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val (mangas, total) =
            transaction {
                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                        .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                        .select(MangaTable.columns + SourceTable.lang)
                        .where { MangaTable.genre like "%$genre%" }
                        .groupBy(MangaTable.id, SourceTable.lang)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangaResults =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map { MangaTable.toDataClass(it, includeMangaMeta = false).copy(sourceLang = it[SourceTable.lang]) }
                Pair(mangaResults, totalCount)
            }
        val localizedGenre =
            LocalizationService.getString(
                langCode,
                "genre.${genre.lowercase().replace(" ", "_")}",
                defaultValue = genre,
            )
        val feedTitle =
            LocalizationService.getString(
                langCode,
                "opds.feeds.genreSpecific",
                localizedGenre,
                defaultValue = "Genre: $localizedGenre",
            )
        val builder =
            FeedBuilder(
                baseUrl,
                pageNum,
                "genre/${genre.encodeForOpdsURL()}",
                feedTitle,
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                this.totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow, langCode) }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getStatusMangaFeed(
        statusId: Long,
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val statusEnum = MangaStatus.valueOf(statusId.toInt())
        val statusName =
            LocalizationService.getString(
                langCode,
                "manga.status.${statusEnum.name.lowercase()}",
                defaultValue = statusEnum.name.lowercase().replaceFirstChar { it.uppercase() },
            )
        val (mangas, total) =
            transaction {
                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                        .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                        .select(MangaTable.columns + SourceTable.lang)
                        .where { MangaTable.status eq statusId.toInt() }
                        .groupBy(MangaTable.id, SourceTable.lang)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangaResults =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map { MangaTable.toDataClass(it, includeMangaMeta = false).copy(sourceLang = it[SourceTable.lang]) }
                Pair(mangaResults, totalCount)
            }
        val feedTitle =
            LocalizationService.getString(
                langCode,
                "opds.feeds.statusSpecific",
                statusName,
                defaultValue = "Status: $statusName",
            )
        val builder =
            FeedBuilder(
                baseUrl,
                pageNum,
                "status/$statusId",
                feedTitle,
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                this.totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow, langCode) }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getLanguageFeed(
        contentLangCode: String,
        baseUrl: String,
        pageNum: Int,
        uiLangCode: String,
    ): String {
        val (mangas, total) =
            transaction {
                val query =
                    SourceTable
                        .join(MangaTable, JoinType.INNER, SourceTable.id, MangaTable.sourceReference)
                        .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                        .select(MangaTable.columns + SourceTable.lang)
                        .where { SourceTable.lang eq contentLangCode }
                        .groupBy(MangaTable.id, SourceTable.lang)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangaResults =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map { MangaTable.toDataClass(it, includeMangaMeta = false).copy(sourceLang = it[SourceTable.lang]) }
                Pair(mangaResults, totalCount)
            }
        val languageDisplayName =
            LocalizationService.getString(
                uiLangCode,
                "language.$contentLangCode",
                defaultValue = contentLangCode.uppercase(),
            )
        val feedTitle =
            LocalizationService.getString(
                uiLangCode,
                "opds.feeds.languageSpecific",
                languageDisplayName,
                defaultValue = "Language: $languageDisplayName",
            )
        val builder =
            FeedBuilder(
                baseUrl,
                pageNum,
                "language/$contentLangCode",
                feedTitle,
                langCode = uiLangCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                this.totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow, uiLangCode) }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    fun getLibraryUpdatesFeed(
        baseUrl: String,
        pageNum: Int,
        langCode: String,
    ): String {
        val (chapterToMangaMap, total) =
            transaction {
                val query =
                    ChapterTable
                        .join(MangaTable, JoinType.INNER, ChapterTable.manga, MangaTable.id)
                        .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                        .selectAll()
                        .where { MangaTable.inLibrary eq true }
                        .orderBy(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                val totalCount = query.count()
                val chapters =
                    query
                        .limit(opdsItemsPerPageBounded)
                        .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                        .map {
                            ChapterTable.toDataClass(it, includeChapterCount = false, includeChapterMeta = false) to
                                MangaTable.toDataClass(it, includeMangaMeta = false).copy(sourceLang = it[SourceTable.lang])
                        }
                Pair(chapters, totalCount)
            }
        val builder =
            FeedBuilder(
                baseUrl,
                pageNum,
                "library-updates",
                "opds.feeds.libraryUpdates",
                langCode = langCode,
                feedType = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
            ).apply {
                this.totalResults = total
                entries +=
                    chapterToMangaMap.map { (chapter, manga) ->
                        createChapterEntry(chapter, manga, baseUrl, false, true, langCode)
                    }
            }
        return OpdsXmlUtil.serializeFeedToString(builder.build())
    }

    private class FeedBuilder(
        val baseUrl: String,
        val pageNum: Int,
        val idWithoutParams: String,
        var titleKeyOrActual: String,
        val searchQuery: String? = null,
        var explicitQueryParams: String? = null,
        val currentSort: String? = null,
        val currentFilter: String? = null,
        val langCode: String,
        val isPaginated: Boolean = true,
        val feedType: String,
    ) {
        val feedGeneratedAt: String = OpdsDateUtil.formatCurrentInstantForOpds()
        var totalResults: Long = 0
        var icon: String? = null
        val links = mutableListOf<OpdsLinkXml>()
        val entries = mutableListOf<OpdsEntryXml>()

        private val feedTitle: String by lazy {
            if (titleKeyOrActual.startsWith("opds.")) {
                LocalizationService.getString(langCode, titleKeyOrActual, defaultValue = titleKeyOrActual.substringAfterLast('.'))
            } else {
                titleKeyOrActual
            }
        }

        private fun buildUrlWithParams(
            baseHrefPath: String,
            page: Int,
        ): String {
            val sb = StringBuilder("$baseUrl/$baseHrefPath?")
            val queryParams = mutableListOf<String>()

            if (isPaginated) queryParams.add("pageNumber=$page")

            explicitQueryParams?.takeIf { it.isNotBlank() }?.let {
                queryParams.add(it)
            } ?: searchQuery?.takeIf { it.isNotBlank() }?.let {
                queryParams.add("query=${it.encodeForOpdsURL()}")
            }

            currentSort?.let { queryParams.add("sort=$it") }
            currentFilter?.let { queryParams.add("filter=$it") }
            queryParams.add("lang=$langCode")

            return sb.append(queryParams.joinToString("&")).toString().replace("?&", "?")
        }

        fun build(): OpdsFeedXml {
            val selfLinkHref = buildUrlWithParams(idWithoutParams, pageNum)
            val feedIconUrl = icon

            val feedLinks = mutableListOf<OpdsLinkXml>()
            feedLinks.addAll(this.links)

            feedLinks.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_SELF,
                    selfLinkHref,
                    feedType,
                    LocalizationService.getString(langCode, "opds.linktitle.selfFeed", defaultValue = "Self Feed"),
                ),
            )
            feedLinks.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_START,
                    "$baseUrl?lang=$langCode",
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    LocalizationService.getString(
                        langCode,
                        "opds.linktitle.catalogRoot",
                        defaultValue = "Catalog Root",
                    ),
                ),
            )
            feedLinks.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_SEARCH,
                    "$baseUrl/search?lang=$langCode",
                    OpdsConstants.TYPE_OPENSEARCH_DESCRIPTION,
                    LocalizationService.getString(
                        langCode,
                        "opds.linktitle.searchCatalog",
                        defaultValue = "Search Catalog",
                    ),
                ),
            )

            if (isPaginated) {
                if (pageNum > 1) {
                    feedLinks.add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_PREV,
                            buildUrlWithParams(idWithoutParams, pageNum - 1),
                            feedType,
                            LocalizationService.getString(
                                langCode,
                                "opds.linktitle.previousPage",
                                defaultValue = "Previous Page",
                            ),
                        ),
                    )
                }
                if (totalResults > pageNum * opdsItemsPerPageBounded) {
                    feedLinks.add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_NEXT,
                            buildUrlWithParams(idWithoutParams, pageNum + 1),
                            feedType,
                            LocalizationService.getString(
                                langCode,
                                "opds.linktitle.nextPage",
                                defaultValue = "Next Page",
                            ),
                        ),
                    )
                }
            }

            return OpdsFeedXml(
                id =
                    "urn:suwayomi:feed:$idWithoutParams:$langCode:$pageNum" +
                        (searchQuery?.let { ":query=${it.encodeForOpdsURL()}" } ?: "") +
                        (currentSort?.let { ":sort=$it" } ?: "") +
                        (currentFilter?.let { ":filter=$it" } ?: ""),
                title = feedTitle,
                updated = feedGeneratedAt, // Use FeedBuilder's own timestamp
                icon = feedIconUrl,
                author = OpdsAuthorXml("Suwayomi", "https://suwayomi.org/"),
                links = feedLinks,
                entries = entries,
                totalResults = totalResults.takeIf { it > 0 },
                itemsPerPage = if (isPaginated && totalResults > 0) opdsItemsPerPageBounded else null,
                startIndex = if (isPaginated && totalResults > 0) ((pageNum - 1) * opdsItemsPerPageBounded + 1) else null,
            )
        }
    }

    private fun mangaEntry(
        manga: MangaDataClass,
        baseUrl: String,
        formattedNow: String,
        langCode: String,
    ): OpdsEntryXml {
        val displayThumbnailUrl = manga.thumbnailUrl?.let { proxyThumbnailUrl(manga.id) }
        val title = manga.title

        return OpdsEntryXml(
            id = "urn:suwayomi:manga:${manga.id}",
            title = title,
            updated = formattedNow,
            authors = manga.author?.let { listOf(OpdsAuthorXml(name = it)) },
            categories =
                manga.genre.filter { it.isNotBlank() }.map {
                    OpdsCategoryXml(term = "", label = it)
                },
            summary = manga.description?.let { OpdsSummaryXml(value = it) },
//            content = manga.description?.let { OpdsContentXml(value = it) },
            link =
                listOfNotNull(
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_SUBSECTION,
                        href = "$baseUrl/manga/${manga.id}?lang=$langCode",
                        type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        title = title,
                    ),
                    displayThumbnailUrl?.let {
                        OpdsLinkXml(rel = OpdsConstants.LINK_REL_IMAGE, href = it, type = OpdsConstants.TYPE_IMAGE_JPEG)
                    },
                    displayThumbnailUrl?.let {
                        OpdsLinkXml(rel = OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, href = it, type = OpdsConstants.TYPE_IMAGE_JPEG)
                    },
                ),
            language = manga.sourceLang,
        )
    }
}
