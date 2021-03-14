package ir.armor.tachidesk.util

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import ir.armor.tachidesk.applicationDirs
import ir.armor.tachidesk.database.dataclass.SourceDataClass
import ir.armor.tachidesk.database.entity.ExtensionEntity
import ir.armor.tachidesk.database.entity.SourceEntity
import ir.armor.tachidesk.database.table.ExtensionTable
import ir.armor.tachidesk.database.table.SourceTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.NullPointerException
import java.net.URL
import java.net.URLClassLoader

private val sourceCache = mutableListOf<Pair<Long, HttpSource>>()
private val extensionCache = mutableListOf<Pair<String, Any>>()

fun getHttpSource(sourceId: Long): HttpSource {
    val sourceRecord = transaction {
        SourceEntity.findById(sourceId)
    } ?: throw NullPointerException("Source with id $sourceId is not installed")

    val cachedResult: Pair<Long, HttpSource>? = sourceCache.firstOrNull { it.first == sourceId }
    if (cachedResult != null) {
        println("used cached HttpSource: ${cachedResult.second.name}")
        return cachedResult.second
    }

    val result: HttpSource = transaction {
        val extensionId = sourceRecord.extension.id.value
        val extensionRecord = ExtensionEntity.findById(extensionId)!!
        val apkName = extensionRecord.apkName
        val className = extensionRecord.classFQName
        val jarName = apkName.substringBefore(".apk") + ".jar"
        val jarPath = "${applicationDirs.extensionsRoot}/$jarName"

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
        return@transaction SourceTable.selectAll().map {
            SourceDataClass(
                it[SourceTable.id].value.toString(),
                it[SourceTable.name],
                it[SourceTable.lang],
                getExtensionIconUrl(ExtensionTable.select { ExtensionTable.id eq it[SourceTable.extension] }.first()[ExtensionTable.apkName]),
                getHttpSource(it[SourceTable.id].value).supportsLatest
            )
        }
    }
}

fun getSource(sourceId: Long): SourceDataClass {
    return transaction {
        val source = SourceTable.select { SourceTable.id eq sourceId }.firstOrNull()

        return@transaction SourceDataClass(
            sourceId.toString(),
            source?.get(SourceTable.name),
            source?.get(SourceTable.lang),
            source?.let { ExtensionTable.select { ExtensionTable.id eq source[SourceTable.extension] }.first()[ExtensionTable.iconUrl] },
            source?.let { getHttpSource(sourceId).supportsLatest }
        )
    }
}
