package suwayomi.tachidesk.opds.controller

import SearchCriteria
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.opds.impl.Opds
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.pathParam
import suwayomi.tachidesk.server.util.queryParam
import suwayomi.tachidesk.server.util.withOperation

object OpdsV1Controller {
    private const val OPDS_MIME = "application/xml;profile=opds-catalog;charset=UTF-8"
    private const val BASE_URL = "/api/opds/v1.2"

    // Root Feed
    val rootFeed =
        handler(
            documentWith = {
                withOperation {
                    summary("OPDS Root Feed")
                    description("")
                }
            },
            behaviorOf = { ctx ->
                ctx.future {
                    future {
                        Opds.getRootFeed(BASE_URL)
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
    val searchDescription =
        handler(
            documentWith = {
                withOperation {
                    summary("OpenSearch Description")
                    description("XML description for OPDS searches")
                }
            },
            behaviorOf = { ctx ->
                ctx.contentType("application/opensearchdescription+xml").result(
                    """
                    <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/"
                        xmlns:atom="http://www.w3.org/2005/Atom">
                        <ShortName>Suwayomi OPDS Search</ShortName>
                        <Description>Search manga in the catalog</Description>
                        <InputEncoding>UTF-8</InputEncoding>
                        <OutputEncoding>UTF-8</OutputEncoding>
                        <Url type="application/atom+xml;profile=opds-catalog" 
                            template="$BASE_URL/search?q={searchTerms}&amp;author={atom:author?}&amp;title={atom:title?}&amp;page={startPage?}"/>
                    </OpenSearchDescription>
                    """.trimIndent(),
                )
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Search Feed
    val searchFeed =
        handler(
            queryParam<String?>("q"),
            queryParam<String?>("author"),
            queryParam<String?>("title"),
            queryParam<Int?>("page"),
            documentWith = {
                withOperation {
                    summary("OPDS Search Results")
                    description("OPDS feed containing search results")
                }
            },
            behaviorOf = { ctx, query, author, title, page ->
                val searchCriteria = SearchCriteria(query, author, title)
                ctx.future {
                    future {
                        Opds.searchManga(searchCriteria, BASE_URL, page ?: 1)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
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
    val mangasFeed =
        handler(
            queryParam<Int?>("pageNumber"),
            documentWith = {
                withOperation {
                    summary("OPDS Mangas Feed")
                    description("OPDS feed for primary grouping of manga entries")
                }
            },
            behaviorOf = { ctx, pageNumber ->
                ctx.future {
                    future {
                        Opds.getMangasFeed(BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Sources Feed")
                    description("OPDS feed for primary grouping of manga sources")
                }
            },
            behaviorOf = { ctx, pageNumber ->
                ctx.future {
                    future {
                        Opds.getSourcesFeed(BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Categories Feed")
                    description("OPDS feed for primary grouping of manga categories")
                }
            },
            behaviorOf = { ctx, pageNumber ->
                ctx.future {
                    future {
                        Opds.getCategoriesFeed(BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Genres Feed")
                    description("OPDS feed for primary grouping of manga genres")
                }
            },
            behaviorOf = { ctx, pageNumber ->
                ctx.future {
                    future {
                        Opds.getGenresFeed(BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Status Feed")
                    description("OPDS feed for primary grouping of manga by status")
                }
            },
            behaviorOf = { ctx, pageNumber ->
                ctx.future {
                    future {
                        Opds.getStatusFeed(BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Languages Feed")
                    description("OPDS feed for primary grouping of available languages")
                }
            },
            behaviorOf = { ctx ->
                ctx.future {
                    future {
                        Opds.getLanguagesFeed(BASE_URL)
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
            documentWith = {
                withOperation {
                    summary("OPDS Manga Feed")
                    description("OPDS feed for chapters of a specific manga")
                }
            },
            behaviorOf = { ctx, mangaId, pageNumber ->
                ctx.future {
                    future {
                        Opds.getMangaFeed(mangaId, BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Source Feed")
                    description("OPDS feed for a specific manga source")
                }
            },
            behaviorOf = { ctx, sourceId, pageNumber ->
                ctx.future {
                    future {
                        Opds.getSourceFeed(sourceId, BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Category Feed")
                    description("OPDS feed for a specific manga category")
                }
            },
            behaviorOf = { ctx, categoryId, pageNumber ->
                ctx.future {
                    future {
                        Opds.getCategoryFeed(categoryId, BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Genre Feed")
                    description("OPDS feed for a specific manga genre")
                }
            },
            behaviorOf = { ctx, genre, pageNumber ->
                ctx.future {
                    future {
                        Opds.getGenreFeed(genre, BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Status Manga Feed")
                    description("OPDS feed for manga filtered by status")
                }
            },
            behaviorOf = { ctx, statusId, pageNumber ->
                ctx.future {
                    future {
                        Opds.getStatusMangaFeed(statusId, BASE_URL, pageNumber ?: 1)
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
            documentWith = {
                withOperation {
                    summary("OPDS Language Feed")
                    description("OPDS feed for manga filtered by language")
                }
            },
            behaviorOf = { ctx, langCode, pageNumber ->
                ctx.future {
                    future {
                        Opds.getLanguageFeed(langCode, BASE_URL, pageNumber ?: 1)
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
