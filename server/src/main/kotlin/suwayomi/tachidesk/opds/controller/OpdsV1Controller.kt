package suwayomi.tachidesk.opds.controller

import io.javalin.http.HttpStatus
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
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
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getRootFeed(BASE_URL, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
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
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
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
                            template="$BASE_URL/mangas?query={searchTerms}&lang=${locale.toLanguageTag()}"/>
                    </OpenSearchDescription>
                    """.trimIndent(),
                )
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // --- Main Navigation & Broad Acquisition Feeds ---

    // All Mangas / Search Results Feed
    val mangasFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("query"),
            queryParam<String?>("author"),
            queryParam<String?>("title"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Mangas Feed")
                    description(
                        "Provides a list of manga entries. Can be paginated and supports search via query parameters " +
                            "(query, author, title). If search parameters are present, it acts as a search results feed.",
                    )
                }
            },
            behaviorOf = { ctx, pageNumber, query, author, title, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                val opdsSearchCriteria =
                    if (query != null || author != null || title != null) {
                        OpdsSearchCriteria(query, author, title)
                    } else {
                        null
                    }
                val effectivePageNumber = if (opdsSearchCriteria != null) 1 else pageNumber ?: 1

                ctx.future {
                    future {
                        OpdsFeedBuilder.getMangasFeed(opdsSearchCriteria, BASE_URL, effectivePageNumber, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Sources Navigation Feed
    val sourcesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Sources Navigation Feed")
                    description("Navigation feed listing available manga sources. Each entry links to a feed for a specific source.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getSourcesFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Categories Navigation Feed
    val categoriesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Categories Navigation Feed")
                    description("Navigation feed listing available manga categories. Each entry links to a feed for a specific category.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getCategoriesFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Genres Navigation Feed
    val genresFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Genres Navigation Feed")
                    description("Navigation feed listing available manga genres. Each entry links to a feed for a specific genre.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getGenresFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Status Navigation Feed
    val statusFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Status Navigation Feed")
                    description(
                        "Navigation feed listing manga publication statuses. Each entry links to a feed for manga with a specific status.",
                    )
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        // Ignoramos pageNumber aquí, siempre usamos 1
                        OpdsFeedBuilder.getStatusFeed(BASE_URL, 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Content Languages Navigation Feed
    val languagesFeed =
        handler(
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Content Languages Navigation Feed")
                    description(
                        "Navigation feed listing available content languages for manga. " +
                            "Each entry links to a feed for manga in a specific content language.",
                    )
                }
            },
            behaviorOf = { ctx, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLanguagesFeed(BASE_URL, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Library Updates Acquisition Feed
    val libraryUpdatesFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Library Updates Feed")
                    description("Acquisition feed listing recent chapter updates for manga in the library. Supports pagination.")
                }
            },
            behaviorOf = { ctx, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLibraryUpdatesFeed(BASE_URL, pageNumber ?: 1, locale)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // --- Filtered Acquisition Feeds ---

    // Source-Specific Manga Acquisition Feed
    val sourceFeed =
        handler(
            pathParam<Long>("sourceId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Source Specific Manga Feed")
                    description("Acquisition feed listing manga from a specific source. Supports pagination.")
                }
            },
            behaviorOf = { ctx, sourceId, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getSourceFeed(sourceId, BASE_URL, pageNumber ?: 1, locale)
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

    // Category-Specific Manga Acquisition Feed
    val categoryFeed =
        handler(
            pathParam<Int>("categoryId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Category Specific Manga Feed")
                    description("Acquisition feed listing manga belonging to a specific category. Supports pagination.")
                }
            },
            behaviorOf = { ctx, categoryId, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getCategoryFeed(categoryId, BASE_URL, pageNumber ?: 1, locale)
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

    // Genre-Specific Manga Acquisition Feed
    val genreFeed =
        handler(
            pathParam<String>("genre"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Genre Specific Manga Feed")
                    description("Acquisition feed listing manga belonging to a specific genre. Supports pagination.")
                }
            },
            behaviorOf = { ctx, genre, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getGenreFeed(genre, BASE_URL, pageNumber ?: 1, locale)
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

    // Status-Specific Manga Acquisition Feed
    val statusMangaFeed =
        handler(
            pathParam<Long>("statusId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Status Specific Manga Feed")
                    description("Acquisition feed listing manga with a specific publication status. Supports pagination.")
                }
            },
            behaviorOf = { ctx, statusId, pageNumber, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getStatusMangaFeed(statusId, BASE_URL, pageNumber ?: 1, locale)
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

    // Language-Specific Manga Acquisition Feed
    val languageFeed =
        handler(
            pathParam<String>("langCode"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Content Language Specific Manga Feed")
                    description("Acquisition feed listing manga of a specific content language. Supports pagination.")
                }
            },
            behaviorOf = { ctx, contentLangCodePath, pageNumber, uiLangParam ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val uiLocale: Locale = LocalizationHelper.ctxToLocale(ctx, uiLangParam)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLanguageFeed(contentLangCodePath, BASE_URL, pageNumber ?: 1, uiLocale)
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

    // --- Item-Specific Acquisition Feeds ---

    // Manga Chapters Acquisition Feed
    val mangaFeed =
        handler(
            pathParam<Int>("mangaId"),
            queryParam<Int?>("pageNumber"),
            queryParam<String?>("sort"),
            queryParam<String?>("filter"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Manga Chapters Feed")
                    description(
                        "Acquisition feed listing chapters for a specific manga. Supports pagination, sorting, and filtering. " +
                            "Facets for sorting and filtering are provided.",
                    )
                }
            },
            behaviorOf = { ctx, mangaId, pageNumber, sort, filter, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getMangaFeed(mangaId, BASE_URL, pageNumber ?: 1, sort, filter, locale)
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
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterIndex"),
            queryParam<String?>("lang"),
            documentWith = {
                withOperation {
                    summary("OPDS Chapter Details Feed")
                    description(
                        "Acquisition feed providing detailed metadata for a specific chapter, " +
                            "including download and streaming links if available.",
                    )
                }
            },
            behaviorOf = { ctx, mangaId, chapterIndex, lang ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                val locale: Locale = LocalizationHelper.ctxToLocale(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getChapterMetadataFeed(mangaId, chapterIndex, BASE_URL, locale)
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
