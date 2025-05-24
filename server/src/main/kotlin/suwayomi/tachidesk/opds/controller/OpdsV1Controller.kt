package suwayomi.tachidesk.opds.controller

import io.javalin.http.Context
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.i18n.LocalizationService
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.impl.OpdsFeedBuilder
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation

object OpdsV1Controller {
    private const val OPDS_MIME = "application/atom+xml;profile=opds-catalog;charset=UTF-8"
    private const val BASE_URL = "/api/opds/v1.2"

    private fun determineLanguage(
        ctx: Context,
        langParam: String?,
    ): String {
        langParam?.trim()?.takeIf { it.isNotBlank() }?.lowercase()?.let {
            return it
        }
        ctx
            .header("Accept-Language")
            ?.split(",")
            ?.firstOrNull()
            ?.split(";")
            ?.firstOrNull()
            ?.trim()
            ?.lowercase()
            ?.let {
                return it
            }
        return LocalizationService.getDefaultLocale()
    }

    // Root Feed
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getRootFeed(BASE_URL, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Search Description
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
                val determinedLang = determineLanguage(ctx, lang)

                // The OpenSearch Description itself is localized by determinedLang
                // The template URL for searches will point to mangasFeed, which will handle its own lang
                ctx.contentType("application/opensearchdescription+xml").result(
                    """
                    <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/"
                        xmlns:atom="http://www.w3.org/2005/Atom">
                        <ShortName>${
                        LocalizationService.getString(
                            determinedLang,
                            "opds.search.shortname",
                            defaultValue = "Suwayomi OPDS Search",
                        )}</ShortName>
                        <Description>${LocalizationService.getString(
                        determinedLang,
                        "opds.search.description",
                        defaultValue = "Search manga in the catalog",
                    )}</Description>
                        <InputEncoding>UTF-8</InputEncoding>
                        <OutputEncoding>UTF-8</OutputEncoding>
                        <Url type="${OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION}" 
                            rel="results" 
                            template="$BASE_URL/mangas?query={searchTerms}&amp;lang=$determinedLang"/>
                    </OpenSearchDescription>
                    """.trimIndent(), // Added lang to template
                )
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Complete Feed for Crawlers
//    val completeFeed = handler(
//        documentWith = {
//            withOperation {
//                summary("OPDS Complete Acquisition Feed")
//                description(
//                    "Complete Acquisition Feed for Crawling: " +
//                        "This feed provides a full representation of every unique catalog entry " +
//                        "to facilitate crawling and aggregation. " +
//                        "It must be referenced using the relation 'http://opds-spec.org/crawlable' " +
//                        "and is not paginated unless extremely large."
//                )
//            }
//        },
//        behaviorOf = { ctx ->
//            ctx.future {
//                future {
//                    Opds.getCompleteFeed(BASE_URL)
//                }.thenApply { xml ->
//                    ctx.contentType("application/atom+xml;profile=opds-catalog;kind=acquisition").result(xml)
//                }
//            }
//        },
//        withResults = {
//            httpCode(HttpStatus.OK)
//        },
//    )

    // Main Manga Grouping
    // Search Feed
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
                val determinedLang = determineLanguage(ctx, lang)
                val opdsSearchCriteria =
                    if (query != null || author != null || title != null) {
                        OpdsSearchCriteria(query, author, title)
                    } else {
                        null
                    }
                val effectivePageNumber = if (opdsSearchCriteria != null) 1 else pageNumber ?: 1

                ctx.future {
                    future {
                        OpdsFeedBuilder.getMangasFeed(opdsSearchCriteria, BASE_URL, effectivePageNumber, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Main Sources Grouping
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getSourcesFeed(BASE_URL, pageNumber ?: 1, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Main Categories Grouping
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getCategoriesFeed(BASE_URL, pageNumber ?: 1, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Main Genres Grouping
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getGenresFeed(BASE_URL, pageNumber ?: 1, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Main Status Grouping
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getStatusFeed(BASE_URL, pageNumber ?: 1, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Main Languages Grouping
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLanguagesFeed(BASE_URL, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Manga Chapters Feed
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getMangaFeed(mangaId, BASE_URL, pageNumber ?: 1, sort, filter, determinedLang)
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

    val chapterMetadataFeed =
        handler(
            pathParam<Int>("mangaId"),
            pathParam<Int>("chapterId"),
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
            behaviorOf = { ctx, mangaId, chapterId, lang ->
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getChapterMetadataFeed(mangaId, chapterId, BASE_URL, determinedLang)
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

    // Specific Source Feed
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getSourceFeed(sourceId, BASE_URL, pageNumber ?: 1, determinedLang)
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

    // Facet Feed: Specific Category
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getCategoryFeed(categoryId, BASE_URL, pageNumber ?: 1, determinedLang)
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

    // Facet Feed: Specific Genre
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getGenreFeed(genre, BASE_URL, pageNumber ?: 1, determinedLang)
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

    // Facet Feed: Specific Status
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getStatusMangaFeed(statusId, BASE_URL, pageNumber ?: 1, determinedLang)
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

    // Facet Feed: Specific Language
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
                val determinedUiLang = determineLanguage(ctx, uiLangParam)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLanguageFeed(contentLangCodePath, BASE_URL, pageNumber ?: 1, determinedUiLang)
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

    // Main Library Updates Feed
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
                val determinedLang = determineLanguage(ctx, lang)
                ctx.future {
                    future {
                        OpdsFeedBuilder.getLibraryUpdatesFeed(BASE_URL, pageNumber ?: 1, determinedLang)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )
}
