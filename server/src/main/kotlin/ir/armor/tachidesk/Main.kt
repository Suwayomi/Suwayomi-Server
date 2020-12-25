package ir.armor.tachidesk

import io.javalin.Javalin
import ir.armor.tachidesk.util.applicationSetup
import ir.armor.tachidesk.util.installAPK
import ir.armor.tachidesk.util.getExtensionList
import ir.armor.tachidesk.util.getSourceList

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // make sure everything we need exists
            applicationSetup()


            val app = Javalin.create().start(4567)

            app.before() { ctx ->
                ctx.header("Access-Control-Allow-Origin", "*") // allow the client which is running on another port
            }

            app.get("/api/v1/extensions") { ctx ->
                ctx.json(getExtensionList())
            }


            app.get("/api/v1/extensions/install/:apkName") { ctx ->
                val apkName = ctx.pathParam("apkName")
                println(apkName)
                ctx.status(
                        installAPK(apkName)
                )
            }
            app.get("/api/v1/sources/") { ctx ->
                ctx.json(getSourceList())
            }
        }
    }
}

