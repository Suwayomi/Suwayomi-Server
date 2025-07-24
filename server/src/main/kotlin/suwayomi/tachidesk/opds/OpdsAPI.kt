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

            // --- Main Navigation Feeds ---

            // Explore Navigation Feed
            get("explore", OpdsV1Controller.exploreSourcesFeed)

            // Reading History Acquisition Feed
            get("history", OpdsV1Controller.historyFeed)

            // Library Updates Acquisition Feed
            get("library-updates", OpdsV1Controller.libraryUpdatesFeed)

            // --- Library-Specific Feeds ---
            path("library") {
                // All Series in Library / Search Results Feed (Acquisition)
                get("series", OpdsV1Controller.seriesFeed)

                // Library Sources Navigation Feed
                get("sources", OpdsV1Controller.librarySourcesFeed)

                // Library Source-Specific Series Acquisition Feed
                path("source/{sourceId}") {
                    get(OpdsV1Controller.librarySourceFeed)
                }

                // Library Categories Navigation Feed
                get("categories", OpdsV1Controller.categoriesFeed)

                // Library Genres Navigation Feed
                get("genres", OpdsV1Controller.genresFeed)

                // Library Status Navigation Feed
                get("status", OpdsV1Controller.statusFeed)

                // Library Content Languages Navigation Feed
                get("languages", OpdsV1Controller.languagesFeed)
            }

            // --- Explore-Specific Feeds ---

            // All Sources Navigation Feed (Explore)
            get("sources", OpdsV1Controller.exploreSourcesFeed)

            // Source-Specific Series Acquisition Feed (Explore)
            path("source/{sourceId}") {
                get(OpdsV1Controller.exploreSourceFeed)
            }

            // --- Item-Specific Feeds (Apply to both Library and Explore contexts) ---

            // Series Chapters Acquisition Feed
            path("series/{seriesId}/chapters") {
                get(OpdsV1Controller.seriesChaptersFeed)
            }

            // Chapter Metadata Acquisition Feed
            path("series/{seriesId}/chapter/{chapterIndex}/metadata") {
                get(OpdsV1Controller.chapterMetadataFeed)
            }

            // Category-Specific Series Acquisition Feed (Library)
            path("category/{categoryId}") {
                get(OpdsV1Controller.categoryFeed)
            }

            // Genre-Specific Series Acquisition Feed (Library)
            path("genre/{genre}") {
                get(OpdsV1Controller.genreFeed)
            }

            // Status-Specific Series Acquisition Feed (Library)
            path("status/{statusId}") {
                get(OpdsV1Controller.statusMangaFeed)
            }

            // Language-Specific Series Acquisition Feed (Library)
            path("language/{langCode}") {
                get(OpdsV1Controller.languageFeed)
            }
        }
    }
}
