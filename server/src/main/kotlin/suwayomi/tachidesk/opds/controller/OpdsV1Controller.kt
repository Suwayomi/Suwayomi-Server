package suwayomi.tachidesk.opds.controller

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

    // Feed raíz
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

    // Descripción de búsqueda
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
                    <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
                        <ShortName>Suwayomi OPDS Search</ShortName>
                        <Description>Search manga in the catalog</Description>
                        <InputEncoding>UTF-8</InputEncoding>
                        <OutputEncoding>UTF-8</OutputEncoding>
                        <Url type="application/atom+xml;profile=opds-catalog" 
                             template="$BASE_URL/search?q={{searchTerms}}&amp;page={startPage?}"/>
                    </OpenSearchDescription>
                    """.trimIndent(),
                )
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Feed de búsqueda
    val searchFeed =
        handler(
            queryParam<String>("q"),
            queryParam<Int?>("page"),
            documentWith = {
                withOperation {
                    summary("OPDS Search Results")
                    description("OPDS feed containing search results")
                }
            },
            behaviorOf = { ctx, query, page ->
                ctx.future {
                    future {
                        Opds.searchManga(query, BASE_URL, page ?: 1)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Feed completo para crawlers
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

    // Agrupación principal de mangas
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
                        Opds.getMangasFeed(BASE_URL)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Agrupación principal de fuentes
    val sourcesFeed =
        handler(
            documentWith = {
                withOperation {
                    summary("OPDS Sources Feed")
                    description("OPDS feed for primary grouping of manga sources")
                }
            },
            behaviorOf = { ctx ->
                ctx.future {
                    future {
                        Opds.getSourcesFeed(BASE_URL)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Agrupación principal de categorías
    val categoriesFeed =
        handler(
            documentWith = {
                withOperation {
                    summary("OPDS Categories Feed")
                    description("OPDS feed for primary grouping of manga categories")
                }
            },
            behaviorOf = { ctx ->
                ctx.future {
                    future {
                        Opds.getCategoriesFeed(BASE_URL)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Agrupación principal de géneros
    val genresFeed =
        handler(
            documentWith = {
                withOperation {
                    summary("OPDS Genres Feed")
                    description("OPDS feed for primary grouping of manga genres")
                }
            },
            behaviorOf = { ctx ->
                ctx.future {
                    future {
                        Opds.getGenresFeed(BASE_URL)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Agrupación principal por estado
    val statusFeed =
        handler(
            documentWith = {
                withOperation {
                    summary("OPDS Status Feed")
                    description("OPDS feed for primary grouping of manga by status")
                }
            },
            behaviorOf = { ctx ->
                ctx.future {
                    future {
                        Opds.getStatusFeed(BASE_URL)
                    }.thenApply { xml ->
                        ctx.contentType(OPDS_MIME).result(xml)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    // Agrupación principal por idiomas
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

    // Feed de capítulos de un manga
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

    // Feed de una fuente específica
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

    // Feed por faceta: Categoría específica
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
                        Opds.getCategoryFeed(categoryId, BASE_URL)
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

    // Feed por faceta: Género específico
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
                        Opds.getGenreFeed(genre, BASE_URL)
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

    // Feed por faceta: Estado específico
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
                        Opds.getStatusMangaFeed(statusId, BASE_URL)
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

    // Feed por faceta: Idioma específico
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
                        Opds.getLanguageFeed(langCode, BASE_URL)
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
