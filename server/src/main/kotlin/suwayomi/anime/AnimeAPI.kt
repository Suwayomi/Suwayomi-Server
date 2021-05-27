package suwayomi.anime

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import suwayomi.anime.impl.Anime.getAnime
import suwayomi.anime.impl.Anime.getAnimeThumbnail
import suwayomi.anime.impl.AnimeList.getAnimeList
import suwayomi.anime.impl.Episode.getEpisode
import suwayomi.anime.impl.Episode.getEpisodeList
import suwayomi.anime.impl.Episode.modifyEpisode
import suwayomi.anime.impl.Source.getAnimeSource
import suwayomi.anime.impl.Source.getSourceList
import suwayomi.anime.impl.extension.Extension.getExtensionIcon
import suwayomi.anime.impl.extension.Extension.installExtension
import suwayomi.anime.impl.extension.Extension.uninstallExtension
import suwayomi.anime.impl.extension.Extension.updateExtension
import suwayomi.anime.impl.extension.ExtensionsList.getExtensionList
import suwayomi.server.JavalinSetup.future

object AnimeAPI {
    fun defineEndpoints(app: Javalin) {
        // list all extensions
        app.get("/api/v1/anime/extension/list") { ctx ->
            ctx.json(
                future {
                    getExtensionList()
                }
            )
        }

        // install extension identified with "pkgName"
        app.get("/api/v1/anime/extension/install/:pkgName") { ctx ->
            val pkgName = ctx.pathParam("pkgName")

            ctx.json(
                future {
                    installExtension(pkgName)
                }
            )
        }

        // update extension identified with "pkgName"
        app.get("/api/v1/anime/extension/update/:pkgName") { ctx ->
            val pkgName = ctx.pathParam("pkgName")

            ctx.json(
                future {
                    updateExtension(pkgName)
                }
            )
        }

        // uninstall extension identified with "pkgName"
        app.get("/api/v1/anime/extension/uninstall/:pkgName") { ctx ->
            val pkgName = ctx.pathParam("pkgName")

            uninstallExtension(pkgName)
            ctx.status(200)
        }

        // icon for extension named `apkName`
        app.get("/api/v1/anime/extension/icon/:apkName") { ctx -> // TODO: move to pkgName
            val apkName = ctx.pathParam("apkName")

            ctx.result(
                future { getExtensionIcon(apkName) }
                    .thenApply {
                        ctx.header("content-type", it.second)
                        it.first
                    }
            )
        }

        // list of sources
        app.get("/api/v1/anime/source/list") { ctx ->
            ctx.json(getSourceList())
        }

        // fetch source with id `sourceId`
        app.get("/api/v1/anime/source/:sourceId") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            ctx.json(getAnimeSource(sourceId))
        }

        // popular animes from source with id `sourceId`
        app.get("/api/v1/anime/source/:sourceId/popular/:pageNum") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            val pageNum = ctx.pathParam("pageNum").toInt()
            ctx.json(
                future {
                    getAnimeList(sourceId, pageNum, popular = true)
                }
            )
        }

        // latest animes from source with id `sourceId`
        app.get("/api/v1/anime/source/:sourceId/latest/:pageNum") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            val pageNum = ctx.pathParam("pageNum").toInt()
            ctx.json(
                future {
                    getAnimeList(sourceId, pageNum, popular = false)
                }
            )
        }

        // get anime info
        app.get("/api/v1/anime/anime/:animeId/") { ctx ->
            val animeId = ctx.pathParam("animeId").toInt()
            val onlineFetch = ctx.queryParam("onlineFetch", "false").toBoolean()

            ctx.json(
                future {
                    getAnime(animeId, onlineFetch)
                }
            )
        }

        // anime thumbnail
        app.get("api/v1/anime/anime/:animeId/thumbnail") { ctx ->
            val animeId = ctx.pathParam("animeId").toInt()

            ctx.result(
                future { getAnimeThumbnail(animeId) }
                    .thenApply {
                        ctx.header("content-type", it.second)
                        it.first
                    }
            )
        }
//
//        // list manga's categories
//        app.get("api/v1/manga/:mangaId/category/") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//            ctx.json(getMangaCategories(mangaId))
//        }
//
//        // adds the manga to category
//        app.get("api/v1/manga/:mangaId/category/:categoryId") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            addMangaToCategory(mangaId, categoryId)
//            ctx.status(200)
//        }
//
//        // removes the manga from the category
//        app.delete("api/v1/manga/:mangaId/category/:categoryId") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            removeMangaFromCategory(mangaId, categoryId)
//            ctx.status(200)
//        }
//
        // get episode list when showing a anime
        app.get("/api/v1/anime/anime/:animeId/episodes") { ctx ->
            val animeId = ctx.pathParam("animeId").toInt()

            val onlineFetch = ctx.queryParam("onlineFetch")?.toBoolean()

            ctx.json(future { getEpisodeList(animeId, onlineFetch) })
        }

        // used to display a episode, get a episode in order to show it's <Quality pending>
        app.get("/api/v1/anime/anime/:animeId/episode/:episodeIndex") { ctx ->
            val episodeIndex = ctx.pathParam("episodeIndex").toInt()
            val animeId = ctx.pathParam("animeId").toInt()
            ctx.json(future { getEpisode(episodeIndex, animeId) })
        }

        // used to modify a episode's parameters
        app.patch("/api/v1/anime/anime/:animeId/episode/:episodeIndex") { ctx ->
            val episodeIndex = ctx.pathParam("episodeIndex").toInt()
            val animeId = ctx.pathParam("animeId").toInt()

            val read = ctx.formParam("read")?.toBoolean()
            val bookmarked = ctx.formParam("bookmarked")?.toBoolean()
            val markPrevRead = ctx.formParam("markPrevRead")?.toBoolean()
            val lastPageRead = ctx.formParam("lastPageRead")?.toInt()

            modifyEpisode(animeId, episodeIndex, read, bookmarked, markPrevRead, lastPageRead)

            ctx.status(200)
        }
//
//        // get page at index "index"
//        app.get("/api/v1/manga/:mangaId/chapter/:chapterIndex/page/:index") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//            val chapterIndex = ctx.pathParam("chapterIndex").toInt()
//            val index = ctx.pathParam("index").toInt()
//
//            ctx.result(
//                JavalinSetup.future { getPageImage(mangaId, chapterIndex, index) }
//                    .thenApply {
//                        ctx.header("content-type", it.second)
//                        it.first
//                    }
//            )
//        }
//
//        // submit a chapter for download
//        app.put("/api/v1/manga/:mangaId/chapter/:chapterIndex/download") { ctx ->
//            // TODO
//        }
//
//        // cancel a chapter download
//        app.delete("/api/v1/manga/:mangaId/chapter/:chapterIndex/download") { ctx ->
//            // TODO
//        }
//
//        // global search, Not implemented yet
//        app.get("/api/v1/search/:searchTerm") { ctx ->
//            val searchTerm = ctx.pathParam("searchTerm")
//            ctx.json(sourceGlobalSearch(searchTerm))
//        }
//
//        // single source search
//        app.get("/api/v1/source/:sourceId/search/:searchTerm/:pageNum") { ctx ->
//            val sourceId = ctx.pathParam("sourceId").toLong()
//            val searchTerm = ctx.pathParam("searchTerm")
//            val pageNum = ctx.pathParam("pageNum").toInt()
//            ctx.json(JavalinSetup.future { sourceSearch(sourceId, searchTerm, pageNum) })
//        }
//
//        // source filter list
//        app.get("/api/v1/source/:sourceId/filters/") { ctx ->
//            val sourceId = ctx.pathParam("sourceId").toLong()
//            ctx.json(sourceFilters(sourceId))
//        }
//
//        // adds the manga to library
//        app.get("api/v1/manga/:mangaId/library") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//
//            ctx.result(
//                JavalinSetup.future { addMangaToLibrary(mangaId) }
//            )
//        }
//
//        // removes the manga from the library
//        app.delete("api/v1/manga/:mangaId/library") { ctx ->
//            val mangaId = ctx.pathParam("mangaId").toInt()
//
//            ctx.result(
//                JavalinSetup.future { removeMangaFromLibrary(mangaId) }
//            )
//        }
//
//        // lists mangas that have no category assigned
//        app.get("/api/v1/library/") { ctx ->
//            ctx.json(getLibraryMangas())
//        }
//
//        // category list
//        app.get("/api/v1/category/") { ctx ->
//            ctx.json(Category.getCategoryList())
//        }
//
//        // category create
//        app.post("/api/v1/category/") { ctx ->
//            val name = ctx.formParam("name")!!
//            Category.createCategory(name)
//            ctx.status(200)
//        }
//
//        // returns some static info of the current app build
//        app.get("/api/v1/about/") { ctx ->
//            ctx.json(About.getAbout())
//        }
//
//        // category modification
//        app.patch("/api/v1/category/:categoryId") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            val name = ctx.formParam("name")
//            val isDefault = ctx.formParam("default")?.toBoolean()
//            Category.updateCategory(categoryId, name, isDefault)
//            ctx.status(200)
//        }
//
//        // category re-ordering
//        app.patch("/api/v1/category/:categoryId/reorder") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            val from = ctx.formParam("from")!!.toInt()
//            val to = ctx.formParam("to")!!.toInt()
//            Category.reorderCategory(categoryId, from, to)
//            ctx.status(200)
//        }
//
//        // category delete
//        app.delete("/api/v1/category/:categoryId") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            Category.removeCategory(categoryId)
//            ctx.status(200)
//        }
//
//        // returns the manga list associated with a category
//        app.get("/api/v1/category/:categoryId") { ctx ->
//            val categoryId = ctx.pathParam("categoryId").toInt()
//            ctx.json(getCategoryMangaList(categoryId))
//        }
//
//        // expects a Tachiyomi legacy backup json in the body
//        app.post("/api/v1/backup/legacy/import") { ctx ->
//            ctx.result(
//                future {
//                    restoreLegacyBackup(ctx.bodyAsInputStream())
//                }
//            )
//        }
//
//        // expects a Tachiyomi legacy backup json as a file upload, the file must be named "backup.json"
//        app.post("/api/v1/backup/legacy/import/file") { ctx ->
//            ctx.result(
//                JavalinSetup.future {
//                    restoreLegacyBackup(ctx.uploadedFile("backup.json")!!.content)
//                }
//            )
//        }
//
//        // returns a Tachiyomi legacy backup json created from the current database as a json body
//        app.get("/api/v1/backup/legacy/export") { ctx ->
//            ctx.contentType("application/json")
//            ctx.result(
//                JavalinSetup.future {
//                    createLegacyBackup(
//                        BackupFlags(
//                            includeManga = true,
//                            includeCategories = true,
//                            includeChapters = true,
//                            includeTracking = true,
//                            includeHistory = true,
//                        )
//                    )
//                }
//            )
//        }
//
//        // returns a Tachiyomi legacy backup json created from the current database as a file
//        app.get("/api/v1/backup/legacy/export/file") { ctx ->
//            ctx.contentType("application/json")
//            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm")
//            val currentDate = sdf.format(Date())
//
//            ctx.header("Content-Disposition", "attachment; filename=\"tachidesk_$currentDate.json\"")
//            ctx.result(
//                JavalinSetup.future {
//                    createLegacyBackup(
//                        BackupFlags(
//                            includeManga = true,
//                            includeCategories = true,
//                            includeChapters = true,
//                            includeTracking = true,
//                            includeHistory = true,
//                        )
//                    )
//                }
//            )
//        }
//
//        // Download queue stats
//        app.ws("/api/v1/downloads") { ws ->
//            ws.onConnect { ctx ->
//                // TODO: send current stat
//                // TODO: add to downlad subscribers
//            }
//            ws.onMessage {
//                // TODO: send current stat
//            }
//            ws.onClose { ctx ->
//                // TODO: remove from subscribers
//            }
//        }
    }
}
