package suwayomi.tachidesk.opds

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import suwayomi.tachidesk.opds.controller.OpdsV1Controller

object OpdsAPI {
    fun defineEndpoints() {
        path("opds/v1.2") {
            // Feed raíz (Navigation Feed)
            get(OpdsV1Controller.rootFeed)

            // Búsqueda
            get("search", OpdsV1Controller.searchFeed)
            get("search.xml", OpdsV1Controller.searchDescription)

            // Feed completo para crawlers
//            get("complete", OpdsV1Controller.completeFeed)

            // Agrupaciones principales
            get("mangas", OpdsV1Controller.mangasFeed)
            get("sources", OpdsV1Controller.sourcesFeed)
            get("categories", OpdsV1Controller.categoriesFeed)
            get("genres", OpdsV1Controller.genresFeed)
            get("status", OpdsV1Controller.statusFeed)
            get("languages", OpdsV1Controller.languagesFeed)

            // Feeds por facetas (Acquisition Feeds)
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
