package ir.armor.tachidesk

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.App
import io.javalin.Javalin
import ir.armor.tachidesk.util.addMangaToCategory
import ir.armor.tachidesk.util.addMangaToLibrary
import ir.armor.tachidesk.util.applicationSetup
import ir.armor.tachidesk.util.createCategory
import ir.armor.tachidesk.util.getCategoryList
import ir.armor.tachidesk.util.getCategoryMangaList
import ir.armor.tachidesk.util.getChapter
import ir.armor.tachidesk.util.getChapterList
import ir.armor.tachidesk.util.getExtensionIcon
import ir.armor.tachidesk.util.getExtensionList
import ir.armor.tachidesk.util.getLibraryMangas
import ir.armor.tachidesk.util.getManga
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
import ir.armor.tachidesk.util.sourceFilters
import ir.armor.tachidesk.util.sourceGlobalSearch
import ir.armor.tachidesk.util.sourceSearch
import ir.armor.tachidesk.util.systemTray
import ir.armor.tachidesk.util.updateCategory
import org.kodein.di.DI
import org.kodein.di.conf.global
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager

class Main {
    companion object {
        val androidCompat by lazy { AndroidCompat() }

        fun registerConfigModules() {
            GlobalConfigManager.registerModules(
//                    ServerConfig.register(GlobalConfigManager.config),
//                    SyncConfigModule.register(GlobalConfigManager.config)
            )
        }

        @JvmStatic
        fun main(args: Array<String>) {
//            System.getProperties()["proxySet"] = "true"
//            System.getProperties()["socksProxyHost"] = "127.0.0.1"
//            System.getProperties()["socksProxyPort"] = "2020"

            // make sure everything we need exists
            applicationSetup()
            val tray = systemTray()

            registerConfigModules()

            // Load config API
            DI.global.addImport(ConfigKodeinModule().create())
            // Load Android compatibility dependencies
            AndroidCompatInitializer().init()
            // start app
            androidCompat.startApp(App())

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
            }.start(4567)
            if (hasWebUiBundled) {
                openInBrowser()
            }

//            app.before() { ctx ->
//                // allow the client which is running on another port
//                ctx.header("Access-Control-Allow-Origin", "*")
//            }

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

            app.get("/api/v1/extension/icon/:apkName") { ctx ->
                val apkName = ctx.pathParam("apkName")
                val result = getExtensionIcon(apkName)

                ctx.result(result.first)
                ctx.header("content-type", result.second)
            }

            app.get("/api/v1/source/list") { ctx ->
                ctx.json(getSourceList())
            }

            app.get("/api/v1/source/:sourceId") { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                ctx.json(getSource(sourceId))
            }

            app.get("/api/v1/source/:sourceId/popular/:pageNum") { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                val pageNum = ctx.pathParam("pageNum").toInt()
                ctx.json(getMangaList(sourceId, pageNum, popular = true))
            }
            app.get("/api/v1/source/:sourceId/latest/:pageNum") { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                val pageNum = ctx.pathParam("pageNum").toInt()
                ctx.json(getMangaList(sourceId, pageNum, popular = false))
            }

            app.get("/api/v1/manga/:mangaId/") { ctx ->
                val mangaId = ctx.pathParam("mangaId").toInt()
                ctx.json(getManga(mangaId))
            }

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
                println("fuck")
                removeMangaFromLibrary(mangaId)
                ctx.status(200)
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

            app.get("/api/v1/manga/:mangaId/chapter/:chapterId") { ctx ->
                val chapterId = ctx.pathParam("chapterId").toInt()
                val mangaId = ctx.pathParam("mangaId").toInt()
                ctx.json(getChapter(chapterId, mangaId))
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

            // lists all manga in the library, suitable if no categories are defined
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
                val isLanding = ctx.formParam("isLanding", "false").toBoolean()
                createCategory(name, isLanding)
                ctx.status(200)
            }

            // category modification
            app.put("/api/v1/category/:categoryId") { ctx ->
                val categoryId = ctx.pathParam("categoryId").toInt()
                val name = ctx.formParam("name")!!
                val isLanding = ctx.formParam("isLanding").toBoolean()
                updateCategory(categoryId, name, isLanding)
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
