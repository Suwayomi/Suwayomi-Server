package suwayomi.tachidesk.opds.controller

import io.javalin.http.HttpStatus
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation
import java.util.Locale

object OpdsV1Controller {
    private const val OPDS_MIME = "application/xml;profile=opds-catalog;charset=UTF-8"
    private const val BASE_URL = "/api/opds/v1.2"

    // OPDS Catalog Root Feed
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
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.contentType(OPDS_MIME).result(OpdsFeedBuilder.getRootFeed(BASE_URL, locale))
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    // --- Main Navigation Feeds ---

    // History Acquisition Feed
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

    // OPDS Search Description Feed
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

    // All Series in Library / Search Results Feed
    val seriesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("query"),
            queryParam<String?>("author"),
            queryParam<String?>("title"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Series in Library Feed")
                    description(
                        "Provides a list of series entries from the library. Can be paginated and supports search via query parameters.",
                    )
                }
            },
            behaviorOf = { ctx, pageNumber, query, author, title, sort, filter, lang ->
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                val opdsSearchCriteria =
                    if (query != null ||
                        author != null ||
                        title != null
                    ) {
                        OpdsSearchCriteria(query, author, title)
                    } else {
                        null
                    }
                val effectivePageNumber = if (opdsSearchCriteria != null) 1 else pageNumber ?: 1

                ctx.future {
                    future {
                        OpdsFeedBuilder.getSeriesFeed(opdsSearchCriteria, BASE_URL, effectivePageNumber, sort, filter, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = { httpCode(HttpStatus.OK) },
        )

    // Explore -> All Sources Navigation Feed
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

    // Library -> Sources Navigation Feed
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

    // Library -> Categories Navigation Feed
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

    // Library -> Genres Navigation Feed
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

    // Library -> Status Navigation Feed
    val statusFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Status Navigation Feed")
                    description("Navigation feed listing series publication statuses for the library.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
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

    // Library -> Content Languages Navigation Feed
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

    // Library Updates Acquisition Feed
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

    // Explore -> Source-Specific Series Acquisition Feed
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

    // Library -> Source-Specific Series Acquisition Feed
    val librarySourceFeed =
        handler(
            pathParam<Long>("sourceId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Library Source Specific Series Feed")
                    description("Acquisition feed listing series from the library belonging to a specific source.")
                }
            },
            behaviorOf = { ctx, sourceId, pageNumber, sort, filter, lang ->
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLibrarySourceFeed(sourceId, BASE_URL, pageNumber ?: 1, sort, filter, locale)
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

    // Category-Specific Series Acquisition Feed
    val categoryFeed =
        handler(
            pathParam<Int>("categoryId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Category Specific Series Feed")
                    description("Acquisition feed listing series belonging to a specific category.")
                }
            },
            behaviorOf = { ctx, categoryId, pageNumber, sort, filter, lang ->
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getCategoryFeed(categoryId, BASE_URL, pageNumber ?: 1, sort, filter, locale)
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

    // Genre-Specific Series Acquisition Feed
    val genreFeed =
        handler(
            pathParam<String>("genre"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Genre Specific Series Feed")
                    description("Acquisition feed listing series belonging to a specific genre.")
                }
            },
            behaviorOf = { ctx, genre, pageNumber, sort, filter, lang ->
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getGenreFeed(genre, BASE_URL, pageNumber ?: 1, sort, filter, locale)
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

    // Status-Specific Series Acquisition Feed
    val statusMangaFeed =
        handler(
            pathParam<Long>("statusId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Status Specific Series Feed")
                    description("Acquisition feed listing series with a specific publication status.")
                }
            },
            behaviorOf = { ctx, statusId, pageNumber, sort, filter, lang ->
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getStatusMangaFeed(statusId, BASE_URL, pageNumber ?: 1, sort, filter, locale)
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

    // Language-Specific Series Acquisition Feed
    val languageFeed =
        handler(
            pathParam<String>("langCode"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Content Language Specific Series Feed")
                    description("Acquisition feed listing series of a specific content language.")
                }
            },
            behaviorOf = { ctx, contentLangCodePath, pageNumber, sort, filter, uiLangParam ->
                val uiLocale: Locale = LocalizationHelper.ctxToLocale(ctx, uiLangParam)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLanguageFeed(contentLangCodePath, BASE_URL, pageNumber ?: 1, sort, filter, uiLocale)
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

    // Series Chapters Acquisition Feed
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

    // Chapter Metadata Acquisition Feed
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
