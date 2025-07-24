package suwayomi.tachidesk.opds.impl

import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsChapterMetadataAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.model.OpdsAuthorXml
import suwayomi.tachidesk.opds.model.OpdsCategoryXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.model.OpdsSummaryXml
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.opds.util.OpdsStringUtil.formatFileSizeForOpds
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale

object OpdsEntryBuilder {
    private fun currentFormattedTime() = OpdsDateUtil.formatCurrentInstantForOpds()

    fun mangaAcqEntryToEntry(
        entry: OpdsMangaAcqEntry,
        baseUrl: String,
        locale: Locale,
    ): OpdsEntryXml {
        val displayThumbnailUrl = entry.thumbnailUrl?.let { proxyThumbnailUrl(entry.id) }
        val categoryScheme = if (entry.inLibrary) "$baseUrl/library/genres" else "$baseUrl/genres"

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
                        scheme = categoryScheme,
                    )
                },
            summary = entry.description?.let { OpdsSummaryXml(value = it) },
            link =
                listOfNotNull(
                    OpdsLinkXml(
                        OpdsConstants.LINK_REL_SUBSECTION,
                        "$baseUrl/series/${entry.id}/chapters?lang=${locale.toLanguageTag()}",
                        OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        entry.title,
                    ),
                    displayThumbnailUrl?.let { OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG) },
                    displayThumbnailUrl?.let { OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG) },
                ),
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
    ) {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val filterGroup = MR.strings.opds_facetgroup_read_status.localized(locale)

        val addFacet = { href: String, titleKey: StringResource, group: String, isActive: Boolean ->
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

        addFacet(
            "$baseUrl?sort=number_asc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_oldest_first,
            sortGroup,
            currentSort == "number_asc",
        )
        addFacet(
            "$baseUrl?sort=number_desc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_newest_first,
            sortGroup,
            currentSort == "number_desc",
        )
        addFacet(
            "$baseUrl?sort=date_asc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_date_asc,
            sortGroup,
            currentSort == "date_asc",
        )
        addFacet(
            "$baseUrl?sort=date_desc&filter=$currentFilter&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_sort_date_desc,
            sortGroup,
            currentSort == "date_desc",
        )

        addFacet(
            "$baseUrl?filter=all&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_all_chapters,
            filterGroup,
            currentFilter == "all",
        )
        addFacet(
            "$baseUrl?filter=unread&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_unread_only,
            filterGroup,
            currentFilter == "unread",
        )
        addFacet(
            "$baseUrl?filter=read&sort=$currentSort&lang=${locale.toLanguageTag()}",
            MR.strings.opds_facet_filter_read_only,
            filterGroup,
            currentFilter == "read",
        )
    }

    fun addLibraryMangaSortAndFilterFacets(
        feedBuilder: FeedBuilderInternal,
        baseUrl: String,
        currentSort: String,
        currentFilter: String,
        locale: Locale,
    ) {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val filterGroup = MR.strings.opds_facetgroup_filter_content.localized(locale)

        val addFacet = { href: String, titleKey: StringResource, group: String, isActive: Boolean ->
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
        addFacet(
            "$baseUrl?sort=alpha_asc&filter=$currentFilter",
            MR.strings.opds_facet_sort_alpha_asc,
            sortGroup,
            currentSort == "alpha_asc",
        )
        addFacet(
            "$baseUrl?sort=alpha_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_alpha_desc,
            sortGroup,
            currentSort == "alpha_desc",
        )
        addFacet(
            "$baseUrl?sort=last_read_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_last_read_desc,
            sortGroup,
            currentSort == "last_read_desc",
        )
        addFacet(
            "$baseUrl?sort=latest_chapter_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_latest_chapter_desc,
            sortGroup,
            currentSort == "latest_chapter_desc",
        )
        addFacet(
            "$baseUrl?sort=date_added_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_date_added_desc,
            sortGroup,
            currentSort == "date_added_desc",
        )
        addFacet(
            "$baseUrl?sort=unread_desc&filter=$currentFilter",
            MR.strings.opds_facet_sort_unread_desc,
            sortGroup,
            currentSort == "unread_desc",
        )

        // Filtering Facets
        addFacet("$baseUrl?filter=all&sort=$currentSort", MR.strings.opds_facet_filter_all, filterGroup, currentFilter == "all")
        addFacet(
            "$baseUrl?filter=unread&sort=$currentSort",
            MR.strings.opds_facet_filter_unread_only,
            filterGroup,
            currentFilter == "unread",
        )
        addFacet(
            "$baseUrl?filter=downloaded&sort=$currentSort",
            MR.strings.opds_facet_filter_downloaded,
            filterGroup,
            currentFilter == "downloaded",
        )
        addFacet("$baseUrl?filter=ongoing&sort=$currentSort", MR.strings.opds_facet_filter_ongoing, filterGroup, currentFilter == "ongoing")
        addFacet(
            "$baseUrl?filter=completed&sort=$currentSort",
            MR.strings.opds_facet_filter_completed,
            filterGroup,
            currentFilter == "completed",
        )
    }
}
