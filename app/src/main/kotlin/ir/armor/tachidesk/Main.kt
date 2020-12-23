package ir.armor.tachidesk

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
//            val contentRoot = "/tmp/tachidesk"
//            File(contentRoot).mkdirs()
//            var sourcePkg = ""
//
//            // get list of extensions
//            var apkToDownload: String = ""
//            runBlocking {
//                val api = ExtensionGithubApi()
//                val source = api.findExtensions().first {
//                    api.getApkUrl(it).endsWith("killsixbilliondemons-v1.2.3.apk")
//                }
//                apkToDownload = api.getApkUrl(source)
//                sourcePkg = source.pkgName
//            }
//            apkToDownload = "https://raw.githubusercontent.com/inorichi/tachiyomi-extensions/repo/apk/tachiyomi-en.killsixbilliondemons-v1.2.3.apk"
//            println(apkToDownload)
//
//            val apkFileName = apkToDownload.split("/").last()
//            val apkFilePath = "$contentRoot/$apkFileName"
//            val zipDirPath = apkFilePath.substringBefore(".apk")
//            val jarFilePath = "$zipDirPath.jar"
//
//            val request = Request.Builder().url(apkToDownload).build()
//            val response = NetworkHelper().client.newCall(request).execute();
//            println(response.code)
//
//            val downloadedFile = File(apkFilePath)
//            val sink: BufferedSink = downloadedFile.sink().buffer()
//            sink.writeAll(response.body!!.source())
//            sink.close()
//
//            Runtime.getRuntime().exec("unzip ${downloadedFile.absolutePath} -d $zipDirPath").waitFor()
//            Runtime.getRuntime().exec("dex2jar $zipDirPath/classes.dex -o $jarFilePath").waitFor()
//
//            val child = URLClassLoader(arrayOf<URL>(URL("file:$jarFilePath")), this.javaClass.classLoader)
//            val classToLoad = Class.forName("eu.kanade.tachiyomi.extension.en.killsixbilliondemons.KillSixBillionDemons", true, child)
//            val instance = classToLoad.newInstance() as CatalogueSource
//            val result = instance.fetchPopularManga(1)
//            val mangasPage = result.toBlocking().first() as MangasPage
//            mangasPage.mangas.forEach{
//                println(it.title)
//            }
//            exitProcess(0)

            val apk = "/tmp/tachidesk/tachiyomi-en.killsixbilliondemons-v1.2.3.apk"
            val dex = "/tmp/tachidesk/tachiyomi-en.killsixbilliondemons-v1.2.3.dex"
            val pkg = APKExtractor.extract_dex_and_read_className(apk, dex)
        }
    }
}

