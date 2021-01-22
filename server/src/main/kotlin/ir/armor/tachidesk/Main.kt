package ir.armor.tachidesk

import eu.kanade.tachiyomi.App
import io.javalin.Javalin
import ir.armor.tachidesk.util.applicationSetup
import ir.armor.tachidesk.util.getChapterList
import ir.armor.tachidesk.util.getExtensionList
import ir.armor.tachidesk.util.getManga
import ir.armor.tachidesk.util.getMangaList
import ir.armor.tachidesk.util.getPages
import ir.armor.tachidesk.util.getSource
import ir.armor.tachidesk.util.getSourceList
import ir.armor.tachidesk.util.installAPK
import ir.armor.tachidesk.util.sourceFilters
import ir.armor.tachidesk.util.sourceGlobalSearch
import ir.armor.tachidesk.util.sourceSearch
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
            System.getProperties()["proxySet"] = "true"
            System.getProperties()["socksProxyHost"] = "127.0.0.1"
            System.getProperties()["socksProxyPort"] = "2020"

            // make sure everything we need exists
            applicationSetup()

            registerConfigModules()

            // Load config API
            DI.global.addImport(ConfigKodeinModule().create())
            // Load Android compatibility dependencies
            AndroidCompatInitializer().init()
            // start app
            androidCompat.startApp(App())

            val app = Javalin.create { config ->
                try {
                    this::class.java.classLoader.getResource("/react/index.html")
                    config.addStaticFiles("/react")
                    config.addSinglePageRoot("/", "/react/index.html")
                } catch (e: RuntimeException) {
                    println("Warning: react build files are missing.")
                }
            }.start(4567)

            app.before() { ctx ->
                // allow the client which is running on another port
                ctx.header("Access-Control-Allow-Origin", "*")
            }

            app.get("/api/v1/extension/list") { ctx ->
                ctx.json(getExtensionList())
            }

            app.get("/api/v1/extension/install/:apkName") { ctx ->
                val apkName = ctx.pathParam("apkName")
                println(apkName)
                ctx.status(
                    installAPK(apkName)
                )
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

            app.get("/api/v1/manga/:mangaId/chapters") { ctx ->
                val mangaId = ctx.pathParam("mangaId").toInt()
                ctx.json(getChapterList(mangaId))
            }

            app.get("/api/v1/manga/:mangaId/chapter/:chapterId") { ctx ->
                val chapterId = ctx.pathParam("chapterId").toInt()
                val mangaId = ctx.pathParam("mangaId").toInt()
                ctx.json(getPages(chapterId, mangaId))
            }

            // global search
            app.get("/api/v1/search/:searchTerm") { ctx ->
                val searchTerm = ctx.pathParam("searchTerm")
                ctx.json(sourceGlobalSearch(searchTerm))
            }

            // single source search
            app.get("/api/v1/source/:sourceId/search/:searchTerm") { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                val searchTerm = ctx.pathParam("searchTerm")
                ctx.json(sourceSearch(sourceId, searchTerm))
            }

            // source filter list
            app.get("/api/v1/source/:sourceId/filters/") { ctx ->
                val sourceId = ctx.pathParam("sourceId").toLong()
                ctx.json(sourceFilters(sourceId))
            }
        }
    }
}
