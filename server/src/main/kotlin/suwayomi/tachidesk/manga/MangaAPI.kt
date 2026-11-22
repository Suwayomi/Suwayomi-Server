package suwayomi.tachidesk.manga

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.head
import io.javalin.apibuilder.ApiBuilder.path
import suwayomi.tachidesk.manga.controller.ExtensionController
import suwayomi.tachidesk.manga.controller.MangaController
import suwayomi.tachidesk.manga.controller.TrackController

object MangaAPI {
    fun defineEndpoints() {
        path("extension") {
            get("icon/{pkgName}", ExtensionController.icon)
        }

        path("manga") {
            get("{mangaId}/thumbnail", MangaController.thumbnail)

            get("{mangaId}/chapter/{chapterIndex}/page/{index}", MangaController.pageRetrieve)
        }

        path("chapter") {
            get("{chapterId}/download", MangaController.downloadChapter)
            head("{chapterId}/download", MangaController.downloadChapter)
        }

        path("track") {
            get("{trackerId}/thumbnail", TrackController.thumbnail)
        }
    }
}
