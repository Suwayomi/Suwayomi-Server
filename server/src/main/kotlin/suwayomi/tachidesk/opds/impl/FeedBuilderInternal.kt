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

/**
 * Clase de ayuda para construir un OpdsFeedXml.
 */
class FeedBuilderInternal(
    val baseUrl: String,
    val idPath: String,
    val title: String,
    val locale: Locale,
    val feedType: String,
    var pageNum: Int? = 1,
    var explicitQueryParams: String? = null,
    val currentSort: String? = null,
    val currentFilter: String? = null,
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
        val actualPageNum = pageNum ?: 1
        val selfLinkHref = buildUrlWithParams(idPath, if (pageNum != null) actualPageNum else null)
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

        if (pageNum != null) {
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
