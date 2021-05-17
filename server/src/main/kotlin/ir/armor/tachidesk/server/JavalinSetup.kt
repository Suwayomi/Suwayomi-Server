package ir.armor.tachidesk.server

import io.javalin.Javalin
import ir.armor.tachidesk.impl.Category.createCategory
import ir.armor.tachidesk.impl.Category.getCategoryList
import ir.armor.tachidesk.impl.Category.removeCategory
import ir.armor.tachidesk.impl.Category.reorderCategory
import ir.armor.tachidesk.impl.Category.updateCategory
import ir.armor.tachidesk.impl.CategoryManga.addMangaToCategory
import ir.armor.tachidesk.impl.CategoryManga.getCategoryMangaList
import ir.armor.tachidesk.impl.CategoryManga.getMangaCategories
import ir.armor.tachidesk.impl.CategoryManga.removeMangaFromCategory
import ir.armor.tachidesk.impl.Chapter.getChapter
import ir.armor.tachidesk.impl.Chapter.getChapterList
import ir.armor.tachidesk.impl.Chapter.modifyChapter
import ir.armor.tachidesk.impl.Extension.getExtensionIcon
import ir.armor.tachidesk.impl.Extension.installExtension
import ir.armor.tachidesk.impl.Extension.uninstallExtension
import ir.armor.tachidesk.impl.Extension.updateExtension
import ir.armor.tachidesk.impl.ExtensionsList.getExtensionList
import ir.armor.tachidesk.impl.Library.addMangaToLibrary
import ir.armor.tachidesk.impl.Library.getLibraryMangas
import ir.armor.tachidesk.impl.Library.removeMangaFromLibrary
import ir.armor.tachidesk.impl.Manga.getManga
import ir.armor.tachidesk.impl.Manga.getMangaThumbnail
import ir.armor.tachidesk.impl.MangaList.getMangaList
import ir.armor.tachidesk.impl.Page.getPageImage
import ir.armor.tachidesk.impl.Search.sourceFilters
import ir.armor.tachidesk.impl.Search.sourceGlobalSearch
import ir.armor.tachidesk.impl.Search.sourceSearch
import ir.armor.tachidesk.impl.Source.getSource
import ir.armor.tachidesk.impl.Source.getSourceList
import ir.armor.tachidesk.impl.backup.BackupFlags
import ir.armor.tachidesk.impl.backup.legacy.LegacyBackupExport.createLegacyBackup
import ir.armor.tachidesk.impl.backup.legacy.LegacyBackupImport.restoreLegacyBackup
import ir.armor.tachidesk.server.internal.About.getAbout
import ir.armor.tachidesk.server.util.openInBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import mu.KotlinLogging
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object JavalinSetup {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Executors.newFixedThreadPool(200).asCoroutineDispatcher())

    private fun <T> future(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        return scope.future(block = block)
    }

    fun javalinSetup() {
        var hasWebUiBundled = false

        val app = Javalin.create { config ->
            try {
                // if the bellow line throws an exception then webUI is not bundled
                this::class.java.getResource("/react/index.html")

                // no exception so we can tell javalin to serve webUI
                hasWebUiBundled = true
                config.addStaticFiles("/react")
                config.addSinglePageRoot("/", "/react/index.html")
            } catch (e: RuntimeException) {
                logger.warn("react build files are missing.")
                hasWebUiBundled = false
            }
            config.enableCorsForAllOrigins()
        }.start(serverConfig.ip, serverConfig.port)

        // when JVM is prompted to shutdown, stop javalin gracefully
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                app.stop()
            }
        )

        if (hasWebUiBundled && serverConfig.initialOpenInBrowserEnabled) {
            openInBrowser()
        }

        app.exception(NullPointerException::class.java) { e, ctx ->
            logger.error("NullPointerException while handling the request", e)
            ctx.status(404)
        }

        app.exception(IOException::class.java) { e, ctx ->
            logger.error("IOException while handling the request", e)
            ctx.status(500)
            ctx.result(e.message ?: "Internal Server Error")
        }

        // list all extensions
        app.get("/api/v1/extension/list") { ctx ->
            ctx.json(
                future {
                    getExtensionList()
                }
            )
        }

        // install extension identified with "pkgName"
        app.get("/api/v1/extension/install/:pkgName") { ctx ->
            val pkgName = ctx.pathParam("pkgName")

            ctx.json(
                future {
                    installExtension(pkgName)
                }
            )
        }

        // update extension identified with "pkgName"
        app.get("/api/v1/extension/update/:pkgName") { ctx ->
            val pkgName = ctx.pathParam("pkgName")

            ctx.json(
                future {
                    updateExtension(pkgName)
                }
            )
        }

        // uninstall extension identified with "pkgName"
        app.get("/api/v1/extension/uninstall/:pkgName") { ctx ->
            val pkgName = ctx.pathParam("pkgName")

            uninstallExtension(pkgName)
            ctx.status(200)
        }

        // icon for extension named `apkName`
        app.get("/api/v1/extension/icon/:apkName") { ctx -> // TODO: move to pkgName
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
        app.get("/api/v1/source/list") { ctx ->
            ctx.json(getSourceList())
        }

        // fetch source with id `sourceId`
        app.get("/api/v1/source/:sourceId") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            ctx.json(getSource(sourceId))
        }

        // popular mangas from source with id `sourceId`
        app.get("/api/v1/source/:sourceId/popular/:pageNum") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            val pageNum = ctx.pathParam("pageNum").toInt()
            ctx.json(
                future {
                    getMangaList(sourceId, pageNum, popular = true)
                }
            )
        }

        // latest mangas from source with id `sourceId`
        app.get("/api/v1/source/:sourceId/latest/:pageNum") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            val pageNum = ctx.pathParam("pageNum").toInt()
            ctx.json(
                future {
                    getMangaList(sourceId, pageNum, popular = false)
                }
            )
        }

        // get manga info
        app.get("/api/v1/manga/:mangaId/") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()
            val onlineFetch = ctx.queryParam("onlineFetch", "false").toBoolean()

            ctx.json(
                future {
                    getManga(mangaId, onlineFetch)
                }
            )
        }

        // manga thumbnail
        app.get("api/v1/manga/:mangaId/thumbnail") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()

            ctx.result(
                future { getMangaThumbnail(mangaId) }
                    .thenApply {
                        ctx.header("content-type", it.second)
                        it.first
                    }
            )
        }

        // adds the manga to library
        app.get("api/v1/manga/:mangaId/library") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()

            ctx.result(
                future { addMangaToLibrary(mangaId) }
            )
        }

        // removes the manga from the library
        app.delete("api/v1/manga/:mangaId/library") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()

            ctx.result(
                future { removeMangaFromLibrary(mangaId) }
            )
        }

        // list manga's categories
        app.get("api/v1/manga/:mangaId/category/") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()
            ctx.json(getMangaCategories(mangaId))
        }

        // adds the manga to category
        app.get("api/v1/manga/:mangaId/category/:categoryId") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()
            val categoryId = ctx.pathParam("categoryId").toInt()
            addMangaToCategory(mangaId, categoryId)
            ctx.status(200)
        }

        // removes the manga from the category
        app.delete("api/v1/manga/:mangaId/category/:categoryId") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()
            val categoryId = ctx.pathParam("categoryId").toInt()
            removeMangaFromCategory(mangaId, categoryId)
            ctx.status(200)
        }

        // get chapter list when showing a manga
        app.get("/api/v1/manga/:mangaId/chapters") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()

            val onlineFetch = ctx.queryParam("onlineFetch", "false").toBoolean()

            ctx.json(future { getChapterList(mangaId, onlineFetch) })
        }

        // used to display a chapter, get a chapter in order to show it's pages
        app.get("/api/v1/manga/:mangaId/chapter/:chapterIndex") { ctx ->
            val chapterIndex = ctx.pathParam("chapterIndex").toInt()
            val mangaId = ctx.pathParam("mangaId").toInt()
            ctx.json(future { getChapter(chapterIndex, mangaId) })
        }

        // used to modify a chapter's parameters
        app.patch("/api/v1/manga/:mangaId/chapter/:chapterIndex") { ctx ->
            val chapterIndex = ctx.pathParam("chapterIndex").toInt()
            val mangaId = ctx.pathParam("mangaId").toInt()

            val read = ctx.formParam("read")?.toBoolean()
            val bookmarked = ctx.formParam("bookmarked")?.toBoolean()
            val markPrevRead = ctx.formParam("markPrevRead")?.toBoolean()
            val lastPageRead = ctx.formParam("lastPageRead")?.toInt()

            modifyChapter(mangaId, chapterIndex, read, bookmarked, markPrevRead, lastPageRead)

            ctx.status(200)
        }

        // get page at index "index"
        app.get("/api/v1/manga/:mangaId/chapter/:chapterIndex/page/:index") { ctx ->
            val mangaId = ctx.pathParam("mangaId").toInt()
            val chapterIndex = ctx.pathParam("chapterIndex").toInt()
            val index = ctx.pathParam("index").toInt()

            ctx.result(
                future { getPageImage(mangaId, chapterIndex, index) }
                    .thenApply {
                        ctx.header("content-type", it.second)
                        it.first
                    }
            )
        }

        // global search, Not implemented yet
        app.get("/api/v1/search/:searchTerm") { ctx ->
            val searchTerm = ctx.pathParam("searchTerm")
            ctx.json(sourceGlobalSearch(searchTerm))
        }

        // single source search
        app.get("/api/v1/source/:sourceId/search/:searchTerm/:pageNum") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            val searchTerm = ctx.pathParam("searchTerm")
            val pageNum = ctx.pathParam("pageNum").toInt()
            ctx.json(future { sourceSearch(sourceId, searchTerm, pageNum) })
        }

        // source filter list
        app.get("/api/v1/source/:sourceId/filters/") { ctx ->
            val sourceId = ctx.pathParam("sourceId").toLong()
            ctx.json(sourceFilters(sourceId))
        }

        // lists mangas that have no category assigned
        app.get("/api/v1/library/") { ctx ->
            ctx.json(getLibraryMangas())
        }

        // category list
        app.get("/api/v1/category/") { ctx ->
            ctx.json(getCategoryList())
        }

        // category create
        app.post("/api/v1/category/") { ctx ->
            val name = ctx.formParam("name")!!
            createCategory(name)
            ctx.status(200)
        }

        // returns some static info of the current app build
        app.get("/api/v1/about/") { ctx ->
            ctx.json(getAbout())
        }

        // category modification
        app.patch("/api/v1/category/:categoryId") { ctx ->
            val categoryId = ctx.pathParam("categoryId").toInt()
            val name = ctx.formParam("name")
            val isLanding = if (ctx.formParam("isLanding") != null) ctx.formParam("isLanding")?.toBoolean() else null
            updateCategory(categoryId, name, isLanding)
            ctx.status(200)
        }

        // category re-ordering
        app.patch("/api/v1/category/:categoryId/reorder") { ctx ->
            val categoryId = ctx.pathParam("categoryId").toInt()
            val from = ctx.formParam("from")!!.toInt()
            val to = ctx.formParam("to")!!.toInt()
            reorderCategory(categoryId, from, to)
            ctx.status(200)
        }

        // category delete
        app.delete("/api/v1/category/:categoryId") { ctx ->
            val categoryId = ctx.pathParam("categoryId").toInt()
            removeCategory(categoryId)
            ctx.status(200)
        }

        // returns the manga list associated with a category
        app.get("/api/v1/category/:categoryId") { ctx ->
            val categoryId = ctx.pathParam("categoryId").toInt()
            ctx.json(getCategoryMangaList(categoryId))
        }

        // expects a Tachiyomi legacy backup json in the body
        app.post("/api/v1/backup/legacy/import") { ctx ->
            ctx.result(
                future {
                    restoreLegacyBackup(ctx.bodyAsInputStream())
                }
            )
        }

        // expects a Tachiyomi legacy backup json as a file upload, the file must be named "backup.json"
        app.post("/api/v1/backup/legacy/import/file") { ctx ->
            ctx.result(
                future {
                    restoreLegacyBackup(ctx.uploadedFile("backup.json")!!.content)
                }
            )
        }

        // returns a Tachiyomi legacy backup json created from the current database as a json body
        app.get("/api/v1/backup/legacy/export") { ctx ->
            ctx.contentType("application/json")
            ctx.result(
                future {
                    createLegacyBackup(
                        BackupFlags(
                            includeManga = true,
                            includeCategories = true,
                            includeChapters = true,
                            includeTracking = true,
                            includeHistory = true,
                        )
                    )
                }
            )
        }

        // returns a Tachiyomi legacy backup json created from the current database as a file
        app.get("/api/v1/backup/legacy/export/file") { ctx ->
            ctx.contentType("application/json")
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm")
            val currentDate = sdf.format(Date())

            ctx.header("Content-Disposition", "attachment; filename=\"tachidesk_$currentDate.json\"")
            ctx.result(
                future {
                    createLegacyBackup(
                        BackupFlags(
                            includeManga = true,
                            includeCategories = true,
                            includeChapters = true,
                            includeTracking = true,
                            includeHistory = true,
                        )
                    )
                }
            )
        }
    }
}
