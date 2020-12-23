package ir.armor.tachidesk

import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okio.BufferedSink
import okio.buffer
import okio.sink
import rx.Observable
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.system.exitProcess

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val contentRoot = "/tmp/tachidesk"
            File(contentRoot).mkdirs()

            // get list of extensions
            var apkToDownload: String = ""
            runBlocking {
                val api = ExtensionGithubApi()
                apkToDownload = api.getApkUrl(api.findExtensions().first {
                    api.getApkUrl(it).endsWith("killsixbilliondemons-v1.2.3.apk")
                })
            }
            apkToDownload = "https://raw.githubusercontent.com/inorichi/tachiyomi-extensions/repo/apk/tachiyomi-en.killsixbilliondemons-v1.2.3.apk"
            println(apkToDownload)

            val apkFileName = apkToDownload.split("/").last()
            val apkFilePath = "$contentRoot/$apkFileName"
            val zipDirPath = apkFilePath.substringBefore(".apk")
            val jarFilePath = "$contentRoot/$zipDirPath.jar"

            val request = Request.Builder().url(apkToDownload).build()
            val response = NetworkHelper().client.newCall(request).execute();
            println(response.code)

            val downloadedFile = File(apkFilePath)
            val sink: BufferedSink = downloadedFile.sink().buffer()
            sink.writeAll(response.body!!.source())
            sink.close()

            Runtime.getRuntime().exec("unzip ${downloadedFile.absolutePath} -d $zipDirPath").waitFor()
            Runtime.getRuntime().exec("dex2jar $zipDirPath/classes.dex -o $jarFilePath").waitFor()

            val child = URLClassLoader(arrayOf<URL>(URL("file:$jarFilePath")), this.javaClass.classLoader)
            val classToLoad = Class.forName("eu.kanade.tachiyomi.extension.en.killsixbilliondemons.KillSixBillionDemons", true, child)
            val instance = classToLoad.newInstance() as CatalogueSource
            val result = instance.fetchPopularManga(1)
            val mangasPage = result.toBlocking().first() as MangasPage
            mangasPage.mangas.forEach{
                println(it.title)
            }
            exitProcess(0)

        }
    }
}

