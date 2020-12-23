package ir.armor.tachidesk

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.DexFileReader
import com.googlecode.dex2jar.tools.Dex2jarCmd
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.system.exitProcess

class Main {
    companion object {
        const val contentRoot = "/tmp/tachidesk"

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
        fun main(args: Array<String>) {
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

            val child = URLClassLoader(arrayOf<URL>(URL("file:$jarFilePath")), this.javaClass.classLoader)
            val classToLoad = Class.forName(className, true, child)
            val instance = classToLoad.newInstance() as HttpSource
            val result = instance.fetchPopularManga(1)
            val mangasPage = result.toBlocking().first() as MangasPage
            mangasPage.mangas.forEach {
                println(it.title)
            }
            exitProcess(0)
        }
    }
}

