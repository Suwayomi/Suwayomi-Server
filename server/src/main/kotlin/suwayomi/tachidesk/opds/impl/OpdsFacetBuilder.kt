package suwayomi.tachidesk.opds.impl

import dev.icerock.moko.resources.StringResource
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.opds.dto.OpdsFacet
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.PrimaryFilterType
import suwayomi.tachidesk.opds.repository.MangaRepository
import suwayomi.tachidesk.opds.repository.NavigationRepository
import java.util.Locale

/**
 * Builds the facets of the acquisition feeds, independently of the catalog
 * format they are rendered to.
 */
object OpdsFacetBuilder {
    /**
     * Returns the sorting facets for an 'Explore Source' feed.
     */
    fun getSourceSortFacets(
        baseUrl: String,
        locale: Locale,
        currentSort: String,
    ): List<OpdsFacet> {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val languageTag = locale.toLanguageTag()
        return listOf(
            "popular" to MR.strings.opds_facet_sort_popular,
            "latest" to MR.strings.opds_facet_sort_latest,
        ).map { (sort, title) ->
            OpdsFacet(sortGroup, title.localized(locale), "$baseUrl?sort=$sort&lang=$languageTag", currentSort == sort)
        }
    }

    /**
     * Returns the sorting and filtering facets for a chapter feed.
     */
    fun getChapterSortAndFilterFacets(
        baseUrl: String,
        locale: Locale,
        currentSort: String,
        currentFilter: String,
        filterCounts: Map<String, Long>,
    ): List<OpdsFacet> {
        val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
        val filterGroup = MR.strings.opds_facetgroup_filter_read_status.localized(locale)
        val languageTag = locale.toLanguageTag()
        return listOf(
            "number_asc" to MR.strings.opds_facet_sort_oldest_first,
            "number_desc" to MR.strings.opds_facet_sort_newest_first,
            "date_asc" to MR.strings.opds_facet_sort_date_asc,
            "date_desc" to MR.strings.opds_facet_sort_date_desc,
        ).map { (sort, title) ->
            OpdsFacet(
                sortGroup,
                title.localized(locale),
                "$baseUrl?sort=$sort&filter=$currentFilter&lang=$languageTag",
                currentSort == sort,
            )
        } +
            listOf(
                "all" to MR.strings.opds_facet_filter_all_chapters,
                "unread" to MR.strings.opds_facet_filter_unread_only,
                "read" to MR.strings.opds_facet_filter_read_only,
            ).map { (filter, title) ->
                OpdsFacet(
                    filterGroup,
                    title.localized(locale),
                    "$baseUrl?filter=$filter&sort=$currentSort&lang=$languageTag",
                    currentFilter == filter,
                    filterCounts[filter],
                )
            }
    }

    /**
     * Returns the facets for library feeds, covering sorting, content
     * filtering, and cross-filtering by source, category, status, language,
     * and genre.
     */
    fun getLibraryFacets(
        userId: Int,
        baseUrl: String,
        locale: Locale,
        activeFilters: OpdsMangaFilter,
    ): List<OpdsFacet> {
        val currentSort = activeFilters.sort ?: "alpha_asc"
        val currentFilter = activeFilters.filter ?: "all"
        val languageTag = locale.toLanguageTag()

        val buildUrl = { newFilters: OpdsMangaFilter, newSort: String, newFilter: String ->
            listOf(newFilters.toCrossFilterQueryParameters(), "sort=$newSort", "filter=$newFilter", "lang=$languageTag")
                .filter { it.isNotEmpty() }
                .joinToString("&", "$baseUrl/library/series?")
        }

        return buildList {
            val sortGroup = MR.strings.opds_facetgroup_sort_order.localized(locale)
            listOf(
                "alpha_asc" to MR.strings.opds_facet_sort_alpha_asc,
                "alpha_desc" to MR.strings.opds_facet_sort_alpha_desc,
                "last_read_desc" to MR.strings.opds_facet_sort_last_read_desc,
                "latest_chapter_desc" to MR.strings.opds_facet_sort_latest_chapter_desc,
                "date_added_desc" to MR.strings.opds_facet_sort_date_added_desc,
                "unread_desc" to MR.strings.opds_facet_sort_unread_desc,
            ).forEach { (sort, title) ->
                add(OpdsFacet(sortGroup, title.localized(locale), buildUrl(activeFilters, sort, currentFilter), currentSort == sort))
            }

            val filterGroup = MR.strings.opds_facetgroup_filter_content.localized(locale)
            val filterCounts = MangaRepository.getLibraryFilterCounts(userId, activeFilters)
            listOf(
                "all" to MR.strings.opds_facet_filter_all,
                "unread" to MR.strings.opds_facet_filter_unread_only,
                "downloaded" to MR.strings.opds_facet_filter_downloaded,
                "ongoing" to MR.strings.opds_facet_filter_ongoing,
                "completed" to MR.strings.opds_facet_filter_completed,
            ).forEach { (filter, title) ->
                add(
                    OpdsFacet(
                        filterGroup,
                        title.localized(locale),
                        buildUrl(activeFilters, currentSort, filter),
                        currentFilter == filter,
                        filterCounts[filter],
                    ),
                )
            }

            // Each cross-filter lists its values plus one clearing it
            fun addCrossFilter(
                key: String,
                group: StringResource,
                clearTitle: StringResource,
                activeValue: String?,
                values: List<Triple<String, String, Long?>>,
            ) {
                val groupTitle = group.localized(locale)
                add(
                    OpdsFacet(
                        groupTitle,
                        clearTitle.localized(locale),
                        buildUrl(activeFilters.without(key), currentSort, currentFilter),
                        activeValue == null,
                    ),
                )
                values.forEach { (value, title, count) ->
                    add(
                        OpdsFacet(
                            groupTitle,
                            title,
                            buildUrl(activeFilters.with(key, value), currentSort, currentFilter),
                            activeValue == value,
                            count,
                        ),
                    )
                }
            }

            if (activeFilters.primaryFilter != PrimaryFilterType.SOURCE) {
                addCrossFilter(
                    "source_id",
                    MR.strings.opds_facetgroup_filter_source,
                    MR.strings.opds_facet_all_sources,
                    activeFilters.sourceId?.toString(),
                    NavigationRepository
                        .getLibrarySources(userId, pageNum = null, activeFilters = activeFilters)
                        .first
                        .map { Triple(it.id.toString(), it.name, it.mangaCount) },
                )
            }
            if (activeFilters.primaryFilter != PrimaryFilterType.CATEGORY) {
                addCrossFilter(
                    "category_id",
                    MR.strings.opds_facetgroup_filter_category,
                    MR.strings.opds_facet_all_categories,
                    activeFilters.categoryId?.toString(),
                    NavigationRepository
                        .getCategories(userId, pageNum = null, activeFilters = activeFilters)
                        .first
                        .map { Triple(it.id.toString(), it.name, it.mangaCount) },
                )
            }
            if (activeFilters.primaryFilter != PrimaryFilterType.STATUS) {
                addCrossFilter(
                    "status_id",
                    MR.strings.opds_facetgroup_filter_status,
                    MR.strings.opds_facet_all_statuses,
                    activeFilters.statusId?.toString(),
                    NavigationRepository
                        .getStatuses(userId, locale, pageNum = null, activeFilters = activeFilters)
                        .first
                        .map { Triple(it.id.toString(), it.title, it.mangaCount) },
                )
            }
            if (activeFilters.primaryFilter != PrimaryFilterType.LANGUAGE) {
                addCrossFilter(
                    "lang_code",
                    MR.strings.opds_facetgroup_filter_language,
                    MR.strings.opds_facet_all_languages,
                    activeFilters.langCode,
                    NavigationRepository
                        .getContentLanguages(userId, locale, pageNum = null, activeFilters = activeFilters)
                        .first
                        .map { Triple(it.id, it.title, it.mangaCount) },
                )
            }
            if (activeFilters.primaryFilter != PrimaryFilterType.GENRE) {
                addCrossFilter(
                    "genre",
                    MR.strings.opds_facetgroup_filter_genre,
                    MR.strings.opds_facet_all_genres,
                    activeFilters.genre,
                    NavigationRepository
                        .getGenres(userId, locale, pageNum = null, activeFilters = activeFilters)
                        .first
                        .map { Triple(it.title, it.title, it.mangaCount) },
                )
            }
        }
    }
}
