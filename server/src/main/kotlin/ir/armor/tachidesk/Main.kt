package ir.armor.tachidesk

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import ir.armor.tachidesk.util.addMangaToCategory
import ir.armor.tachidesk.util.addMangaToLibrary
import ir.armor.tachidesk.util.createCategory
import ir.armor.tachidesk.util.getCategoryList
import ir.armor.tachidesk.util.getCategoryMangaList
import ir.armor.tachidesk.util.getChapter
import ir.armor.tachidesk.util.getChapterList
import ir.armor.tachidesk.util.getExtensionIcon
import ir.armor.tachidesk.util.getExtensionList
import ir.armor.tachidesk.util.getLibraryMangas
import ir.armor.tachidesk.util.getManga
import ir.armor.tachidesk.util.getMangaCategories
import ir.armor.tachidesk.util.getMangaList
import ir.armor.tachidesk.util.getPageImage
import ir.armor.tachidesk.util.getSource
import ir.armor.tachidesk.util.getSourceList
import ir.armor.tachidesk.util.getThumbnail
import ir.armor.tachidesk.util.installAPK
import ir.armor.tachidesk.util.openInBrowser
import ir.armor.tachidesk.util.removeCategory
import ir.armor.tachidesk.util.removeExtension
import ir.armor.tachidesk.util.removeMangaFromCategory
import ir.armor.tachidesk.util.removeMangaFromLibrary
import ir.armor.tachidesk.util.reorderCategory
import ir.armor.tachidesk.util.sourceFilters
import ir.armor.tachidesk.util.sourceGlobalSearch
import ir.armor.tachidesk.util.sourceSearch
import ir.armor.tachidesk.util.updateCategory

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            serverSetup()

            var hasWebUiBundled: Boolean = false

            val app = Javalin.create { config ->
                try {
                    this::class.java.classLoader.getResource("/react/index.html")
                    hasWebUiBundled = true
                    config.addStaticFiles("/react")
                    config.addSinglePageRoot("/", "/react/index.html")
                } catch (e: RuntimeException) {
                    println("Warning: react build files are missing.")
                    hasWebUiBundled = false
                }
                config.enableCorsForAllOrigins()
            }.start(serverConfig.ip, serverConfig.port)
            if (hasWebUiBundled) {
                openInBrowser()
            }

            app.exception(NullPointerException::class.java) { _, ctx ->
                ctx.status(404)
            }

            app.get("/api/v1/extension/list") { ctx ->
                ctx.json(getExtensionList())
            }

            app.get("/api/v1/extension/install/:apkName") { ctx ->
                val apkName = ctx.pathParam("apkName")
                println("installing $apkName")

                ctx.status(
                    installAPK(apkName)
                )
            }

            app.get("/api/v1/extension/uninstall/:apkName") { ctx ->
                val apkName = ctx.pathParam("apkName")
                println("uninstalling $apkName")
                removeExtension(apkName)
                ctx.status(200)
            }

            // icon for extension named `apkName`
            app.get("/api/v1/extension/icon/:apkName") { ctx ->
                val apkName = ctx.pathParam("apkName")
                val result = getExtensionIcon(apkName)

                ctx.result(result.first)
                ctx.header("content-type", result.second)
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
                ctx.json(getMangaList(sourceId, pageNum, popular = true))
            }

            // latest mangas from source with id `sourceId`
            app.get("/api/v1/source/:sourceId/latest/:pageNum") { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                val pageNum = ctx.pathParam("pageNum").toInt()
                ctx.json(getMangaList(sourceId, pageNum, popular = false))
            }

            // get manga info
            app.get("/api/v1/manga/:mangaId/") { ctx ->
                val mangaId = ctx.pathParam("mangaId").toInt()
                ctx.json(getManga(mangaId))
            }

            // manga thumbnail
            app.get("api/v1/manga/:mangaId/thumbnail") { ctx ->
                val mangaId = ctx.pathParam("mangaId").toInt()
                val result = getThumbnail(mangaId)

                ctx.result(result.first)
                ctx.header("content-type", result.second)
            }

            // adds the manga to library
            app.get("api/v1/manga/:mangaId/library") { ctx ->
                val mangaId = ctx.pathParam("mangaId").toInt()
                addMangaToLibrary(mangaId)
                ctx.status(200)
            }

            // removes the manga from the library
            app.delete("api/v1/manga/:mangaId/library") { ctx ->
                val mangaId = ctx.pathParam("mangaId").toInt()
                removeMangaFromLibrary(mangaId)
                ctx.status(200)
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

            app.get("/api/v1/manga/:mangaId/chapters") { ctx ->
                val mangaId = ctx.pathParam("mangaId").toInt()
                ctx.json(getChapterList(mangaId))
            }

            app.get("/api/v1/manga/:mangaId/chapter/:chapterIndex") { ctx ->
                val chapterIndex = ctx.pathParam("chapterIndex").toInt()
                val mangaId = ctx.pathParam("mangaId").toInt()
                ctx.json(getChapter(chapterIndex, mangaId))
            }

            app.get("/api/v1/manga/:mangaId/chapter/:chapterId/page/:index") { ctx ->
                val chapterId = ctx.pathParam("chapterId").toInt()
                val mangaId = ctx.pathParam("mangaId").toInt()
                val index = ctx.pathParam("index").toInt()
                val result = getPageImage(mangaId, chapterId, index)

                ctx.result(result.first)
                ctx.header("content-type", result.second)
            }

            // global search
            app.get("/api/v1/search/:searchTerm") { ctx ->
                val searchTerm = ctx.pathParam("searchTerm")
                ctx.json(sourceGlobalSearch(searchTerm))
            }

            // single source search
            app.get("/api/v1/source/:sourceId/search/:searchTerm/:pageNum") { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                val searchTerm = ctx.pathParam("searchTerm")
                val pageNum = ctx.pathParam("pageNum").toInt()
                ctx.json(sourceSearch(sourceId, searchTerm, pageNum))
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
        }
    }
}
