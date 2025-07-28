package suwayomi.tachidesk.opds.impl

import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsChapterMetadataAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.model.OpdsAuthorXml
import suwayomi.tachidesk.opds.model.OpdsCategoryXml
import suwayomi.tachidesk.opds.model.OpdsContentXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.model.OpdsSummaryXml
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.opds.util.OpdsStringUtil.formatFileSizeForOpds
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale

object OpdsEntryBuilder {
    private fun currentFormattedTime() = OpdsDateUtil.formatCurrentInstantForOpds()

    /**
     * Builds an intelligent and concise summary for manga entries.
     */
    private fun buildMangaSummary(
        entry: OpdsMangaAcqEntry,
        locale: Locale,
    ): String {
        val summaryParts = mutableListOf<String>()

        val statusKey =
            when (MangaStatus.valueOf(entry.status)) {
                MangaStatus.ONGOING -> MR.strings.manga_status_ongoing
                MangaStatus.COMPLETED -> MR.strings.manga_status_completed
                MangaStatus.LICENSED -> MR.strings.manga_status_licensed
                MangaStatus.PUBLISHING_FINISHED -> MR.strings.manga_status_publishing_finished
                MangaStatus.CANCELLED -> MR.strings.manga_status_cancelled
                MangaStatus.ON_HIATUS -> MR.strings.manga_status_on_hiatus
                else -> MR.strings.manga_status_unknown
            }
        summaryParts.add(MR.strings.opds_manga_summary_status.localized(locale, statusKey.localized(locale)))
        summaryParts.add(MR.strings.opds_manga_summary_source.localized(locale, entry.sourceName))
        summaryParts.add(MR.strings.opds_manga_summary_language.localized(locale, entry.sourceLang))

        return summaryParts.joinToString(" | ")
    }

    private fun addFacet(
        feedBuilder: FeedBuilderInternal,
        href: String,
        titleKey: StringResource,
        group: String,
        isActive: Boolean,
        count: Long?,
        locale: Locale,
    ) {
        feedBuilder.links.add(
            OpdsLinkXml(
                OpdsConstants.LINK_REL_FACET,
                href,
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                titleKey.localized(locale),
                facetGroup = group,
                activeFacet = isActive,
                thrCount = count?.toInt(),
            ),
        )
    }

    /**
     * Converts a manga acquisition entry to OPDS XML entry.
     */
    fun mangaAcqEntryToEntry(
        entry: OpdsMangaAcqEntry,
        baseUrl: String,
        locale: Locale,
    ): OpdsEntryXml {
        val displayThumbnailUrl = entry.thumbnailUrl?.let { proxyThumbnailUrl(entry.id) }
        val categoryScheme = if (entry.inLibrary) "$baseUrl/library/genres" else "$baseUrl/genres"

        val links = mutableListOf<OpdsLinkXml>()

        // Link to chapters list
        links.add(
            OpdsLinkXml(
                OpdsConstants.LINK_REL_SUBSECTION,
                "$baseUrl/series/${entry.id}/chapters?lang=${locale.toLanguageTag()}",
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                entry.title,
            ),
        )

        // Add link to the web version of the manga if url is available
        entry.url?.let {
            links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_ALTERNATE,
                    it,
                    "text/html",
                    MR.strings.opds_linktitle_view_on_web.localized(locale),
                ),
            )
        }

        // Image links
        displayThumbnailUrl?.let {
            links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
            links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
        }

        // Build summary
        val summaryText = buildMangaSummary(entry, locale)

        return OpdsEntryXml(
            id = "urn:suwayomi:manga:${entry.id}",
            title = entry.title,
            updated = OpdsDateUtil.formatEpochMillisForOpds(entry.lastFetchedAt * 1000),
            authors = entry.author?.let { listOf(OpdsAuthorXml(name = it)) },
            categories =
                entry.genres.filter { it.isNotBlank() }.map { genre ->
                    OpdsCategoryXml(
                        term = genre.lowercase().replace(" ", "_"),
                        label = genre,
                        scheme = categoryScheme,
                    )
                },
            summary = OpdsSummaryXml(value = summaryText),
            content = entry.description?.let { OpdsContentXml(type = "text", value = it) },
            link = links,
            // identifier = entry.url?.let { "urn:suwayomi:manga:source:${entry.url.hashCode()}" },
            publisher = entry.sourceName,
            language = entry.sourceLang,
        )
    }

    fun createChapterListEntry(
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
                    append(MR.strings.opds_chapter_details_scanlator.localized(locale, it))
                }
                if (chapter.pageCount > 0) {
                    append(MR.strings.opds_chapter_details_progress.localized(locale, chapter.lastPageRead, chapter.pageCount))
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
                        href = "$baseUrl/series/${manga.id}/chapter/${chapter.sourceOrder}/metadata?lang=${locale.toLanguageTag()}",
                        type = OpdsConstants.TYPE_ATOM_XML_ENTRY_PROFILE_OPDS,
                        title = MR.strings.opds_linktitle_view_chapter_details.localized(locale),
                    ),
                ),
        )
    }

    suspend fun createChapterMetadataEntry(
        chapter: OpdsChapterMetadataAcqEntry,
        manga: OpdsMangaDetails,
        baseUrl: String,
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
                    append(MR.strings.opds_chapter_details_scanlator.localized(locale, it))
                }
                val pageCountDisplay = chapter.pageCount.takeIf { it > 0 } ?: "?"
                append(MR.strings.opds_chapter_details_progress.localized(locale, chapter.lastPageRead, pageCountDisplay))
            }

        val links = mutableListOf<OpdsLinkXml>()
        var cbzFileSize: Long? = null

        chapter.url?.let {
            links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_ALTERNATE,
                    it,
                    "text/html",
                    MR.strings.opds_linktitle_view_on_web.localized(locale),
                ),
            )
        }

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
                    pseLastRead = chapter.lastPageRead.takeIf { it > 0 },
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

    fun addSourceSortFacets(
        feedBuilder: FeedBuilderInternal,
        baseUrl: String,
        currentSort: String,
        locale: Locale,
    ) {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val addFacet = { href: String, titleKey: StringResource, isActive: Boolean ->
            feedBuilder.links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_FACET,
                    href,
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    titleKey.localized(locale),
                    facetGroup = sortGroup,
                    activeFacet = isActive,
                ),
            )
        }

        addFacet("$baseUrl?sort=popular&lang=${locale.toLanguageTag()}", MR.strings.opds_facet_sort_popular, currentSort == "popular")
        addFacet("$baseUrl?sort=latest&lang=${locale.toLanguageTag()}", MR.strings.opds_facet_sort_latest, currentSort == "latest")
    }

    fun addChapterSortAndFilterFacets(
        feedBuilder: FeedBuilderInternal,
        baseUrl: String,
        currentSort: String,
        currentFilter: String,
        locale: Locale,
        filterCounts: Map<String, Long>? = null,
    ) {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val filterGroup = MR.strings.opds_facetgroup_read_status.localized(locale)

        val addSortFacet = { href: String, titleKey: StringResource, group: String, isActive: Boolean ->
            feedBuilder.links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_FACET,
                    href,
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    titleKey.localized(locale),
                    facetGroup = group,
                    activeFacet = isActive,
                ),
            )
        }

        addSortFacet(
            "$baseUrl?sort=number_asc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_oldest_first,
            sortGroup,
            currentSort == "number_asc",
        )
        addSortFacet(
            "$baseUrl?sort=number_desc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_newest_first,
            sortGroup,
            currentSort == "number_desc",
        )
        addSortFacet(
            "$baseUrl?sort=date_asc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_date_asc,
            sortGroup,
            currentSort == "date_asc",
        )
        addSortFacet(
            "$baseUrl?sort=date_desc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_date_desc,
            sortGroup,
            currentSort == "date_desc",
        )

        addFacet(
            feedBuilder,
            "$baseUrl?filter=all&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_all_chapters,
            filterGroup,
            currentFilter == "all",
            filterCounts?.get("all"),
            locale,
        )
        addFacet(
            feedBuilder,
            "$baseUrl?filter=unread&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_unread_only,
            filterGroup,
            currentFilter == "unread",
            filterCounts?.get("unread"),
            locale,
        )
        addFacet(
            feedBuilder,
            "$baseUrl?filter=read&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_read_only,
            filterGroup,
            currentFilter == "read",
            filterCounts?.get("read"),
            locale,
        )
    }

    fun addLibraryMangaSortAndFilterFacets(
        feedBuilder: FeedBuilderInternal,
        baseUrl: String,
        currentSort: String,
        currentFilter: String,
        locale: Locale,
        filterCounts: Map<String, Long>? = null,
    ) {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val filterGroup = MR.strings.opds_facetgroup_filter_content.localized(locale)

        val addSortFacet = { href: String, titleKey: StringResource, group: String, isActive: Boolean ->
            feedBuilder.links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_FACET,
                    href,
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    titleKey.localized(locale),
                    facetGroup = group,
                    activeFacet = isActive,
                ),
            )
        }

        // Sorting Facets
        addSortFacet(
            "$baseUrl?sort=alpha_asc&filter=$currentFilter",
            MR.strings.opds_facet_sort_alpha_asc,
            sortGroup,
            currentSort == "alpha_asc",
        )
        addSortFacet(
            "$baseUrl?sort=alpha_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_alpha_desc,
            sortGroup,
            currentSort == "alpha_desc",
        )
        addSortFacet(
            "$baseUrl?sort=last_read_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_last_read_desc,
            sortGroup,
            currentSort == "last_read_desc",
        )
        addSortFacet(
            "$baseUrl?sort=latest_chapter_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_latest_chapter_desc,
            sortGroup,
            currentSort == "latest_chapter_desc",
        )
        addSortFacet(
            "$baseUrl?sort=date_added_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_date_added_desc,
            sortGroup,
            currentSort == "date_added_desc",
        )
        addSortFacet(
            "$baseUrl?sort=unread_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_unread_desc,
            sortGroup,
            currentSort == "unread_desc",
        )

        // Filtering Facets
        addFacet(
            feedBuilder,
            "$baseUrl?filter=all&sort=$currentSort",
            MR.strings.opds_facet_filter_all,
            filterGroup,
            currentFilter == "all",
            filterCounts?.get("all"),
            locale,
        )
        addFacet(
            feedBuilder,
            "$baseUrl?filter=unread&sort=$currentSort",
            MR.strings.opds_facet_filter_unread_only,
            filterGroup,
            currentFilter == "unread",
            filterCounts?.get("unread"),
            locale,
        )
        addFacet(
            feedBuilder,
            "$baseUrl?filter=downloaded&sort=$currentSort",
            MR.strings.opds_facet_filter_downloaded,
            filterGroup,
            currentFilter == "downloaded",
            filterCounts?.get("downloaded"),
            locale,
        )
        addFacet(
            feedBuilder,
            "$baseUrl?filter=ongoing&sort=$currentSort",
            MR.strings.opds_facet_filter_ongoing,
            filterGroup,
            currentFilter == "ongoing",
            filterCounts?.get("ongoing"),
            locale,
        )
        addFacet(
            feedBuilder,
            "$baseUrl?filter=completed&sort=$currentSort",
            MR.strings.opds_facet_filter_completed,
            filterGroup,
            currentFilter == "completed",
            filterCounts?.get("completed"),
            locale,
        )
    }
}
