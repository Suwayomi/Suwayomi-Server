package ir.armor.tachidesk

import com.googlecode.dex2jar.tools.Dex2jarCmd
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.HttpSource
import io.javalin.Javalin
import io.javalin.http.Context
import ir.armor.tachidesk.database.DBMangaer
import ir.armor.tachidesk.database.makeDataBaseTables
import ir.armor.tachidesk.database.model.ExtensionsTable
import ir.armor.tachidesk.database.model.SourcesTable
import kotlinx.coroutines.runBlocking
import net.harawata.appdirs.AppDirsFactory
import okhttp3.Request
import okio.buffer
import okio.sink
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction


class Main {
    companion object {
        @JvmStatic
        fun downloadAPK(url: String, apkPath: String){
            val request = Request.Builder().url(url).build()
            val response = NetworkHelper().client.newCall(request).execute();

            val downloadedFile = File(apkPath)
            val sink = downloadedFile.sink().buffer()
            sink.writeAll(response.body!!.source())
            sink.close()
        }

        @JvmStatic
        fun testExtensionExecution(){
            val contentRoot = Config.dataRoot + "/extensions"
            File(contentRoot).mkdirs()
            var sourcePkg = ""

            // get list of extensions
            var apkToDownload: String = ""
            runBlocking {
                val api = ExtensionGithubApi()
                val source = api.findExtensions().first {
                    api.getApkUrl(it).endsWith("killsixbilliondemons-v1.2.3.apk")
                }
                apkToDownload = api.getApkUrl(source)
                sourcePkg = source.pkgName
            }

            val apkFileName = apkToDownload.split("/").last()
            val apkFilePath = "$contentRoot/$apkFileName"
            val zipDirPath = apkFilePath.substringBefore(".apk")
            val jarFilePath = "$zipDirPath.jar"
            val dexFilePath = "$zipDirPath.dex"

            // download apk file
            downloadAPK(apkToDownload, apkFilePath)


            val className = APKExtractor.extract_dex_and_read_className(apkFilePath, dexFilePath)
            // dex -> jar
            Dex2jarCmd.main(dexFilePath, "-o", jarFilePath, "--force")

            val child = URLClassLoader(arrayOf<URL>(URL("file:$jarFilePath")), this::class.java.classLoader)
            val classToLoad = Class.forName(className, true, child)
            val instance = classToLoad.newInstance() as HttpSource
            val result = instance.fetchPopularManga(1)
            val mangasPage = result.toBlocking().first() as MangasPage
            mangasPage.mangas.forEach {
                println(it.title)
            }
//            exitProcess(0)
        }


        @JvmStatic
        fun main(args: Array<String>) {
            // make sure data everything we need exists
            File(Config.dataRoot).mkdirs()
            makeDataBaseTables()


//            val app = Javalin.create().start(4567)
//
//            app.before() { ctx ->
//                ctx.header("Access-Control-Allow-Origin", "*")
//            }
//
//            app.get("/api/v1/extensions") { ctx ->
//                runBlocking {
//                    val api = ExtensionGithubApi()
//                    val sources = api.findExtensions()
//                    ctx.json(sources)
//                }
//            }
//
//            app.get("/api/v1/extensions/install/:extensionURL") { ctx ->
//                ctx.pathParam("extensionURL")
//            })


//            ExtensionTable.new {
//                name = "khar"
//                pkgName = "eu.khar"
//            }
        }
    }
}

