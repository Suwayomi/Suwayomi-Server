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
    private val idPath: String,
    private val title: String,
    private val locale: Locale,
    private val feedType: String,
    private val pageNum: Int? = 1,
    private val explicitQueryParams: String? = null,
    private val currentSort: String? = null,
    private val currentFilter: String? = null,
    private val isSearchFeed: Boolean = false,
) {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

    private val feedAuthor = OpdsAuthorXml("Suwayomi", "https://suwayomi.org/")
    private val feedGeneratedAt: String = OpdsDateUtil.formatCurrentInstantForOpds()

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

        explicitQueryParams?.takeIf { it.isNotBlank() }?.let { queryParamsList.add(it) }
        page?.let { queryParamsList.add("pageNumber=$it") }
        currentSort?.let { queryParamsList.add("sort=$it") }
        currentFilter?.let { queryParamsList.add("filter=$it") }
        queryParamsList.add("lang=${locale.toLanguageTag()}")

        if (queryParamsList.isNotEmpty()) {
            sb.append("?").append(queryParamsList.joinToString("&"))
        }
        return sb.toString()
    }

    fun build(): OpdsFeedXml {
        val selfLinkHref = buildUrlWithParams(idPath, if (pageNum != null) pageNum else null)
        val feedLinks = mutableListOf<OpdsLinkXml>()
        feedLinks.addAll(this.links)

        feedLinks.add(
            OpdsLinkXml(OpdsConstants.LINK_REL_SELF, selfLinkHref, feedType, MR.strings.opds_linktitle_self_feed.localized(locale)),
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

        // Add pagination links if needed
        if (pageNum != null) {
            val totalPages = ceil(totalResults.toDouble() / opdsItemsPerPageBounded).toInt()

            if (totalPages > 1) {
                val currentPage = pageNum.coerceAtLeast(1)

                // Always add 'first' link when there are multiple pages
                feedLinks.add(
                    OpdsLinkXml(
                        OpdsConstants.LINK_REL_FIRST,
                        buildUrlWithParams(idPath, 1),
                        feedType,
                        MR.strings.opds_linktitle_first_page.localized(locale),
                    ),
                )

                // Add 'prev' link if not on first page
                if (currentPage > 1) {
                    feedLinks.add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_PREV,
                            buildUrlWithParams(idPath, currentPage - 1),
                            feedType,
                            MR.strings.opds_linktitle_previous_page.localized(locale),
                        ),
                    )
                }

                // Add 'next' link if not on last page
                if (currentPage < totalPages) {
                    feedLinks.add(
                        OpdsLinkXml(
                            OpdsConstants.LINK_REL_NEXT,
                            buildUrlWithParams(idPath, currentPage + 1),
                            feedType,
                            MR.strings.opds_linktitle_next_page.localized(locale),
                        ),
                    )
                }

                // Always add 'last' link when there are multiple pages
                feedLinks.add(
                    OpdsLinkXml(
                        OpdsConstants.LINK_REL_LAST,
                        buildUrlWithParams(idPath, totalPages),
                        feedType,
                        MR.strings.opds_linktitle_last_page.localized(locale),
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

        val showOpenSearchFields = isSearchFeed && pageNum != null && totalResults > 0

        return OpdsFeedXml(
            id = "urn:suwayomi:feed:${idPath.replace('/',':')}$urnSuffix",
            title = title,
            updated = feedGeneratedAt,
            icon = icon,
            author = feedAuthor,
            links = feedLinks,
            entries = entries,
            totalResults = totalResults.takeIf { showOpenSearchFields },
            itemsPerPage = if (showOpenSearchFields) opdsItemsPerPageBounded else null,
            startIndex = if (showOpenSearchFields) ((pageNum - 1) * opdsItemsPerPageBounded) + 1 else null,
        )
    }
}
