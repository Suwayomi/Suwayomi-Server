package suwayomi.tachidesk.opds

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import suwayomi.tachidesk.opds.controller.OpdsV1Controller

object OpdsAPI {
    fun defineEndpoints() {
        path("opds/v1.2") {
            // Root feed (Navigation Feed)
            get(OpdsV1Controller.rootFeed)

            // Search
            get("search", OpdsV1Controller.searchFeed)
            get("search.xml", OpdsV1Controller.searchDescription)

            // Complete feed for crawlers
            // get("complete", OpdsV1Controller.completeFeed)

            // Main groupings
            get("mangas", OpdsV1Controller.mangasFeed)
            get("sources", OpdsV1Controller.sourcesFeed)
            get("categories", OpdsV1Controller.categoriesFeed)
            get("genres", OpdsV1Controller.genresFeed)
            get("status", OpdsV1Controller.statusFeed)
            get("languages", OpdsV1Controller.languagesFeed)

            // Faceted feeds (Acquisition Feeds)
            path("manga/{mangaId}") {
                get(OpdsV1Controller.mangaFeed)
            }

            path("source/{sourceId}") {
                get(OpdsV1Controller.sourceFeed)
            }

            path("category/{categoryId}") {
                get(OpdsV1Controller.categoryFeed)
            }

            path("genre/{genre}") {
                get(OpdsV1Controller.genreFeed)
            }

            path("status/{statusId}") {
                get(OpdsV1Controller.statusMangaFeed)
            }

            path("language/{langCode}") {
                get(OpdsV1Controller.languageFeed)
            }
        }
    }
}
