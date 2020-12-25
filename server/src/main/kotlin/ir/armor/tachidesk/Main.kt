package ir.armor.tachidesk

import com.googlecode.dex2jar.tools.Dex2jarCmd
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.HttpSource
import io.javalin.Javalin
import ir.armor.tachidesk.database.makeDataBaseTables
import ir.armor.tachidesk.database.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okio.buffer
import okio.sink
import org.jetbrains.exposed.sql.*
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import org.jetbrains.exposed.sql.transactions.transaction

class Main {
    companion object {
        var lastExtensionCheck: Long = 0


        @JvmStatic
        fun downloadAPKFile(url: String, apkPath: String) {
            val request = Request.Builder().url(url).build()
            val response = NetworkHelper().client.newCall(request).execute();

            val downloadedFile = File(apkPath)
            val sink = downloadedFile.sink().buffer()
            sink.writeAll(response.body!!.source())
            sink.close()
        }

        @JvmStatic
        fun testExtensionExecution() {
            File(Config.extensionsRoot).mkdirs()
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
            val apkFilePath = "${Config.extensionsRoot}/$apkFileName"
            val zipDirPath = apkFilePath.substringBefore(".apk")
            val jarFilePath = "$zipDirPath.jar"
            val dexFilePath = "$zipDirPath.dex"

            // download apk file
            downloadAPKFile(apkToDownload, apkFilePath)


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
        }

        fun extensionDatabaseIsEmtpy(): Boolean {
            return transaction {
                return@transaction ExtensionsTable.selectAll().count() == 0L
            }
        }

        fun getExtensionList(offline: Boolean = false): List<ExtensionDataClass> {
            // update if 60 seconds has passed or requested offline and database is empty
            if (lastExtensionCheck + 60 * 1000 < System.currentTimeMillis() || (offline && extensionDatabaseIsEmtpy())) {
                println("Getting extensions list from the internet")
                lastExtensionCheck = System.currentTimeMillis()
                var foundExtensions: List<Extension.Available>
                runBlocking {
                    val api = ExtensionGithubApi()
                    foundExtensions = api.findExtensions()
                    transaction {
                        foundExtensions.forEach { foundExtension ->
                            val extensionRecord = ExtensionsTable.select { ExtensionsTable.name eq foundExtension.name }.firstOrNull()
                            if (extensionRecord != null) {
                                // update the record
                                ExtensionsTable.update({ ExtensionsTable.name eq foundExtension.name }) {
                                    it[name] = foundExtension.name
                                    it[pkgName] = foundExtension.pkgName
                                    it[versionName] = foundExtension.versionName
                                    it[versionCode] = foundExtension.versionCode
                                    it[lang] = foundExtension.lang
                                    it[isNsfw] = foundExtension.isNsfw
                                    it[apkName] = foundExtension.apkName
                                    it[iconUrl] = foundExtension.iconUrl
                                }
                            } else {
                                // insert new record
                                ExtensionsTable.insert {
                                    it[name] = foundExtension.name
                                    it[pkgName] = foundExtension.pkgName
                                    it[versionName] = foundExtension.versionName
                                    it[versionCode] = foundExtension.versionCode
                                    it[lang] = foundExtension.lang
                                    it[isNsfw] = foundExtension.isNsfw
                                    it[apkName] = foundExtension.apkName
                                    it[iconUrl] = foundExtension.iconUrl
                                }
                            }
                        }
                    }
                }
            }

            return transaction {
                return@transaction ExtensionsTable.selectAll().map {
                    ExtensionDataClass(
                            it[ExtensionsTable.name],
                            it[ExtensionsTable.pkgName],
                            it[ExtensionsTable.versionName],
                            it[ExtensionsTable.versionCode],
                            it[ExtensionsTable.lang],
                            it[ExtensionsTable.isNsfw],
                            it[ExtensionsTable.apkName],
                            it[ExtensionsTable.iconUrl],
                            it[ExtensionsTable.installed],
                            it[ExtensionsTable.classFQName]
                    )
                }

            }
        }

        fun downloadApk(apkName: String): Int {
            val extensionRecord = getExtensionList(true).first { it.apkName == apkName }
            val fileNameWithoutType = apkName.substringBefore(".apk")
            val dirPathWithoutType = "${Config.extensionsRoot}/$fileNameWithoutType"

            // check if we don't have the dex file already downloaded
            val dexPath = "${Config.extensionsRoot}/$fileNameWithoutType.jar"
            if (!File(dexPath).exists()) {
                runBlocking {
                    val api = ExtensionGithubApi()
                    val apkToDownload = api.getApkUrl(extensionRecord)

                    val apkFilePath = "$dirPathWithoutType.apk"
                    val jarFilePath = "$dirPathWithoutType.jar"
                    val dexFilePath = "$dirPathWithoutType.dex"

                    // download apk file
                    downloadAPKFile(apkToDownload, apkFilePath)


                    val className: String = APKExtractor.extract_dex_and_read_className(apkFilePath, dexFilePath)
                    println(className)
                    // dex -> jar
                    Dex2jarCmd.main(dexFilePath, "-o", jarFilePath, "--force")

                    // clean up
                    File(apkFilePath).delete()
                    File(dexFilePath).delete()

                    // update sources of the extension
                    val child = URLClassLoader(arrayOf<URL>(URL("file:$jarFilePath")), this::class.java.classLoader)
                    val classToLoad = Class.forName(className, true, child)
                    val instance = classToLoad.newInstance()

                    val extensionId = transaction {
                        return@transaction ExtensionsTable.select { ExtensionsTable.name eq extensionRecord.name }.first()[ExtensionsTable.id]
                    }

                    if (instance is HttpSource) {// single source
                        val httpSource = instance as HttpSource
                        transaction {
//                            SourceEntity.new  {
//                                sourceId = httpSource.id
//                                name = httpSource.name
//                                this.extension =  ExtensionEntity.find { ExtensionsTable.name eq extension.name }.first().id
//                            }
                            if (SourcesTable.select { SourcesTable.sourceId eq httpSource.id }.count() == 0L) {
                                SourcesTable.insert {
                                    it[this.sourceId] = httpSource.id
                                    it[name] = httpSource.name
                                    it[this.lang] = httpSource.lang
                                    it[extension] = extensionId
                                }
                            }
//                            println(httpSource.id)
//                            println(httpSource.name)
//                            println()
                        }

                    } else { // multi source
                        val sourceFactory = instance as SourceFactory
                        transaction {
                            sourceFactory.createSources().forEachIndexed { index, source ->
                                val httpSource = source as HttpSource
                                if (SourcesTable.select { SourcesTable.sourceId eq httpSource.id }.count() == 0L) {
                                    SourcesTable.insert {
                                        it[this.sourceId] = httpSource.id
                                        it[name] = httpSource.name
                                        it[this.lang] = httpSource.lang
                                        it[extension] = extensionId
                                        it[partOfFactorySource] = true
                                        it[positionInFactorySource] = index
                                    }
                                }
//                                println(httpSource.id)
//                                println(httpSource.name)
//                                println()
                            }
                        }
                    }

                    // update extension info
                    transaction {
                        ExtensionsTable.update({ ExtensionsTable.name eq extensionRecord.name }) {
                            it[installed] = true
                            it[classFQName] = className
                        }
                    }

                }
                return 201 // we downloaded successfully
            } else {
                return 302
            }
        }

        fun getHttpSource(sourceId: Long): HttpSource {
            return transaction {
                val sourceRecord = SourceEntity.find { SourcesTable.sourceId eq sourceId }.first()
                val extensionId = sourceRecord.extension.id.value
                val extensionRecord = ExtensionEntity.get(extensionId)
                val apkName = extensionRecord.apkName
                val className = extensionRecord.classFQName
                val jarName = apkName.substringBefore(".apk") + ".jar"
                val jarPath = "${Config.extensionsRoot}/$jarName"

                println(jarPath)

                val child = URLClassLoader(arrayOf<URL>(URL("file:$jarPath")), this::class.java.classLoader)
                val classToLoad = Class.forName(className, true, child)
                val instance = classToLoad.newInstance()

                if (sourceRecord.partOfFactorySource) {
                    return@transaction (instance as SourceFactory).createSources()[sourceRecord.positionInFactorySource!!] as HttpSource
                } else {
                    return@transaction instance as HttpSource
                }
            }
        }

        fun getSourceList(): List<SourceDataClass> {
            return transaction {
                return@transaction SourcesTable.selectAll().map {
                    SourceDataClass(
                            it[SourcesTable.sourceId],
                            it[SourcesTable.name],
                            it[SourcesTable.lang],
                            ExtensionsTable.select { ExtensionsTable.id eq it[SourcesTable.extension] }.first()[ExtensionsTable.iconUrl],
                            getHttpSource(it[SourcesTable.sourceId]).supportsLatest
                            )
                }
            }
        }


        @JvmStatic
        fun main(args: Array<String>) {
            // make sure everything we need exists
            File(Config.dataRoot).mkdirs()
            File(Config.extensionsRoot).mkdirs()
            makeDataBaseTables()


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
                        downloadApk(apkName)
                )
            }
            app.get("/api/v1/sources/") { ctx ->
                ctx.json(getSourceList())
            }


        }
    }
}

