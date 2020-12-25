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

fun getHttpSource(sourceId: Long): HttpSource {
    return transaction {
        val sourceRecord = SourceEntity.get(sourceId)
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
                    it[SourcesTable.id].value,
                    it[SourcesTable.name],
                    it[SourcesTable.lang],
                    ExtensionsTable.select { ExtensionsTable.id eq it[SourcesTable.extension] }.first()[ExtensionsTable.iconUrl],
                    getHttpSource(it[SourcesTable.id].value).supportsLatest
            )
        }
    }
}