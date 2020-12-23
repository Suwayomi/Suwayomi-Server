package ir.armor.tachidesk

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Request
import rx.Observable
import java.io.File
import java.net.URL
import java.net.URLClassLoader

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // get list of extensions
            var apkToDownload: String = ""
//            runBlocking {
//                val api = ExtensionGithubApi()
//                apkToDownload = api.getApkUrl(api.findExtensions().first {
//                    api.getApkUrl(it).endsWith("killsixbilliondemons-v1.2.3.apk")
//                })
//            }
            apkToDownload = "https://raw.githubusercontent.com/inorichi/tachiyomi-extensions/repo/apk/tachiyomi-en.killsixbilliondemons-v1.2.3.apk"
            println(apkToDownload)

            val apkFileName = apkToDownload.split("/").last()
            val apkFileDir = apkFileName.substringBefore(".apk")
            val apkFileDirAbsolutePath = File("$apkFileDir.jar").absolutePath

            val request = Request.Builder().url(apkToDownload)
//                    .addHeader("Content-Type", "application/json")
                    .build();
//            val response = NetworkHelper().client.newCall(request).execute();
//            println(response.code)
//
//            val downloadedFile = File(apkFileName)
//            val sink: BufferedSink = downloadedFile.sink().buffer()
//            sink.writeAll(response.body!!.source())
//            sink.close()

//            Runtime.getRuntime().exec("unzip $apkFileName -d $apkFileDir")
//            Runtime.getRuntime().exec("dex2jar $apkFileDir/classes.dex -o $apkFileDir.jar")

            val child = URLClassLoader(arrayOf<URL>(URL("file:$apkFileDirAbsolutePath")), this.javaClass.classLoader)
            val classToLoad = Class.forName("eu.kanade.tachiyomi.extension.en.killsixbilliondemons.KillSixBillionDemons", true, child)
//            val method = classToLoad.getDeclaredMethod("fetchPopularManga")
            val instance = classToLoad.newInstance() as CatalogueSource
//            val result = method.invoke(instance, 1) as Observable<MangasPage>
            val result = instance.fetchPopularManga(1)
            val mangasPage = result.toBlocking().first() as MangasPage
            mangasPage.mangas.forEach{
                println(it.title)
            }

        }
    }
}

