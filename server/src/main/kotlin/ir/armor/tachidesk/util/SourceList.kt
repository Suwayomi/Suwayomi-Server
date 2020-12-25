package ir.armor.tachidesk.util

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.Config
import ir.armor.tachidesk.Main
import ir.armor.tachidesk.database.dataclass.SourceDataClass
import ir.armor.tachidesk.database.entity.ExtensionEntity
import ir.armor.tachidesk.database.entity.SourceEntity
import ir.armor.tachidesk.database.table.ExtensionsTable
import ir.armor.tachidesk.database.table.SourcesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URL
import java.net.URLClassLoader
import java.util.*

private val sourceCache = mutableListOf<Pair<Long, HttpSource>>()
private val extensionCache = mutableListOf<Pair<String, Any>>()

fun getHttpSource(sourceId: Long): HttpSource {
    val cachedResult: Pair<Long, HttpSource>? = sourceCache.firstOrNull { it.first == sourceId }
    if (cachedResult != null) {
        println("used cached HttpSource: ${cachedResult.second.name}")
        return cachedResult.second
    }

    val result: HttpSource = transaction {
        val sourceRecord = SourceEntity.findById(sourceId)!!
        val extensionId = sourceRecord.extension.id.value
        val extensionRecord = ExtensionEntity.findById(extensionId)!!
        val apkName = extensionRecord.apkName
        val className = extensionRecord.classFQName
        val jarName = apkName.substringBefore(".apk") + ".jar"
        val jarPath = "${Config.extensionsRoot}/$jarName"

        println(jarName)

        val cachedExtensionPair = extensionCache.firstOrNull { it.first == jarPath }
        var usedCached = false
        val instance =
                if (cachedExtensionPair != null) {
                    usedCached = true
                    println("Used cached Extension")
                    cachedExtensionPair.second
                } else {
                    println("No Extension cache")
                    val child = URLClassLoader(arrayOf<URL>(URL("file:$jarPath")), this::class.java.classLoader)
                    val classToLoad = Class.forName(className, true, child)
                    classToLoad.newInstance()
                }
        if (sourceRecord.partOfFactorySource) {
            return@transaction if (usedCached) {
                (instance as List<HttpSource>)[sourceRecord.positionInFactorySource!!]
            } else {
                val list = (instance as SourceFactory).createSources()
                extensionCache.add(Pair(jarPath, list))
                list[sourceRecord.positionInFactorySource!!] as HttpSource
            }
        } else {
            if (!usedCached)
                extensionCache.add(Pair(jarPath, instance))
            return@transaction instance as HttpSource
        }
    }
    sourceCache.add(Pair(sourceId, result))
    return result
}

fun getSourceList(): List<SourceDataClass> {
    return transaction {
        return@transaction SourcesTable.selectAll().map {
            SourceDataClass(
                    it[SourcesTable.id].value.toString(),
                    it[SourcesTable.name],
                    Locale(it[SourcesTable.lang]).getDisplayLanguage(Locale(it[SourcesTable.lang])),
                    ExtensionsTable.select { ExtensionsTable.id eq it[SourcesTable.extension] }.first()[ExtensionsTable.iconUrl],
                    getHttpSource(it[SourcesTable.id].value).supportsLatest
            )
        }
    }
}