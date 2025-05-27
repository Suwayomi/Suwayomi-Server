package suwayomi.tachidesk.opds

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import suwayomi.tachidesk.opds.controller.OpdsV1Controller

object OpdsAPI {
    fun defineEndpoints() {
        path("opds/v1.2") {
            // OPDS Catalog Root Feed (Navigation)
            get(OpdsV1Controller.rootFeed)

            // OPDS Search Description Feed
            get("search", OpdsV1Controller.searchFeed)

            // Complete feed for crawlers
            // get("complete", OpdsV1Controller.completeFeed)

            // --- Main Navigation & Broad Acquisition Feeds ---

            // All Mangas / Search Results Feed (Acquisition)
            get("mangas", OpdsV1Controller.mangasFeed)

            // Sources Navigation Feed
            get("sources", OpdsV1Controller.sourcesFeed)

            // Categories Navigation Feed
            get("categories", OpdsV1Controller.categoriesFeed)

            // Genres Navigation Feed
            get("genres", OpdsV1Controller.genresFeed)

            // Status Navigation Feed
            get("status", OpdsV1Controller.statusFeed)

            // Content Languages Navigation Feed
            get("languages", OpdsV1Controller.languagesFeed)

            // Library Updates Acquisition Feed
            get("library-updates", OpdsV1Controller.libraryUpdatesFeed)

            // --- Filtered & Item-Specific Acquisition Feeds ---

            // Manga Chapters Acquisition Feed
            path("manga/{mangaId}/chapters") {
                get(OpdsV1Controller.mangaFeed)
            }

            // Chapter Metadata Acquisition Feed
            path("manga/{mangaId}/chapter/{chapterIndex}/metadata") {
                get(OpdsV1Controller.chapterMetadataFeed)
            }

            // Source-Specific Manga Acquisition Feed
            path("source/{sourceId}") {
                get(OpdsV1Controller.sourceFeed)
            }

            // Category-Specific Manga Acquisition Feed
            path("category/{categoryId}") {
                get(OpdsV1Controller.categoryFeed)
            }

            // Genre-Specific Manga Acquisition Feed
            path("genre/{genre}") {
                get(OpdsV1Controller.genreFeed)
            }

            // Status-Specific Manga Acquisition Feed
            path("status/{statusId}") {
                get(OpdsV1Controller.statusMangaFeed)
            }

            // Language-Specific Manga Acquisition Feed
            path("language/{langCode}") {
                get(OpdsV1Controller.languageFeed)
            }
        }
    }
}
