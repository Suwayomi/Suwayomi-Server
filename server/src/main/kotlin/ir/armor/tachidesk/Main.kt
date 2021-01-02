package ir.armor.tachidesk

import eu.kanade.tachiyomi.App
import io.javalin.Javalin
import ir.armor.tachidesk.util.*
import org.kodein.di.DI
import org.kodein.di.conf.global
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.ServerConfig
import java.util.*

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
            // make sure everything we need exists
            applicationSetup()

            registerConfigModules()

            //Load config API
            DI.global.addImport(ConfigKodeinModule().create())
            //Load Android compatibility dependencies
            AndroidCompatInitializer().init()
            // start app
            androidCompat.startApp(App())



            val app = Javalin.create().start(4567)

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

            app.get("/api/v1/source/:source_id/popular") { ctx ->
                val sourceId = ctx.pathParam("source_id")
                ctx.json(getPopularManga(sourceId))
            }
        }


    }
}

