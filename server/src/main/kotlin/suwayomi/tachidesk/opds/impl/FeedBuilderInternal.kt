package suwayomi.tachidesk.opds.impl

import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.model.OpdsAuthorXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsFeedXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale
import kotlin.math.ceil

/**
 * Helper class to build an OpdsFeedXml.
 */
class FeedBuilderInternal(
    private val baseUrl: String,
    private val locale: Locale,
    private val idPath: String,
    private val title: String,
    private val feedType: String,
    private val pageNum: Int? = null,
    private val explicitQueryParams: String? = null,
    private val currentSort: String? = null,
    private val currentFilter: String? = null,
    private val isSearchFeed: Boolean = false,
) {
    var totalResults: Long = 0
    var icon: String? = null
    val links = mutableListOf<OpdsLinkXml>()
    val entries = mutableListOf<OpdsEntryXml>()

    private fun buildUrlWithParams(page: Int? = pageNum): String {
        val queryParams =
            listOfNotNull(
                explicitQueryParams?.takeIf(String::isNotBlank),
                page?.let { "pageNumber=$it" },
                currentSort?.let { "sort=$it" },
                currentFilter?.let { "filter=$it" },
                "lang=${locale.toLanguageTag()}",
            ).joinToString("&")

        return "$baseUrl/$idPath" + if (queryParams.isNotEmpty()) "?$queryParams" else ""
    }

    fun build(): OpdsFeedXml {
        val itemsPerPage = serverConfig.opdsItemsPerPage.value
        val showOpenSearch = isSearchFeed && pageNum != null && totalResults > 0
        val urnSuffix =
            listOfNotNull(
                locale.toLanguageTag(),
                pageNum?.let { "page$it" },
                explicitQueryParams?.replace("&", ":")?.replace("=", "_"),
                currentSort?.let { "sort_$it" },
                currentFilter?.let { "filter_$it" },
            ).joinToString(":")

        return OpdsFeedXml(
            id = "urn:suwayomi:feed:${idPath.replace('/', ':')}${if (urnSuffix.isNotEmpty()) ":$urnSuffix" else ""}",
            title = title,
            updated = OpdsDateUtil.formatCurrentInstantForOpds(),
            icon = icon,
            author = OpdsAuthorXml("Suwayomi", "https://suwayomi.org/"),
            links =
                buildList {
                    addAll(this@FeedBuilderInternal.links)
                    add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_SELF,
                            buildUrlWithParams(),
                            feedType,
                            MR.strings.opds_linktitle_self_feed.localized(locale),
                        ),
                    )
                    add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_START,
                            "$baseUrl?lang=${locale.toLanguageTag()}",
                            OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                            MR.strings.opds_linktitle_catalog_root.localized(locale),
                        ),
                    )
                    add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_SEARCH,
                            "$baseUrl/search?lang=${locale.toLanguageTag()}",
                            OpdsConstants.TYPE_OPENSEARCH_DESCRIPTION,
                            MR.strings.opds_linktitle_search_catalog.localized(locale),
                        ),
                    )

                    if (pageNum != null) {
                        val totalPages = ceil(totalResults.toDouble() / itemsPerPage).toInt()
                        if (totalPages > 1) {
                            val currentPage = pageNum.coerceAtLeast(1)
                            add(
                                OpdsLinkXml(
                                    OpdsConstants.LINK_REL_FIRST,
                                    buildUrlWithParams(1),
                                    feedType,
                                    MR.strings.opds_linktitle_first_page.localized(locale),
                                ),
                            )
                            if (currentPage >
                                1
                            ) {
                                add(
                                    OpdsLinkXml(
                                        OpdsConstants.LINK_REL_PREV,
                                        buildUrlWithParams(currentPage - 1),
                                        feedType,
                                        MR.strings.opds_linktitle_previous_page.localized(locale),
                                    ),
                                )
                            }
                            if (currentPage <
                                totalPages
                            ) {
                                add(
                                    OpdsLinkXml(
                                        OpdsConstants.LINK_REL_NEXT,
                                        buildUrlWithParams(currentPage + 1),
                                        feedType,
                                        MR.strings.opds_linktitle_next_page.localized(locale),
                                    ),
                                )
                            }
                            add(
                                OpdsLinkXml(
                                    OpdsConstants.LINK_REL_LAST,
                                    buildUrlWithParams(totalPages),
                                    feedType,
                                    MR.strings.opds_linktitle_last_page.localized(locale),
                                ),
                            )
                        }
                    }
                },
            entries = entries,
            totalResults = totalResults.takeIf { showOpenSearch },
            itemsPerPage = itemsPerPage.takeIf { showOpenSearch },
            startIndex = if (showOpenSearch && pageNum != null) ((pageNum - 1) * itemsPerPage) + 1 else null,
        )
    }
}
