package suwayomi.tachidesk.anime

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.apibuilder.ApiBuilder.delete
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import suwayomi.tachidesk.anime.controller.AnimeController
import suwayomi.tachidesk.anime.controller.ExtensionController
import suwayomi.tachidesk.anime.controller.SourceController

object AnimeAPI {
    fun defineEndpoints() {
        path("anime/extension") {
            get("list", ExtensionController.list)

            get("install/{pkgName}", ExtensionController.install)
            post("install", ExtensionController.installFile)
            get("update/{pkgName}", ExtensionController.update)
            get("uninstall/{pkgName}", ExtensionController.uninstall)

            get("icon/{apkName}", ExtensionController.icon)
        }

        path("anime/source") {
            get("list", SourceController.list)
            get("{sourceId}", SourceController.retrieve)

            get("{sourceId}/popular/{pageNum}", SourceController.popular)
            get("{sourceId}/latest/{pageNum}", SourceController.latest)

            get("{sourceId}/preferences", SourceController.getPreferences)
            post("{sourceId}/preferences", SourceController.setPreference)

            get("{sourceId}/filters", SourceController.getFilters)
            post("{sourceId}/filters", SourceController.setFilters)

            get("{sourceId}/search", SourceController.searchSingle)
            post("{sourceId}/quick-search", SourceController.quickSearchSingle)
        }

        path("anime") {
            get("library", AnimeController.libraryList)
            get("{animeId}", AnimeController.retrieve)
            get("{animeId}/thumbnail", AnimeController.thumbnail)

            get("{animeId}/library", AnimeController.addToLibrary)
            delete("{animeId}/library", AnimeController.removeFromLibrary)

            get("{animeId}/episodes", AnimeController.episodeList)
            post("episode/batch", AnimeController.episodeBatch)
            get("{animeId}/episode/{episodeIndex}", AnimeController.episodeRetrieve)

            get("{animeId}/episode/{episodeIndex}/videos", AnimeController.episodeVideos)
            post("{animeId}/episode/{episodeIndex}/download/{videoIndex}", AnimeController.downloadEpisodeVideo)
            delete("{animeId}/episode/{episodeIndex}/download", AnimeController.deleteEpisodeDownloads)
            get("{animeId}/episode/{episodeIndex}/hosters", AnimeController.episodeHosters)
            get("{animeId}/episode/{episodeIndex}/hoster/{hosterIndex}/videos", AnimeController.hosterVideos)

            get("{animeId}/episode/{episodeIndex}/video/{videoIndex}", AnimeController.videoProxy)
            get("{animeId}/episode/{episodeIndex}/video/{videoIndex}/playlist", AnimeController.videoPlaylist)
            get("{animeId}/episode/{episodeIndex}/video/{videoIndex}/segment/{token}", AnimeController.videoSegmentProxy)
            get("{animeId}/episode/{episodeIndex}/video/{videoIndex}/subtitle/{token}", AnimeController.subtitleProxy)
            get("{animeId}/episode/{episodeIndex}/hoster/{hosterIndex}/video/{videoIndex}", AnimeController.hosterVideoProxy)
        }
    }
}
