package suwayomi.tachidesk.opds.controller

import io.javalin.http.Context
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.dto.PrimaryFilterType
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUserWithBasicFallback
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import java.util.Locale

/**
 * Controller for handling OPDS v1.2 feed requests.
 */
object OpdsV1Controller {
    private const val OPDS_MIME = "application/xml;profile=opds-catalog;charset=UTF-8"
    private const val BASE_URL = "/api/opds/v1.2"

    /**
     * Helper function to generate and send a library feed response.
     * It asynchronously builds the feed and sets the response content type.
     */
    private fun getLibraryFeed(
        ctx: Context,
        pageNum: Int?,
        criteria: OpdsMangaFilter,
    ) {
        val locale: Locale = LocalizationHelper.ctxToLocale(ctx, ctx.queryParam("lang"))
        ctx.future {
            future {
                OpdsFeedBuilder.getLibraryFeed(
                    criteria = criteria,
                    baseUrl = BASE_URL,
                    pageNum = pageNum ?: 1,
                    sort = criteria.sort,
                    filter = criteria.filter,
                    locale = locale,
                )
            }.thenApply { xml ->
                ctx.contentType(OPDS_MIME).result(xml)
            }
        }
    }

    /**
     * Serves the root navigation feed for the OPDS catalog.
     */
    val rootFeed =
        handler(
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Root Feed")
                    description("Top-level navigation feed for the OPDS catalog.")
                }
            },
            behaviorOf = { ctx, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.contentType(OPDS_MIME).result(OpdsFeedBuilder.getRootFeed(BASE_URL, locale))
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    // --- Main Navigation Feeds ---

    /**
     * Serves an acquisition feed listing recently read chapters.
     */
    val historyFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS History Feed")
                    description("Acquisition feed listing recently read chapters.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getHistoryFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves the OpenSearch description document for catalog integration.
     */
    val searchFeed =
        handler(
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OpenSearch Description")
                    description("XML description for OPDS searches, enabling catalog search integration.")
                }
            },
            behaviorOf = { ctx, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.contentType("application/opensearchdescription+xml").result(
                    """
                    <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/"
                        xmlns:atom="http://www.w3.org/2005/Atom">
                        <ShortName>${MR.strings.opds_search_shortname.localized(locale)}</ShortName>
                        <Description>${MR.strings.opds_search_description.localized(locale)}</Description>
                        <InputEncoding>UTF-8</InputEncoding>
                        <OutputEncoding>UTF-8</OutputEncoding>
                        <Url type="${OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION}"
                            rel="results"
                            template="$BASE_URL/library/series?query={searchTerms}&lang=${locale.toLanguageTag()}"/>
                    </OpenSearchDescription>
                    """.trimIndent(),
                )
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves an acquisition feed for all series in the library or search results.
     * This endpoint handles both general library browsing and specific search queries.
     */
    val seriesFeed =
        handler(
            documentWith = { withOperation { summary("OPDS Series in Library Feed") } },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val pageNumber = ctx.queryParam("pageNumber")?.toIntOrNull()
                val query = ctx.queryParam("query")
                val author = ctx.queryParam("author")
                val title = ctx.queryParam("title")
                val lang = ctx.queryParam("lang")
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)

                if (query != null || author != null || title != null) {
                    val opdsSearchCriteria = OpdsSearchCriteria(query, author, title)
                    ctx.future {
                        future {
                            OpdsFeedBuilder.getSearchFeed(opdsSearchCriteria, BASE_URL, pageNumber ?: 1, locale)
                        }.thenApply { xml ->
                            ctx.contentType(OPDS_MIME).result(xml)
                        }
                    }
                } else {
                    val criteria =
                        OpdsMangaFilter(
                            sourceId = ctx.queryParam("source_id")?.toLongOrNull(),
                            categoryId = ctx.queryParam("category_id")?.toIntOrNull(),
                            statusId = ctx.queryParam("status_id")?.toIntOrNull(),
                            genre = ctx.queryParam("genre"),
                            langCode = ctx.queryParam("lang_code"),
                            sort = ctx.queryParam("sort"),
                            filter = ctx.queryParam("filter"),
                            primaryFilter = PrimaryFilterType.NONE,
                        )
                    getLibraryFeed(
                        ctx,
                        pageNumber,
                        criteria,
                    )
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves a navigation feed listing all available manga sources for exploration.
     */
    val exploreSourcesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS All Sources Navigation Feed")
                    description("Navigation feed listing all available manga sources.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getExploreSourcesFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves a navigation feed listing only the sources for series present in the library.
     */
    val librarySourcesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Library Sources Navigation Feed")
                    description("Navigation feed listing sources for series currently in the library.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLibrarySourcesFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves a navigation feed for browsing manga categories within the library.
     */
    val categoriesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Categories Navigation Feed")
                    description("Navigation feed listing available manga categories for the library.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getCategoriesFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves a navigation feed for browsing manga genres within the library.
     */
    val genresFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Genres Navigation Feed")
                    description("Navigation feed listing available manga genres in the library.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getGenresFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves a navigation feed for browsing series by their publication status.
     */
    val statusesFeed =
        handler(
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Statuses Navigation Feed")
                    description("Navigation feed listing series publication statuses for the library.")
                }
            },
            behaviorOf = { ctx, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getStatusFeed(BASE_URL, 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves a navigation feed for browsing series by their content language.
     */
    val languagesFeed =
        handler(
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Content Languages Navigation Feed")
                    description("Navigation feed listing available content languages for series in the library.")
                }
            },
            behaviorOf = { ctx, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLanguagesFeed(BASE_URL, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves an acquisition feed of recent chapter updates for series in the library.
     */
    val libraryUpdatesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Library Updates Feed")
                    description("Acquisition feed listing recent chapter updates for series in the library.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLibraryUpdatesFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    /**
     * Serves an acquisition feed for all series from a specific source.
     */
    val exploreSourceFeed =
        handler(
            pathParam<Long>("sourceId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Source Specific Series Feed (Explore)")
                    description("Acquisition feed listing all series from a specific source.")
                }
            },
            behaviorOf = { ctx, sourceId, pageNumber, sort, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getExploreSourceFeed(sourceId, BASE_URL, pageNumber ?: 1, sort ?: "popular", locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /**
     * Builds an [OpdsMangaFilter] from the current request context, inheriting existing filters.
     */
    private fun buildCriteriaFromContext(
        ctx: Context,
        initialCriteria: OpdsMangaFilter,
    ): OpdsMangaFilter =
        initialCriteria.copy(
            sort = ctx.queryParam("sort"),
            filter = ctx.queryParam("filter"),
        )

    /**
     * Serves an acquisition feed for series in the library from a specific source.
     */
    val librarySourceFeed =
        handler(
            pathParam<Long>("sourceId"),
            documentWith = { withOperation { summary("OPDS Library Source Specific Series Feed") } },
            behaviorOf = { ctx, sourceId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val criteria = buildCriteriaFromContext(ctx, OpdsMangaFilter(sourceId = sourceId, primaryFilter = PrimaryFilterType.SOURCE))
                getLibraryFeed(ctx, ctx.queryParam("pageNumber")?.toIntOrNull(), criteria)
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /**
     * Serves an acquisition feed for series in a specific category.
     */
    val categoryFeed =
        handler(
            pathParam<Int>("categoryId"),
            documentWith = { withOperation { summary("OPDS Category Specific Series Feed") } },
            behaviorOf = { ctx, categoryId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val criteria =
                    buildCriteriaFromContext(ctx, OpdsMangaFilter(categoryId = categoryId, primaryFilter = PrimaryFilterType.CATEGORY))
                getLibraryFeed(ctx, ctx.queryParam("pageNumber")?.toIntOrNull(), criteria)
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /**
     * Serves an acquisition feed for series belonging to a specific genre.
     */
    val genreFeed =
        handler(
            pathParam<String>("genre"),
            documentWith = { withOperation { summary("OPDS Genre Specific Series Feed") } },
            behaviorOf = { ctx, genre ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val criteria = buildCriteriaFromContext(ctx, OpdsMangaFilter(genre = genre, primaryFilter = PrimaryFilterType.GENRE))
                getLibraryFeed(ctx, ctx.queryParam("pageNumber")?.toIntOrNull(), criteria)
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /**
     * Serves an acquisition feed for series with a specific publication status.
     */
    val statusMangaFeed =
        handler(
            pathParam<Int>("statusId"),
            documentWith = { withOperation { summary("OPDS Status Specific Series Feed") } },
            behaviorOf = { ctx, statusId ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val criteria = buildCriteriaFromContext(ctx, OpdsMangaFilter(statusId = statusId, primaryFilter = PrimaryFilterType.STATUS))
                getLibraryFeed(ctx, ctx.queryParam("pageNumber")?.toIntOrNull(), criteria)
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /**
     * Serves an acquisition feed for series of a specific content language.
     */
    val languageFeed =
        handler(
            pathParam<String>("langCode"),
            documentWith = {
                withOperation {
                    summary("OPDS Content Language Specific Series Feed")
                    description("Acquisition feed listing series of a specific content language.")
                }
            },
            behaviorOf = { ctx, langCode ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val criteria =
                    buildCriteriaFromContext(ctx, OpdsMangaFilter(langCode = langCode, primaryFilter = PrimaryFilterType.LANGUAGE))
                getLibraryFeed(ctx, ctx.queryParam("pageNumber")?.toIntOrNull(), criteria)
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /**
     * Serves an acquisition feed listing chapters for a specific series.
     */
    val seriesChaptersFeed =
        handler(
            pathParam<Int>("seriesId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Series Chapters Feed")
                    description("Acquisition feed listing chapters for a specific series. Supports pagination, sorting, and filtering.")
                }
            },
            behaviorOf = { ctx, seriesId, pageNumber, sort, filter, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getSeriesChaptersFeed(seriesId, BASE_URL, pageNumber ?: 1, sort, filter, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /**
     * Serves an acquisition feed with detailed metadata for a single chapter.
     */
    val chapterMetadataFeed =
        handler(
            pathParam<Int>("seriesId"),
            pathParam<Int>("chapterIndex"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Chapter Details Feed")
                    description("Acquisition feed providing detailed metadata for a specific chapter.")
                }
            },
            behaviorOf = { ctx, seriesId, chapterIndex, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUserWithBasicFallback(ctx)
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getChapterMetadataFeed(seriesId, chapterIndex, BASE_URL, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )
}
