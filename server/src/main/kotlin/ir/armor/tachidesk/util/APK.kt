package ir.armor.tachidesk.util

import com.googlecode.dex2jar.tools.Dex2jarCmd
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.APKExtractor
import ir.armor.tachidesk.Config
import ir.armor.tachidesk.database.table.ExtensionsTable
import ir.armor.tachidesk.database.table.SourcesTable
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okio.buffer
import okio.sink
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.net.URL
import java.net.URLClassLoader

fun installAPK(apkName: String): Int {
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

private fun downloadAPKFile(url: String, apkPath: String) {
    val request = Request.Builder().url(url).build()
    val response = NetworkHelper().client.newCall(request).execute();

    val downloadedFile = File(apkPath)
    val sink = downloadedFile.sink().buffer()
    sink.writeAll(response.body!!.source())
    sink.close()
}
